package com.campuspulse.realtime;

import com.campuspulse.security.AuthContext;
import com.campuspulse.security.TokenService;
import com.campuspulse.common.Api;
import org.springframework.http.HttpStatus;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatRealtimeService {
    private final JdbcTemplate jdbcTemplate;
    private final TokenService tokenService;
    private final ThreadPoolExecutor sender = new ThreadPoolExecutor(4, 8, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(256), runnable -> { Thread thread = new Thread(runnable, "sse-sender"); thread.setDaemon(true); return thread; }, new ThreadPoolExecutor.AbortPolicy());
    private final ConcurrentHashMap<String, ClientEmitter> emitters = new ConcurrentHashMap<>();

    public ChatRealtimeService(JdbcTemplate jdbcTemplate, TokenService tokenService) {
        this.jdbcTemplate = jdbcTemplate;
        this.tokenService = tokenService;
    }

    public synchronized SseEmitter register(AuthContext.User user, Scope scope) {
        if (emitters.size() >= 200 || emitters.values().stream().filter(c -> c.user().id() == user.id()).count() >= 3)
            throw new Api.ApiException(HttpStatus.TOO_MANY_REQUESTS, "操作过于频繁，请稍后再试");
        SseEmitter emitter = new SseEmitter(60 * 1000L);
        String id = UUID.randomUUID().toString();
        ClientEmitter client = new ClientEmitter(id, emitter, user, scope, new ArrayBlockingQueue<>(32), new AtomicBoolean());
        emitters.put(id, client);
        emitter.onCompletion(() -> emitters.remove(id));
        emitter.onTimeout(() -> emitters.remove(id));
        emitter.onError((ex) -> emitters.remove(id));
        send(client, "CONNECTED", Map.of(
                "type", "CONNECTED",
                "ts", Instant.now().toString()
        ));
        return emitter;
    }

    @Scheduled(fixedRate = 25000)
    public void heartbeat() {
        broadcast(client -> true, "HEARTBEAT", Map.of("type", "HEARTBEAT", "ts", Instant.now().toString()));
    }

    public void notifyTeamMessage(long teamId, long messageId) {
        List<Long> memberIds = jdbcTemplate.queryForList("""
                SELECT user_id
                FROM team_member
                WHERE team_id = ? AND status = 'ACTIVE'
                """, Long.class, teamId);
        if (memberIds.isEmpty()) return;
        Set<Long> members = Set.copyOf(memberIds);
        broadcast(client -> client.scope().type() == ScopeType.TEAM
                && client.scope().scopeId() == teamId
                && members.contains(client.user().id()),
                "TEAM_MESSAGE",
                Map.of(
                        "type", "TEAM_MESSAGE",
                        "scopeId", teamId,
                        "messageId", messageId,
                        "ts", Instant.now().toString()
                ));
    }

    public void notifyDirectMessage(long senderId, long receiverId, long messageId) {
        broadcast(client -> client.scope().type() == ScopeType.DM
                        && ((client.user().id() == senderId && client.scope().scopeId() == receiverId)
                        || (client.user().id() == receiverId && client.scope().scopeId() == senderId)),
                "DM_MESSAGE",
                Map.of(
                        "type", "DM_MESSAGE",
                        "messageId", messageId,
                        "senderId", senderId,
                        "receiverId", receiverId,
                        "ts", Instant.now().toString()
                ));
    }

    public void notifyActivityMessage(long activityId, long messageId) {
        broadcast(client -> client.scope().type() == ScopeType.ACTIVITY && client.scope().scopeId() == activityId,
                "ACTIVITY_MESSAGE",
                Map.of(
                        "type", "ACTIVITY_MESSAGE",
                        "scopeId", activityId,
                        "messageId", messageId,
                        "ts", Instant.now().toString()
                ));
    }

    public boolean canAccessScope(AuthContext.User user, Scope scope) {
        if (scope.type() == ScopeType.TEAM) {
            Long count = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM team_member tm JOIN teams t ON t.id = tm.team_id LEFT JOIN activities a ON a.id = t.activity_id
                    WHERE tm.team_id = ? AND tm.user_id = ? AND tm.status = 'ACTIVE' AND t.status = 'OPEN'
                    AND (COALESCE(t.end_time, t.start_time) IS NULL OR COALESCE(t.end_time, t.start_time) > CURRENT_TIMESTAMP)
                    AND (t.activity_id IS NULL OR (a.status = 'PUBLISHED' AND a.audit_status = 'APPROVED' AND a.teaming_enabled = 1 AND COALESCE(a.end_time, a.start_time) > CURRENT_TIMESTAMP))
                    """, Long.class, scope.scopeId(), user.id());
            return count != null && count > 0;
        }
        if (scope.type() == ScopeType.DM) {
            Long exists = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE id = ? AND status = 1", Long.class, scope.scopeId());
            return exists != null && exists > 0 && scope.scopeId() != user.id();
        }
        Long allowed = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM activities a
                LEFT JOIN registrations r ON r.activity_id = a.id AND r.user_id = ? AND r.status = 'APPROVED'
                WHERE a.id = ? AND a.chat_enabled = 1 AND a.status = 'PUBLISHED' AND a.audit_status = 'APPROVED'
                  AND COALESCE(a.end_time, a.start_time) > CURRENT_TIMESTAMP
                  AND (a.organizer_id = ? OR r.user_id IS NOT NULL OR ?='ADMIN')
                """, Long.class, user.id(), scope.scopeId(), user.id(),user.role());
        return allowed != null && allowed > 0;
    }

    private void broadcast(ClientMatcher matcher, String eventName, Map<String, Object> payload) {
        for (ClientEmitter client : emitters.values()) {
            if (!matcher.matches(client)) continue;
            send(client, eventName, payload);
        }
    }

    private void send(ClientEmitter client, String eventName, Map<String, Object> payload) {
        if (!client.queue().offer(new Event(eventName, payload))) { close(client); return; }
        schedule(client);
    }

    private void schedule(ClientEmitter client) {
        if (!client.sending().compareAndSet(false, true)) return;
        try {
            sender.execute(() -> {
                try {
                    Event event;
                    while (emitters.containsKey(client.id()) && (event = client.queue().poll()) != null) {
                        // Revalidate immediately before each send, including heartbeat and initial connection.
                        if (!tokenService.isCurrent(client.user()) || !canAccessScope(client.user(), client.scope())) { close(client); return; }
                        client.emitter().send(SseEmitter.event().name(event.name()).data(event.payload()));
                    }
                } catch (Exception ex) { close(client); }
                finally {
                    client.sending().set(false);
                    if (emitters.containsKey(client.id()) && !client.queue().isEmpty()) schedule(client);
                }
            });
        } catch (RejectedExecutionException ex) { client.sending().set(false); close(client); }
    }
    private void close(ClientEmitter client) {
        emitters.remove(client.id());
        client.queue().clear();
        client.emitter().complete();
    }
    @PreDestroy
    public void shutdown() {
        emitters.values().forEach(this::close);
        sender.shutdownNow();
    }
    public int connectedClients() { return emitters.size(); }
    private record Event(String name, Map<String, Object> payload) {}

    public record Scope(ScopeType type, long scopeId) {
    }

    public enum ScopeType {
        TEAM,
        DM,
        ACTIVITY
    }

    private record ClientEmitter(String id, SseEmitter emitter, AuthContext.User user, Scope scope, ArrayBlockingQueue<Event> queue, AtomicBoolean sending) {
    }

    @FunctionalInterface
    private interface ClientMatcher {
        boolean matches(ClientEmitter client);
    }
}
