package com.campuspulse.security;

import com.campuspulse.common.Api;
import org.springframework.http.HttpStatus;

import java.util.Set;

public final class AuthContext {
    private static final ThreadLocal<User> USER_HOLDER = new ThreadLocal<>();

    private AuthContext() {
    }

    public static void set(User user) {
        USER_HOLDER.set(user);
    }

    public static void clear() {
        USER_HOLDER.remove();
    }

    public static User userOrNull() {
        return USER_HOLDER.get();
    }

    public static User requireUser() {
        User u = USER_HOLDER.get();
        if (u == null) throw new Api.ApiException(HttpStatus.UNAUTHORIZED, "未登录或登录已过期");
        return u;
    }

    public static User requireRole(Set<String> roles) {
        User u = requireUser();
        if (!roles.contains(u.role())) throw new Api.ApiException(HttpStatus.FORBIDDEN, "无权限");
        return u;
    }

    public record User(long id, String username, String role, long tokenVersion, long expiresAt) {
        public User(long id, String username, String role) {
            this(id, username, role, 0, Long.MAX_VALUE);
        }
    }
}
