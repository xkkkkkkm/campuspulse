package com.campuspulse.config;

import com.campuspulse.common.Api;
import com.campuspulse.security.AuthContext;
import com.campuspulse.security.TokenService;
import com.campuspulse.security.RequestRateLimiter;
import java.util.Set;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ApiAuthConfig implements WebMvcConfigurer {
    private final TokenService tokenService;
    private final ObjectMapper objectMapper;
    private final RequestRateLimiter limiter;

    public ApiAuthConfig(TokenService tokenService, ObjectMapper objectMapper, RequestRateLimiter limiter) {
        this.tokenService = tokenService;
        this.objectMapper = objectMapper;
        this.limiter = limiter;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new ApiAuthInterceptor(tokenService, objectMapper, limiter)).addPathPatterns("/api/**");
    }

    static final class ApiAuthInterceptor implements HandlerInterceptor {
        private final TokenService tokenService;
        private final ObjectMapper objectMapper;
        private final RequestRateLimiter limiter;

        ApiAuthInterceptor(TokenService tokenService, ObjectMapper objectMapper, RequestRateLimiter limiter) {
            this.tokenService = tokenService;
            this.objectMapper = objectMapper;
            this.limiter = limiter;
        }

        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
            String path = request.getRequestURI();
            String method = request.getMethod();

            if (HttpMethod.OPTIONS.matches(method)) {
                return true;
            }

            AuthContext.clear();
            if (HttpMethod.POST.matches(method) && path.startsWith("/api/auth/")) {
                limiter.require("auth:" + request.getRemoteAddr(), 60, 60);
                if (path.contains("send")) limiter.require("mail:" + request.getRemoteAddr(), 10, 600);
            }
            if (HttpMethod.POST.matches(method) && Set.of("/api/auth/login", "/api/auth/register", "/api/auth/login-email", "/api/auth/register-email", "/api/auth/password/reset", "/api/auth/email/send").contains(path)) {
                setUserIfPresent(request);
                return true;
            }
            if (HttpMethod.GET.matches(method) && path.equals("/api/realtime/chat/stream")) return true;
            // Exact route allowlist: newly added API routes require authentication by default.
            boolean publicGet = HttpMethod.GET.matches(method) && (
                    Set.of("/api/tags", "/api/search", "/api/activities", "/api/activities/highlights", "/api/teams", "/api/teams/page", "/api/recommendations/feed", "/api/recommendations/activities", "/api/recommendations/teams", "/api/support/knowledge", "/api/profile/default-avatars").contains(path)
                    || path.matches("/api/activities/[0-9]+(?:/teams)?") || path.matches("/api/teams/[0-9]+"));
            if (publicGet || (HttpMethod.POST.matches(method) && path.equals("/api/support/chat"))) {
                setUserIfPresent(request);
                return true;
            }

            if (!setUserIfPresent(request)) {
                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                response.setContentType("application/json;charset=UTF-8");
                objectMapper.writeValue(response.getWriter(), Api.failure("未登录或登录已过期", HttpStatus.UNAUTHORIZED));
                return false;
            }
            if ((path.startsWith("/api/admin/") || path.startsWith("/api/support/admin/")) && !"ADMIN".equals(AuthContext.requireUser().role())) throw new Api.ApiException(HttpStatus.FORBIDDEN, "无权限");
            return true;
        }

        @Override
        public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
            AuthContext.clear();
        }

        private boolean setUserIfPresent(HttpServletRequest request) {
            String auth = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (auth == null || !auth.startsWith("Bearer ")) {
                return false;
            }
            String token = auth.substring("Bearer ".length()).trim();
            return tokenService.parse(token).map(u -> {
                AuthContext.set(u);
                return true;
            }).orElse(false);
        }
    }
}
