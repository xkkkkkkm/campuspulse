package com.campuspulse.security;

import com.campuspulse.common.Api;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;

/** Bounded, single-instance fixed-window limiter. Never trusts client forwarding headers. */
@Component
public class RequestRateLimiter {
    private final Map<String, Window> windows = new HashMap<>();
    private final Clock clock;
    public RequestRateLimiter() { this(Clock.systemUTC()); }
    RequestRateLimiter(Clock clock) { this.clock = clock; }
    public synchronized void require(String key, int limit, long seconds) {
        long now = clock.millis();
        windows.entrySet().removeIf(e -> e.getValue().until <= now);
        Window window = windows.get(key);
        if (window == null) {
            if (windows.size() >= 10000) throw limited();
            window = new Window(now + seconds * 1000);
            windows.put(key, window);
        }
        if (window.count >= limit) throw limited();
        window.count++;
    }
    private static Api.ApiException limited() { return new Api.ApiException(HttpStatus.TOO_MANY_REQUESTS, "操作过于频繁，请稍后再试"); }
    private static final class Window { final long until; int count; Window(long until) { this.until = until; } }
}
