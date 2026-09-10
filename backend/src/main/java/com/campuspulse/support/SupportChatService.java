package com.campuspulse.support;

import com.campuspulse.common.Api;
import com.campuspulse.security.AuthContext;
import com.campuspulse.security.RequestRateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.Semaphore;

@Service
public class SupportChatService {
    private static final Logger log = LoggerFactory.getLogger(SupportChatService.class);
    private final SupportAgentClient agent;
    private final SupportConversationService conversations;
    private final RequestRateLimiter limiter;
    private final JdbcTemplate db;
    private final boolean generationEnabled;
    private final int dailyLimit;
    private final Semaphore slots = new Semaphore(4);

    public SupportChatService(SupportAgentClient agent, SupportConversationService conversations,
            RequestRateLimiter limiter, JdbcTemplate db,
            @Value("${app.support.generation-enabled:false}") boolean generationEnabled,
            @Value("${app.support.max-daily-generations:100}") int dailyLimit) {
        this.agent = agent; this.conversations = conversations; this.limiter = limiter; this.db = db;
        this.generationEnabled = generationEnabled; this.dailyLimit = Math.max(0, dailyLimit);
    }
    public record Reply(String answer, String conversationId, String source, boolean escalated, Long ticketId,
                        List<SupportAnswer.Citation> citations, boolean suggestEscalation) {}

    public Reply answer(String input, String conversationId, AuthContext.User user, String address) {
        String question = SupportTicketService.validate(input, 500);
        if (user != null) limiter.require("support-user:" + user.id(), 20, 60);
        else {
            limiter.require("support-ip:" + address, 30, 60);
            if (conversationId != null && !conversationId.isBlank())
                throw new Api.ApiException(HttpStatus.UNAUTHORIZED, "未登录或登录已过期");
        }
        String locale = LocaleContextHolder.getLocale().getLanguage().equals("zh") ? "zh-CN" : "en-US";
        SupportConversationService.Lease lease = user == null ? null : conversations.begin(user.id(), conversationId);
        try {
            SupportAnswer answer = null;
            if (agent.enabled() && slots.tryAcquire()) {
                try {
                    boolean generate = user != null && generationEnabled && reserveGeneration(user.id());
                    answer = agent.answer(question, locale, lease == null ? List.of() : lease.history(), generate);
                } catch (Exception e) {
                    // Do not log prompts, provider credentials, response bodies or personal chat history.
                    log.warn("Support graph unavailable; using local guide ({})", e.getClass().getSimpleName());
                } finally { slots.release(); }
            }
            if (answer == null) answer = LocalSupportKnowledge.answer(question, locale);
            if (lease != null) conversations.complete(user.id(), lease, question, answer);
            return new Reply(answer.answer(), lease == null ? null : lease.id(), answer.source(), false, null,
                    answer.citations(), answer.suggestEscalation());
        } finally {
            if (lease != null) conversations.release(lease);
        }
    }

    private boolean reserveGeneration(long userId) {
        try { limiter.require("support-generation:" + userId, 10, 3600); }
        catch (Api.ApiException limited) { return false; }
        java.sql.Date day = java.sql.Date.valueOf(LocalDate.now(ZoneOffset.UTC));
        db.update("INSERT INTO support_ai_budget(budget_date,request_count) VALUES (?,0) ON DUPLICATE KEY UPDATE budget_date=VALUES(budget_date)", day);
        return db.update("UPDATE support_ai_budget SET request_count=request_count+1 WHERE budget_date=? AND request_count<?", day, dailyLimit) == 1;
    }
}
