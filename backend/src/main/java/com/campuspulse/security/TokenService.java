package com.campuspulse.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

@Component
public class TokenService {
    private static final String HMAC_ALG = "HmacSHA256";
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final byte[] secret;
    private final long ttlSeconds;

    public TokenService(ObjectMapper objectMapper,
                        JdbcTemplate jdbcTemplate,
                        @Value("${app.security.token-secret}") String tokenSecret,
                        @Value("${app.security.token-ttl-seconds:7200}") long ttlSeconds) {
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.secret = tokenSecret.getBytes(StandardCharsets.UTF_8);
        this.ttlSeconds = ttlSeconds;
    }

    public String issueToken(long userId, String username, String role) {
        Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
        long exp = Instant.now().getEpochSecond() + ttlSeconds;
        Long version = jdbcTemplate.queryForObject("SELECT token_version FROM users WHERE id = ?", Long.class, userId);
        if (version == null) throw new IllegalStateException("User no longer exists");
        Map<String, Object> payload = Map.of("sub", username, "uid", userId, "role", role, "exp", exp, "ver", version);
        String headerB64 = base64UrlJson(header);
        String payloadB64 = base64UrlJson(payload);
        String signingInput = headerB64 + "." + payloadB64;
        String sig = hmacB64(signingInput);
        return signingInput + "." + sig;
    }

    public Optional<AuthContext.User> parse(String token) {
        try {
            if (token == null || token.length() > 4096) return Optional.empty();
            String[] parts = token.split("\\.");
            if (parts.length != 3) return Optional.empty();
            String signingInput = parts[0] + "." + parts[1];
            String sigExpected = hmacB64(signingInput);
            if (!constantTimeEquals(sigExpected, parts[2])) return Optional.empty();
            byte[] payloadJson = Base64.getUrlDecoder().decode(parts[1]);
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = objectMapper.readValue(payloadJson, Map.class);
            long exp = ((Number) payload.getOrDefault("exp", 0)).longValue();
            if (Instant.now().getEpochSecond() >= exp) return Optional.empty();
            long uid = ((Number) payload.getOrDefault("uid", 0)).longValue();
            String sub = String.valueOf(payload.getOrDefault("sub", ""));
            if (uid <= 0 || sub.isBlank()) return Optional.empty();
            var users = jdbcTemplate.queryForList("SELECT username, role, status, token_version FROM users WHERE id = ? LIMIT 1", uid);
            if (users.isEmpty() || ((Number) users.get(0).get("status")).intValue() != 1) return Optional.empty();
            if (!(payload.get("ver") instanceof Number version)
                    || version.longValue() != ((Number) users.get(0).get("token_version")).longValue()) return Optional.empty();
            String username = String.valueOf(users.get(0).get("username"));
            if (!username.equals(sub)) return Optional.empty();
            String role = String.valueOf(users.get(0).get("role"));
            return Optional.of(new AuthContext.User(uid, username, role, version.longValue(), exp));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    public void revokeAll(long userId) {
        jdbcTemplate.update("UPDATE users SET token_version = token_version + 1 WHERE id = ?", userId);
    }

    public boolean isCurrent(AuthContext.User user) {
        if (user.expiresAt() <= Instant.now().getEpochSecond()) return false;
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE id = ? AND status = 1 AND token_version = ?", Long.class, user.id(), user.tokenVersion());
        return count != null && count == 1;
    }

    private String base64UrlJson(Map<String, Object> obj) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(obj);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(json);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String hmacB64(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALG);
            mac.init(new SecretKeySpec(secret, HMAC_ALG));
            byte[] sig = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(sig);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
