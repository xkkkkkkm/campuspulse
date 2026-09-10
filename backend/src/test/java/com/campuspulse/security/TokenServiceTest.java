package com.campuspulse.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class TokenServiceTest {
    static class UserDatabase extends JdbcTemplate {
        long version;
        int status = 1;
        String role = "USER";
        @Override public <T> T queryForObject(String sql, Class<T> type, Object... args) { return type.cast(version); }
        @Override public List<Map<String,Object>> queryForList(String sql, Object... args) {
            return List.of(Map.of("username", "alice", "role", role, "status", status, "token_version", version));
        }
        @Override public int update(String sql, Object... args) { version++; return 1; }
    }
    @Test void roleStatusRevocationAndTamperingAreEnforced() {
        UserDatabase jdbc = new UserDatabase();
        TokenService service = new TokenService(new ObjectMapper(), jdbc, "unique-random-secret-for-unit-tests-only", 300);
        String token = service.issueToken(7L, "alice", "USER");
        assertEquals("alice", service.parse(token).orElseThrow().username());
        jdbc.role = "ADMIN";
        assertEquals("ADMIN", service.parse(token).orElseThrow().role());
        jdbc.status = 0;
        assertTrue(service.parse(token).isEmpty());
        jdbc.status = 2;
        assertTrue(service.parse(token).isEmpty());
        jdbc.status = 1;
        assertTrue(service.parse(token.substring(0, token.length()-2) + "xx").isEmpty());
        service.revokeAll(7);
        assertTrue(service.parse(token).isEmpty());
        assertTrue(service.parse(service.issueToken(7L,"alice","ADMIN")).isPresent());
    }
    @Test void expiredTokenAndMalformedTokenCannotAuthenticate() {
        UserDatabase jdbc = new UserDatabase();
        TokenService service = new TokenService(new ObjectMapper(), jdbc, "unique-random-secret-for-unit-tests-only", -1);
        assertTrue(service.parse(service.issueToken(7L,"alice","USER")).isEmpty());
        assertTrue(service.parse("a.b.c").isEmpty());
        assertTrue(service.parse(null).isEmpty());
    }
}
