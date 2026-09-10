package com.campuspulse.realtime;

import com.campuspulse.security.AuthContext;
import com.campuspulse.security.TokenService;
import com.campuspulse.common.Api;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class StreamTicketService {
    private static final long TTL_SECONDS = 60;
    private final TokenService tokenService;
    public StreamTicketService(TokenService tokenService) { this.tokenService = tokenService; }
    private final ConcurrentHashMap<String, Ticket> tickets = new ConcurrentHashMap<>();

    public synchronized String issue(AuthContext.User user) {
        long now = Instant.now().getEpochSecond();
        tickets.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
        if (tickets.size() >= 2048 || tickets.values().stream().filter(t -> t.user().id() == user.id()).count() >= 6) throw new Api.ApiException(HttpStatus.TOO_MANY_REQUESTS, "操作过于频繁，请稍后再试");
        String ticket = UUID.randomUUID().toString();
        tickets.put(ticket, new Ticket(user, now + TTL_SECONDS));
        return ticket;
    }

    public Optional<AuthContext.User> consume(String ticket) {
        if (ticket == null || ticket.isBlank()) return Optional.empty();
        Ticket issued = tickets.remove(ticket);
        if (issued == null || issued.expiresAt() <= Instant.now().getEpochSecond() || !tokenService.isCurrent(issued.user())) return Optional.empty();
        return Optional.of(issued.user());
    }

    private record Ticket(AuthContext.User user, long expiresAt) {
    }
}
