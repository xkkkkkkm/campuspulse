package com.campuspulse.support;

import com.campuspulse.common.Api;
import com.campuspulse.security.AuthContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

/** Public support boundary; the internal graph has no database or ticket-writing privileges. */
@RestController
@RequestMapping("/api/support")
public class SupportController {
    private final SupportChatService chat;
    private final SupportConversationService conversations;
    private final SupportTicketService tickets;

    public SupportController(SupportChatService chat, SupportConversationService conversations, SupportTicketService tickets) {
        this.chat = chat; this.conversations = conversations; this.tickets = tickets;
    }

    @PostMapping("/chat")
    public Api.ApiResponse<SupportChatService.Reply> chat(@RequestBody SupportRequest request, HttpServletRequest http) {
        return Api.ok(chat.answer(request.message(), request.conversationId(), AuthContext.userOrNull(), http.getRemoteAddr()));
    }

    @PostMapping("/escalate")
    public Api.ApiResponse<SupportChatService.Reply> escalate(@RequestBody SupportRequest request) {
        String question = request.message() == null || request.message().isBlank()
                ? Api.localize("请求人工客服协助", "Request help from the support team") : request.message();
        long id = tickets.create(AuthContext.requireUser(), question);
        return Api.ok(new SupportChatService.Reply(Api.localize("工单已提交，请在我的工单中查看进度。编号：", "Support ticket created. Follow progress in My tickets. Ticket #") + id,
                null, "HUMAN_ESCALATION", true, id, java.util.List.of(), false));
    }

    @GetMapping("/conversations")
    public Api.ApiResponse<java.util.List<Map<String,Object>>> conversations() {
        return Api.ok(conversations.list(AuthContext.requireUser().id()));
    }

    @GetMapping("/conversations/{id}")
    public Api.ApiResponse<Map<String,Object>> history(@PathVariable String id) {
        return Api.ok(conversations.detail(AuthContext.requireUser().id(), id));
    }

    @DeleteMapping("/conversations/{id}")
    public Api.ApiResponse<Void> delete(@PathVariable String id) {
        conversations.delete(AuthContext.requireUser().id(), id); return Api.ok();
    }

    @GetMapping("/knowledge")
    public Api.ApiResponse<Map<String,Object>> knowledge() {
        return Api.ok(Map.of("source", "CampusPulse support guides", "version", "2026-09", "topics", LocalSupportKnowledge.topics()));
    }

    public record SupportRequest(String message, String conversationId) {}
}
