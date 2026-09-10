package com.campuspulse.realtime;

import com.campuspulse.common.Api;
import com.campuspulse.security.AuthContext;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@RestController
@RequestMapping("/api/realtime/chat")
public class ChatRealtimeController {
    private final StreamTicketService ticketService;
    private final ChatRealtimeService realtimeService;

    public ChatRealtimeController(StreamTicketService ticketService, ChatRealtimeService realtimeService) {
        this.ticketService = ticketService;
        this.realtimeService = realtimeService;
    }

    @PostMapping("/ticket")
    public Api.ApiResponse<Map<String, Object>> ticket() {
        return Api.ok(Map.of("ticket", ticketService.issue(AuthContext.requireUser()), "expiresIn", 60));
    }

    @GetMapping("/stream")
    public SseEmitter stream(@RequestParam(required = false) String ticket,
                             @RequestParam(required = false) Long teamId,
                             @RequestParam(required = false) Long userId,
                             @RequestParam(required = false) Long activityId) {
        AuthContext.User user = ticketService.consume(ticket)
                .orElseThrow(() -> new Api.ApiException(HttpStatus.UNAUTHORIZED, "实时连接凭证无效或已过期"));
        ChatRealtimeService.Scope scope = resolveScope(teamId, userId, activityId);
        if (!realtimeService.canAccessScope(user, scope)) {
            throw new Api.ApiException(HttpStatus.FORBIDDEN, "无权限订阅该聊天频道");
        }
        return realtimeService.register(user, scope);
    }

    private ChatRealtimeService.Scope resolveScope(Long teamId, Long userId, Long activityId) {
        int count = (isPositive(teamId) ? 1 : 0) + (isPositive(userId) ? 1 : 0) + (isPositive(activityId) ? 1 : 0);
        if (count != 1) {
            throw new Api.ApiException(HttpStatus.BAD_REQUEST, "实时频道参数错误");
        }
        if (isPositive(teamId)) {
            return new ChatRealtimeService.Scope(ChatRealtimeService.ScopeType.TEAM, teamId);
        }
        if (isPositive(userId)) {
            return new ChatRealtimeService.Scope(ChatRealtimeService.ScopeType.DM, userId);
        }
        return new ChatRealtimeService.Scope(ChatRealtimeService.ScopeType.ACTIVITY, activityId);
    }

    private boolean isPositive(Long value) {
        return value != null && value > 0;
    }
}
