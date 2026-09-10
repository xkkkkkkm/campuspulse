package com.campuspulse.message;

import com.campuspulse.common.Api;
import com.campuspulse.activity.ActivityRepository;
import org.springframework.transaction.annotation.Transactional;
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
@RequestMapping("/api/activities")
public class ActivityChatController {
    private final JdbcTemplate jdbcTemplate;
    private final MessageService messages;

    public ActivityChatController(JdbcTemplate jdbcTemplate, MessageService messages) {
        this.messages = messages;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostMapping("/{id}/chat/enable")
    @Transactional
    public Api.ApiResponse<Void> enableChat(@PathVariable long id, @RequestParam(defaultValue = "true") boolean enable) {
        AuthContext.User p = AuthContext.requireUser();
        var activity = jdbcTemplate.queryForList("SELECT * FROM activities WHERE id=? FOR UPDATE",id);
        if (activity.isEmpty()) throw new Api.ApiException(HttpStatus.NOT_FOUND,"活动不存在");
        ActivityRepository.requireLive(activity.get(0));
        Long owner = ((Number)activity.get(0).get("organizer_id")).longValue();
        if (owner == null) throw new Api.ApiException(HttpStatus.NOT_FOUND, "活动不存在");
        if (!owner.equals(p.id()) && !"ADMIN".equals(p.role())) throw new Api.ApiException(HttpStatus.FORBIDDEN, "无权限");
        jdbcTemplate.update("UPDATE activities SET chat_enabled = ?, version = version + 1 WHERE id = ?", enable ? 1 : 0, id);
        return Api.ok();
    }

    @GetMapping("/{id}/chat/enabled")
    public Api.ApiResponse<Map<String, Object>> chatEnabled(@PathVariable long id) {
        List<Integer> flags = jdbcTemplate.queryForList("SELECT IF(status='PUBLISHED' AND audit_status='APPROVED' AND COALESCE(end_time,start_time)>CURRENT_TIMESTAMP,chat_enabled,0) FROM activities WHERE id = ? LIMIT 1", Integer.class, id);
        if(flags.isEmpty()) throw new Api.ApiException(HttpStatus.NOT_FOUND,"活动不存在");
        return Api.ok(Map.of("enabled", Optional.ofNullable(flags.get(0)).orElse(0) == 1));
    }

    @GetMapping("/{id}/chat/messages")
    public Api.ApiResponse<List<Item>> list(@PathVariable long id,
                                            @RequestParam(required = false) Long afterId,
                                              @RequestParam(required = false) Long beforeId,
                                            @RequestParam(defaultValue = "50") int size) {
        List<Map<String,Object>> rows = messages.list(MessageService.Channel.ACTIVITY, id, afterId, beforeId, size);
        List<Item> list = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            list.add(new Item(
                    ((Number) r.get("id")).longValue(),
                    ((Number) r.get("sender_id")).longValue(),
                    String.valueOf(r.get("nickname")),
                    String.valueOf(r.get("content")),
                    toLocalDateTimeString(r.get("created_at")),
                    String.valueOf(r.get("content_type")),(String)r.get("image_url"),((Number)r.get("read_count")).intValue()
            ));
        }
        return Api.ok(list);
    }

    @PostMapping("/{id}/chat/messages")
    public Api.ApiResponse<Map<String, Object>> send(@PathVariable long id, @RequestBody Send req) {
        if (req == null) throw new Api.ApiException(HttpStatus.BAD_REQUEST, "参数错误");
        return Api.ok(Map.of("id", messages.send(MessageService.Channel.ACTIVITY, id, req.content(), req.clientMessageId(), req.contentType(), req.imageUrl())));
    }

    public record Item(long id, long senderId, String senderNickname, String content, String createdAt, String contentType, String imageUrl, int readCount) {
    }

    public record Send(String content, String clientMessageId, String contentType, String imageUrl) {
        public Send(String content,String clientMessageId) {this(content,clientMessageId,"TEXT",null);}
    }

    @PostMapping("/{id}/chat/read")
    public Api.ApiResponse<Void> read(@PathVariable long id, @RequestBody DirectMessageController.MarkRead req) {
        if(req==null) throw new Api.ApiException(HttpStatus.BAD_REQUEST,"参数错误");
        messages.markRead(MessageService.Channel.ACTIVITY,id,req.lastReadId());return Api.ok();
    }

    private static String toLocalDateTimeString(Object value) {
        if (value == null) return "";
        if (value instanceof LocalDateTime ldt) return ldt.toString();
        if (value instanceof Timestamp ts) return ts.toLocalDateTime().toString();
        if (value instanceof java.util.Date d) return new Timestamp(d.getTime()).toLocalDateTime().toString();
        return String.valueOf(value).replace(" ", "T");
    }
}
