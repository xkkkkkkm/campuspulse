package com.campuspulse.tag;

import com.campuspulse.common.Api;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tags")
public class TagController {
    private final JdbcTemplate jdbcTemplate;

    public TagController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public Api.ApiResponse<List<TagItem>> list() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT id, name, type
                FROM tags
                WHERE type IN ('HOME', 'OTHER')
                ORDER BY FIELD(type, 'HOME', 'OTHER'),
                         CASE
                           WHEN type = 'HOME' AND name = '其他' THEN 2
                           WHEN type = 'HOME' THEN 1
                           ELSE 3
                         END,
                         name
                LIMIT 200
                """);
        List<TagItem> list = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            list.add(new TagItem(((Number) r.get("id")).longValue(), String.valueOf(r.get("name")), String.valueOf(r.get("type"))));
        }
        return Api.ok(list);
    }

    public record TagItem(long id, String name, String type) {
    }
}
