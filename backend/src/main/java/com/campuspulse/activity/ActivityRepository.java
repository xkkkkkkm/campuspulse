package com.campuspulse.activity;

import com.campuspulse.common.Api;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

@Repository
public class ActivityRepository {
    private final JdbcTemplate db;
    public ActivityRepository(JdbcTemplate db) { this.db = db; }

    /** Must be the first database read in every activity mutation transaction. */
    public Map<String,Object> lock(long id) {
        var rows = db.queryForList("SELECT * FROM activities WHERE id = ? FOR UPDATE", id);
        if (rows.isEmpty()) throw new Api.ApiException(HttpStatus.NOT_FOUND, "活动不存在");
        return rows.get(0);
    }
    public int approvedCount(long id) {
        return db.queryForList("SELECT id FROM registrations WHERE activity_id = ? AND status = 'APPROVED' FOR UPDATE", Long.class, id).size();
    }
    public void changed(long id) { db.update("UPDATE activities SET version = version + 1 WHERE id = ?", id); }
    public static void requireLive(Map<String,Object> row) {
        if (!"PUBLISHED".equals(row.get("status")) || !"APPROVED".equals(row.get("audit_status")) || ended(row))
            throw new Api.ApiException(HttpStatus.CONFLICT, "活动已结束、已归档或尚未通过审核");
    }
    public static boolean ended(Map<String,Object> row) {
        Object value = row.get("end_time") == null ? row.get("start_time") : row.get("end_time");
        if (value == null) return false;
        LocalDateTime end = value instanceof Timestamp ts ? ts.toLocalDateTime() : value instanceof LocalDateTime dt ? dt : LocalDateTime.parse(value.toString().replace(' ', 'T'));
        return !end.isAfter(LocalDateTime.now());
    }
    public static void requireEditable(Map<String,Object> row) {
        if ("ARCHIVED".equals(row.get("status")) || ended(row)) throw new Api.ApiException(HttpStatus.CONFLICT, "已归档或结束的活动不可修改");
    }
    public Map<Long,List<String>> tags(Collection<Long> ids) {
        Map<Long,List<String>> result = new HashMap<>();
        if (ids.isEmpty()) return result;
        String marks = String.join(",", Collections.nCopies(ids.size(), "?"));
        for (var row : db.queryForList("SELECT at2.activity_id, t.name FROM activity_tag at2 JOIN tags t ON t.id = at2.tag_id WHERE at2.activity_id IN ("+marks+") ORDER BY t.name", ids.toArray()))
            result.computeIfAbsent(((Number)row.get("activity_id")).longValue(), k -> new ArrayList<>()).add(String.valueOf(row.get("name")));
        return result;
    }
}
