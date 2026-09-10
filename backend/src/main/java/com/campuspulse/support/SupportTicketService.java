package com.campuspulse.support;

import com.campuspulse.common.Api;
import com.campuspulse.security.AuthContext;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Statement;
import java.util.*;

@Service
public class SupportTicketService {
    private final JdbcTemplate jdbc;
    public SupportTicketService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public long create(AuthContext.User user, String question) {
        String subject = validate(question, 500);
        jdbc.queryForList("SELECT id FROM users WHERE id = ? FOR UPDATE", user.id());
        var duplicate = jdbc.queryForList("SELECT id FROM support_ticket WHERE user_id = ? AND subject = ? AND status <> 'CLOSED' ORDER BY id DESC LIMIT 1", Long.class, user.id(), subject);
        if (!duplicate.isEmpty()) return duplicate.get(0);
        Long open = jdbc.queryForObject("SELECT COUNT(*) FROM support_ticket WHERE user_id = ? AND status <> 'CLOSED'", Long.class, user.id());
        Long recent = jdbc.queryForObject("SELECT COUNT(*) FROM support_ticket WHERE user_id = ? AND created_at > DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 1 DAY)", Long.class, user.id());
        if ((open != null && open >= 3) || (recent != null && recent >= 10)) throw new Api.ApiException(HttpStatus.TOO_MANY_REQUESTS, Api.localize("请先处理现有工单", "Please follow up on your existing support tickets."));
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("INSERT INTO support_ticket (user_id, subject) VALUES (?, ?)", Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, user.id()); statement.setString(2, subject); return statement;
        }, key);
        long id = Objects.requireNonNull(key.getKey()).longValue();
        notifyAdmins(id, "New support ticket", subject);
        return id;
    }

    public List<Map<String, Object>> list(AuthContext.User user, boolean admin, long beforeId, int limit) {
        if (admin && !"ADMIN".equals(user.role())) throw denied();
        int size = Math.max(1, Math.min(100, limit));
        return jdbc.queryForList("SELECT id, user_id AS userId, subject, status, created_at AS createdAt, updated_at AS updatedAt FROM support_ticket WHERE "
                + (admin ? "1=1" : "user_id = " + user.id()) + " AND id < ? ORDER BY id DESC LIMIT ?", beforeId <= 0 ? Long.MAX_VALUE : beforeId, size);
    }

    public Map<String, Object> detail(AuthContext.User user, long id, long beforeReplyId) {
        Map<String,Object> ticket = accessible(user, id, false);
        List<Map<String,Object>> replies = jdbc.queryForList("SELECT r.id, r.author_id AS authorId, u.nickname AS authorName, u.role AS authorRole, r.content AS message, r.created_at AS createdAt FROM support_reply r JOIN users u ON u.id = r.author_id WHERE r.ticket_id = ? AND r.id < ? ORDER BY r.id DESC LIMIT 101", id, beforeReplyId <= 0 ? Long.MAX_VALUE : beforeReplyId);
        boolean hasMore = replies.size() > 100;
        if (hasMore) replies.remove(replies.size() - 1);
        Collections.reverse(replies);
        return Map.of("ticket", ticket, "replies", replies, "hasMoreReplies", hasMore);
    }

    @Transactional
    public void reply(AuthContext.User user, long id, String content) {
        String message = validate(content, 4000);
        Map<String,Object> ticket = accessible(user, id, true);
        if ("CLOSED".equals(ticket.get("status"))) throw new Api.ApiException(HttpStatus.CONFLICT, Api.localize("工单已关闭", "The support ticket is closed."));
        jdbc.update("INSERT INTO support_reply (ticket_id, author_id, content) VALUES (?, ?, ?)", id, user.id(), message);
        jdbc.update("UPDATE support_ticket SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", "ADMIN".equals(user.role()) ? "IN_PROGRESS" : "OPEN", id);
        long owner = ((Number) ticket.get("userId")).longValue();
        if (user.id() != owner) jdbc.update("INSERT INTO notifications (user_id, type, title, content) VALUES (?, 'SUPPORT_REPLY', ?, ?)", owner, "Support ticket #" + id, message.length() > 900 ? message.substring(0,900) : message);
        else notifyAdmins(id, "Support ticket reply", message);
    }

    @Transactional
    public void status(AuthContext.User user, long id, String status) {
        if (!"ADMIN".equals(user.role())) throw denied();
        if (status == null || !Set.of("OPEN", "IN_PROGRESS", "CLOSED").contains(status)) throw new Api.ApiException(HttpStatus.BAD_REQUEST, "参数错误");
        Map<String,Object> ticket = accessible(user, id, true);
        if (status.equals(ticket.get("status"))) return;
        jdbc.update("UPDATE support_ticket SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", status, id);
        jdbc.update("INSERT INTO support_reply (ticket_id, author_id, content) VALUES (?, ?, ?)", id, user.id(), "Status: " + status);
        jdbc.update("INSERT INTO notifications (user_id, type, title, content) VALUES (?, 'SUPPORT_STATUS', ?, ?)", ticket.get("userId"), "Support ticket #" + id, status);
    }

    private Map<String,Object> accessible(AuthContext.User user, long id, boolean lock) {
        var rows = jdbc.queryForList("SELECT id, user_id AS userId, subject, status, created_at AS createdAt, updated_at AS updatedAt FROM support_ticket WHERE id = ?" + (lock ? " FOR UPDATE" : ""), id);
        if (rows.isEmpty()) throw new Api.ApiException(HttpStatus.NOT_FOUND, Api.localize("工单不存在", "Support ticket not found."));
        var ticket = rows.get(0);
        if (((Number) ticket.get("userId")).longValue() != user.id() && !"ADMIN".equals(user.role())) throw denied();
        return ticket;
    }
    private void notifyAdmins(long id, String title, String content) {
        jdbc.update("INSERT INTO notifications (user_id, type, title, content) SELECT id, 'SUPPORT_ESCALATION', ?, ? FROM users WHERE role = 'ADMIN' AND status = 1", title + " #" + id, content.length() > 900 ? content.substring(0,900) : content);
    }
    static String validate(String value, int max) {
        if (value == null || value.isBlank() || value.trim().length() > max) throw new Api.ApiException(HttpStatus.BAD_REQUEST, "参数错误");
        return value.trim();
    }
    private static Api.ApiException denied() { return new Api.ApiException(HttpStatus.FORBIDDEN, "无权限"); }
}
