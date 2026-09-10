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
public class ChatController {
    private final MessageService messages;

    public ChatController(MessageService messages) {
        this.messages = messages;
    }

    @GetMapping("/api/teams/{teamId}/messages")
    public Api.ApiResponse<List<MessageItem>> list(@PathVariable long teamId,
                                                   @RequestParam(required = false) Long afterId,
                                              @RequestParam(required = false) Long beforeId,
                                                   @RequestParam(defaultValue = "50") int size) {
        List<Map<String,Object>> rows = messages.list(MessageService.Channel.TEAM, teamId, afterId, beforeId, size);
        List<MessageItem> list = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            list.add(new MessageItem(
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

    @PostMapping("/api/teams/{teamId}/messages")
    public Api.ApiResponse<Map<String, Object>> send(@PathVariable long teamId, @RequestBody SendMessageRequest req) {
        if (req == null) throw new Api.ApiException(HttpStatus.BAD_REQUEST, "参数错误");
        return Api.ok(Map.of("id", messages.send(MessageService.Channel.TEAM, teamId, req.content(), req.clientMessageId(), req.contentType(), req.imageUrl())));
    }

    @PostMapping("/api/teams/{teamId}/read")
    public Api.ApiResponse<Void> read(@PathVariable long teamId, @RequestBody DirectMessageController.MarkRead req) {
        if(req==null) throw new Api.ApiException(HttpStatus.BAD_REQUEST,"参数错误");
        messages.markRead(MessageService.Channel.TEAM,teamId,req.lastReadId());return Api.ok();
    }

    private static String toLocalDateTimeString(Object value) {
        if (value == null) return "";
        if (value instanceof LocalDateTime ldt) return ldt.toString();
        if (value instanceof Timestamp ts) return ts.toLocalDateTime().toString();
        if (value instanceof java.util.Date d) return new Timestamp(d.getTime()).toLocalDateTime().toString();
        return String.valueOf(value).replace(" ", "T");
    }

    public record MessageItem(long id, long senderId, String senderNickname, String content, String createdAt, String contentType, String imageUrl, int readCount) {
    }

    public record SendMessageRequest(String content, String clientMessageId, String contentType, String imageUrl) {
        public SendMessageRequest(String content,String clientMessageId) {this(content,clientMessageId,"TEXT",null);}
    }
}
