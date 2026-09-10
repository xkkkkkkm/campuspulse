package com.campuspulse.user;

import com.campuspulse.common.Api;
import com.campuspulse.security.AuthContext;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final JdbcTemplate jdbcTemplate;

    public UserController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/{id}/brief")
    public Api.ApiResponse<UserBrief> brief(@PathVariable long id) {
        AuthContext.requireUser();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT id, nickname, avatar_url
                FROM users
                WHERE id = ?
                LIMIT 1
                """, id);
        if (rows.isEmpty()) throw new Api.ApiException(HttpStatus.NOT_FOUND, "用户不存在");
        Map<String, Object> u = rows.get(0);
        return Api.ok(new UserBrief(
                ((Number) u.get("id")).longValue(),
                String.valueOf(u.get("nickname")),
                u.get("avatar_url") == null ? null : String.valueOf(u.get("avatar_url"))
        ));
    }

    @GetMapping("/search")
    public Api.ApiResponse<List<UserSearchItem>> search(@RequestParam(required = false) String q,
                                                        @RequestParam(defaultValue = "12") int size) {
        AuthContext.User p = AuthContext.requireUser();
        int limit = Math.min(Math.max(size, 1), 30);
        String keyword = q == null ? "" : q.trim();
        if (keyword.length() > 100) throw new Api.ApiException(HttpStatus.BAD_REQUEST, "关键词过长");
        List<Map<String,Object>> rows = keyword.isBlank() ? List.of() : jdbcTemplate.queryForList("""
                SELECT id,nickname,avatar_url,college FROM users WHERE id<>? AND status=1
                AND (nickname LIKE ? OR college LIKE ?) ORDER BY CASE WHEN nickname=? THEN 0 ELSE 1 END,id DESC LIMIT ?
                """,p.id(),"%"+keyword+"%","%"+keyword+"%",keyword,limit);
        List<UserSearchItem> list = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            list.add(new UserSearchItem(
                    ((Number) row.get("id")).longValue(),
                    String.valueOf(row.get("nickname")),
                    row.get("avatar_url") == null ? null : String.valueOf(row.get("avatar_url")),
                    null,
                    null,
                    row.get("college") == null ? null : String.valueOf(row.get("college"))
            ));
        }
        return Api.ok(list);
    }

    public record UserBrief(long id, String nickname, String avatarUrl) {
    }

    public record UserSearchItem(long id, String nickname, String avatarUrl, String username, String studentNo, String college) {
    }
}
