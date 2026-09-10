package com.campuspulse.message;

import com.campuspulse.common.Api;
import com.campuspulse.realtime.ChatRealtimeService;
import com.campuspulse.security.AuthContext;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.web.bind.annotation.*;

import java.sql.Timestamp;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/dm")
public class DirectMessageController {
    private final JdbcTemplate jdbcTemplate;
    private final MessageService messages;

    public DirectMessageController(JdbcTemplate jdbcTemplate, MessageService messages) {
        this.messages = messages;
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/threads")
    public Api.ApiResponse<List<ThreadItem>> threads(@RequestParam(defaultValue = "50") int size,@RequestParam(defaultValue="1") int page) {
        AuthContext.User p = AuthContext.requireUser();
        int limit = Math.min(Math.max(size, 1), 100);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT t.peer_id,
                       u.nickname,
                       u.avatar_url,
                       m.id AS last_message_id,
                       m.content AS last_message,
                       m.created_at AS last_created_at,
                       COALESCE(rs.last_read_id, 0) AS last_read_id,
                       (SELECT COUNT(*)
                        FROM dm_message dm2
                        WHERE dm2.sender_id = t.peer_id AND dm2.receiver_id = ? AND dm2.id > COALESCE(rs.last_read_id, 0)
                       ) AS unread_count
                FROM (
                    SELECT peer_id, MAX(id) AS last_id
                    FROM (
                        SELECT CASE WHEN sender_id = ? THEN receiver_id ELSE sender_id END AS peer_id, id
                        FROM dm_message
                        WHERE sender_id = ? OR receiver_id = ?
                    ) x
                    GROUP BY peer_id
                    ORDER BY last_id DESC
                    LIMIT ? OFFSET ?
                ) t
                JOIN dm_message m ON m.id = t.last_id
                JOIN users u ON u.id = t.peer_id
                LEFT JOIN dm_read_state rs ON rs.user_id = ? AND rs.peer_id = t.peer_id
                ORDER BY t.last_id DESC
                """, p.id(), p.id(), p.id(), p.id(), limit, (Math.min(Math.max(page,1),1000000)-1)*limit, p.id());

        List<ThreadItem> list = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            list.add(new ThreadItem(
                    ((Number) r.get("peer_id")).longValue(),
                    String.valueOf(r.get("nickname")),
                    r.get("avatar_url") == null ? null : String.valueOf(r.get("avatar_url")),
                    ((Number) r.get("last_message_id")).longValue(),
                    String.valueOf(r.get("last_message")),
                    toLocalDateTimeString(r.get("last_created_at")),
                    ((Number) r.get("unread_count")).intValue()
            ));
        }
        return Api.ok(list);
    }

    @GetMapping("/{peerId}/messages")
    public Api.ApiResponse<List<DMItem>> list(@PathVariable long peerId,
                                              @RequestParam(required = false) Long afterId,
                                              @RequestParam(required = false) Long beforeId,
                                              @RequestParam(defaultValue = "50") int size) {
        List<Map<String,Object>> rows = messages.list(MessageService.Channel.DIRECT, peerId, afterId, beforeId, size);
        List<DMItem> list = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            list.add(new DMItem(
                    ((Number) r.get("id")).longValue(),
                    ((Number) r.get("sender_id")).longValue(),
                    ((Number) r.get("receiver_id")).longValue(),
                    String.valueOf(r.get("content")),
                    toLocalDateTimeString(r.get("created_at")),
                    String.valueOf(r.get("content_type")),(String)r.get("image_url"),((Number)r.get("read_count")).intValue()
            ));
        }
        return Api.ok(list);
    }

    @PostMapping("/{peerId}/read")
    public Api.ApiResponse<Void> markRead(@PathVariable long peerId, @RequestBody MarkRead req) {
        AuthContext.User p = AuthContext.requireUser();
        long lastReadId = req == null ? 0 : Math.max(0, req.lastReadId());
        if (peerId <= 0) throw new Api.ApiException(HttpStatus.BAD_REQUEST, "参数错误");
        Long maximum=jdbcTemplate.queryForObject("SELECT COALESCE(MAX(id),0) FROM dm_message WHERE (sender_id=? AND receiver_id=?) OR (sender_id=? AND receiver_id=?)",Long.class,p.id(),peerId,peerId,p.id());
        lastReadId = Math.min(lastReadId, maximum == null ? 0 : maximum);
        jdbcTemplate.update("""
                INSERT INTO dm_read_state (user_id, peer_id, last_read_id)
                VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE last_read_id = GREATEST(last_read_id, VALUES(last_read_id))
                """, p.id(), peerId, lastReadId);
        return Api.ok();
    }

    @PostMapping("/{peerId}/messages")
    public Api.ApiResponse<Map<String, Object>> send(@PathVariable long peerId, @RequestBody SendDM req) {
        if (req == null) throw new Api.ApiException(HttpStatus.BAD_REQUEST, "参数错误");
        return Api.ok(Map.of("id", messages.send(MessageService.Channel.DIRECT, peerId, req.content(), req.clientMessageId(), req.contentType(), req.imageUrl())));
    }

    public record DMItem(long id, long senderId, long receiverId, String content, String createdAt, String contentType, String imageUrl, int readCount) {
    }

    public record SendDM(String content, String clientMessageId, String contentType, String imageUrl) {
        public SendDM(String content,String clientMessageId) {this(content,clientMessageId,"TEXT",null);}
    }

    public record MarkRead(long lastReadId) {
    }

    public record ThreadItem(long peerId, String peerNickname, String peerAvatarUrl,
                             long lastMessageId, String lastMessage, String lastCreatedAt, int unreadCount) {
    }

    private static String toLocalDateTimeString(Object value) {
        if (value == null) return "";
        if (value instanceof LocalDateTime ldt) return ldt.toString();
        if (value instanceof Timestamp ts) return ts.toLocalDateTime().toString();
        if (value instanceof java.util.Date d) return new Timestamp(d.getTime()).toLocalDateTime().toString();
        return String.valueOf(value).replace(" ", "T");
    }
}
