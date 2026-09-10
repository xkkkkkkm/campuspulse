package com.campuspulse.support;

import com.campuspulse.common.Api;
import com.campuspulse.security.AuthContext;
import com.campuspulse.security.RequestRateLimiter;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/support")
public class SupportTicketController {
    private final SupportTicketService tickets;
    private final RequestRateLimiter limiter;
    public SupportTicketController(SupportTicketService tickets, RequestRateLimiter limiter) { this.tickets = tickets; this.limiter = limiter; }
    @GetMapping("/tickets")
    public Api.ApiResponse<List<Map<String,Object>>> list(@RequestParam(defaultValue="0") long beforeId, @RequestParam(defaultValue="30") int limit) { return Api.ok(tickets.list(AuthContext.requireUser(), false, beforeId, limit)); }
    @GetMapping("/admin/tickets")
    public Api.ApiResponse<List<Map<String,Object>>> adminList(@RequestParam(defaultValue="0") long beforeId, @RequestParam(defaultValue="50") int limit) { return Api.ok(tickets.list(AuthContext.requireUser(), true, beforeId, limit)); }
    @PostMapping("/tickets")
    public Api.ApiResponse<Map<String,Long>> create(@RequestBody Message request) { return Api.ok(Map.of("id", tickets.create(AuthContext.requireUser(), request.message()))); }
    @GetMapping("/tickets/{id}")
    public Api.ApiResponse<Map<String,Object>> detail(@PathVariable long id, @RequestParam(defaultValue="0") long beforeReplyId) { return Api.ok(tickets.detail(AuthContext.requireUser(), id, beforeReplyId)); }
    @PostMapping("/tickets/{id}/replies")
    public Api.ApiResponse<Void> reply(@PathVariable long id, @RequestBody Message request) {
        AuthContext.User user = AuthContext.requireUser(); limiter.require("support-reply:" + user.id(), 30, 60);
        tickets.reply(user, id, request.message()); return Api.ok();
    }
    @PatchMapping("/admin/tickets/{id}")
    public Api.ApiResponse<Void> status(@PathVariable long id, @RequestBody Status request) { tickets.status(AuthContext.requireUser(), id, request.status()); return Api.ok(); }
    public record Message(String message) {}
    public record Status(String status) {}
}
