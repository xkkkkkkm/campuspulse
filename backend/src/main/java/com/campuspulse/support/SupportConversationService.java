package com.campuspulse.support;

import com.campuspulse.common.Api;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Short database leases order turns without holding a transaction across a model request. */
@Service
public class SupportConversationService {
    private final JdbcTemplate db;
    private final ObjectMapper json;
    public SupportConversationService(JdbcTemplate db, ObjectMapper json) { this.db = db; this.json = json; }
    public record Lease(String id, String token, List<Map<String,String>> history) {}

    @Transactional
    public Lease begin(long userId, String requestedId) {
        String id = requestedId;
        if (id == null || id.isBlank()) {
            db.queryForList("SELECT id FROM users WHERE id=? FOR UPDATE", userId);
            Long count = db.queryForObject("SELECT COUNT(*) FROM support_conversation WHERE user_id=?", Long.class, userId);
            if (count != null && count >= 50) throw new Api.ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    Api.localize("客服会话数量已达上限，请删除旧会话", "Delete an older support conversation before starting another."));
            id = UUID.randomUUID().toString();
            db.update("INSERT INTO support_conversation(id,user_id) VALUES (?,?)", id, userId);
        } else {
            validateId(id);
            requireOwner(userId, id, true);
        }
        String token = UUID.randomUUID().toString();
        if (db.update("UPDATE support_conversation SET lease_token=?,busy_until=DATE_ADD(NOW(),INTERVAL 45 SECOND) WHERE id=? AND user_id=? AND (busy_until IS NULL OR busy_until<NOW())", token, id, userId) != 1)
            throw new Api.ApiException(HttpStatus.CONFLICT, Api.localize("上一条客服问题仍在处理中，请稍后再试", "The previous support question is still being processed. Please try again shortly."));
        Long count = db.queryForObject("SELECT COUNT(*) FROM support_chat_turn WHERE conversation_id=?", Long.class, id);
        if (count != null && count >= 50) throw new Api.ApiException(HttpStatus.CONFLICT,
                Api.localize("本次会话已满，请开始新会话", "This conversation is full. Start a new conversation."));
        var turns = db.queryForList("SELECT message,answer FROM support_chat_turn WHERE conversation_id=? ORDER BY id DESC LIMIT 3", id);
        Collections.reverse(turns);
        List<Map<String,String>> history = new ArrayList<>();
        for (var turn : turns) {
            history.add(Map.of("role", "user", "content", (String) turn.get("message")));
            String answer = (String) turn.get("answer");
            history.add(Map.of("role", "assistant", "content", answer.substring(0, Math.min(1000, answer.length()))));
        }
        return new Lease(id, token, List.copyOf(history));
    }

    @Transactional
    public void complete(long userId, Lease lease, String message, SupportAnswer answer) {
        requireOwner(userId, lease.id(), true);
        String current = db.queryForObject("SELECT lease_token FROM support_conversation WHERE id=?", String.class, lease.id());
        if (!lease.token().equals(current)) throw new Api.ApiException(HttpStatus.CONFLICT,
                Api.localize("会话已变化，请重新发送", "The conversation changed. Send your question again."));
        try {
            db.update("INSERT INTO support_chat_turn(conversation_id,message,answer,source,citations_json,suggest_escalation) VALUES (?,?,?,?,?,?)",
                    lease.id(), message, answer.answer(), answer.source(), json.writeValueAsString(answer.citations()), answer.suggestEscalation());
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException(e); }
        db.update("UPDATE support_conversation SET lease_token=NULL,busy_until=NULL,updated_at=NOW() WHERE id=?", lease.id());
    }

    public void release(Lease lease) {
        db.update("UPDATE support_conversation SET lease_token=NULL,busy_until=NULL WHERE id=? AND lease_token=?", lease.id(), lease.token());
    }

    public List<Map<String,Object>> list(long userId) {
        return db.queryForList("SELECT c.id,c.updated_at AS updatedAt,COALESCE((SELECT LEFT(t.message,80) FROM support_chat_turn t WHERE t.conversation_id=c.id ORDER BY t.id LIMIT 1),'') AS preview FROM support_conversation c WHERE c.user_id=? ORDER BY c.updated_at DESC,c.id DESC LIMIT 50", userId);
    }

    public Map<String,Object> detail(long userId, String id) {
        validateId(id); requireOwner(userId, id, false);
        var turns = db.queryForList("SELECT message,answer,source,citations_json,suggest_escalation FROM support_chat_turn WHERE conversation_id=? ORDER BY id LIMIT 50", id);
        List<Map<String,Object>> messages = new ArrayList<>();
        for (var turn : turns) {
            messages.add(Map.of("role", "user", "content", turn.get("message")));
            try {
                messages.add(Map.of("role", "assistant", "content", turn.get("answer"), "source", turn.get("source"),
                    "citations", json.readValue((String) turn.get("citations_json"), new TypeReference<List<SupportAnswer.Citation>>() {}),
                    "suggestEscalation", turn.get("suggest_escalation")));
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException(e); }
        }
        return Map.of("conversationId", id, "messages", messages);
    }

    @Transactional
    public void delete(long userId, String id) {
        validateId(id); requireOwner(userId, id, true);
        db.update("DELETE FROM support_conversation WHERE id=? AND user_id=?", id, userId);
    }

    private void requireOwner(long userId, String id, boolean lock) {
        if (db.queryForList("SELECT id FROM support_conversation WHERE id=? AND user_id=?" + (lock ? " FOR UPDATE" : ""), id, userId).isEmpty())
            throw new Api.ApiException(HttpStatus.NOT_FOUND, Api.localize("客服会话不存在", "Support conversation not found."));
    }

    private static void validateId(String id) {
        if (id == null || !id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
            throw new Api.ApiException(HttpStatus.BAD_REQUEST, "参数错误");
    }

    @Scheduled(fixedDelay = 3600000)
    public void prune() {
        db.update("DELETE FROM support_conversation WHERE updated_at<DATE_SUB(NOW(),INTERVAL 30 DAY) AND (busy_until IS NULL OR busy_until<NOW()) LIMIT 100");
        db.update("DELETE FROM support_ai_budget WHERE budget_date<DATE_SUB(CURRENT_DATE,INTERVAL 90 DAY) LIMIT 1000");
    }
}
