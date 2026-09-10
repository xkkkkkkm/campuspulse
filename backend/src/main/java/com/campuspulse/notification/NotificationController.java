package com.campuspulse.notification;

import com.campuspulse.common.Api;
import com.campuspulse.security.AuthContext;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final JdbcTemplate jdbcTemplate;

    public NotificationController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public Api.ApiResponse<NotificationPage> list(@RequestParam(defaultValue = "all") String status,
                                                  @RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        AuthContext.User p = requireLogin();
        purgeExpiredNotifications();
        String normalizedStatus = status == null ? "all" : status.trim().toLowerCase();
        if (!List.of("all", "read", "unread").contains(normalizedStatus)) {
            throw new Api.ApiException(HttpStatus.BAD_REQUEST, "通知筛选条件不支持");
        }
        int pageNo = Math.max(page, 1);
        int limit = Math.min(Math.max(size, 1), 100);
        int offset = (pageNo - 1) * limit;
        String where = switch (normalizedStatus) {
            case "read" -> "user_id = ? AND read_flag = 1";
            case "unread" -> "user_id = ? AND read_flag = 0";
            default -> "user_id = ?";
        };
        String countSql = "SELECT COUNT(*) FROM notifications WHERE " + where;
        String listSql = "SELECT id, type, title, content, read_flag, created_at " +
                "FROM notifications " +
                "WHERE " + where + " " +
                "ORDER BY created_at DESC " +
                "LIMIT ? OFFSET ?";
        Long total = jdbcTemplate.queryForObject(countSql, Long.class, p.id());
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(listSql, p.id(), limit, offset);
        List<NotificationItem> list = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            list.add(new NotificationItem(
                    ((Number) r.get("id")).longValue(),
                    String.valueOf(r.get("type")),
                    String.valueOf(r.get("title")),
                    String.valueOf(r.get("content")),
                    ((Number) r.get("read_flag")).intValue() == 1,
                    toLocalDateTimeString(r.get("created_at"))
            ));
        }
        return Api.ok(new NotificationPage(list, total == null ? 0 : total, pageNo, limit));
    }

    @PostMapping("/{id}/read")
    public Api.ApiResponse<Void> markRead(@PathVariable long id) {
        AuthContext.User p = requireLogin();
        jdbcTemplate.update("UPDATE notifications SET read_flag = 1 WHERE id = ? AND user_id = ?", id, p.id());
        return Api.ok();
    }

    @PostMapping("/read-all")
    public Api.ApiResponse<Void> readAll() {
        AuthContext.User p = requireLogin();
        jdbcTemplate.update("UPDATE notifications SET read_flag = 1 WHERE user_id = ?", p.id());
        return Api.ok();
    }

    @DeleteMapping("/{id}")
    public Api.ApiResponse<Void> delete(@PathVariable long id) {
        AuthContext.User p = requireLogin();
        jdbcTemplate.update("DELETE FROM notifications WHERE id = ? AND user_id = ?", id, p.id());
        return Api.ok();
    }

    private AuthContext.User requireLogin() {
        return AuthContext.requireUser();
    }

    private void purgeExpiredNotifications() {
        jdbcTemplate.update("""
                DELETE FROM notifications
                WHERE created_at < DATE_SUB(NOW(), INTERVAL 6 MONTH)
                """);
    }

    private static String toLocalDateTimeString(Object value) {
        if (value == null) return "";
        if (value instanceof LocalDateTime ldt) return ldt.toString();
        if (value instanceof Timestamp ts) return ts.toLocalDateTime().toString();
        if (value instanceof java.util.Date d) return new Timestamp(d.getTime()).toLocalDateTime().toString();
        return String.valueOf(value).replace(" ", "T");
    }

    public record NotificationItem(long id, String type, String title, String content, boolean read, String createdAt) {
    }

    public record NotificationPage(List<NotificationItem> items, long total, int page, int size) {
    }
}
