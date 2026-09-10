package com.campuspulse.team;

import com.campuspulse.activity.ActivityRepository;
import com.campuspulse.common.Api;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
public class TeamRepository {
    private final JdbcTemplate db;
    private final ActivityRepository activities;
    public TeamRepository(JdbcTemplate db, ActivityRepository activities) { this.db = db; this.activities = activities; }
    /** Team is always locked first; linked activity second. Activity mutations never lock existing teams. */
    public Map<String,Object> lock(long id) {
        var rows = db.queryForList("SELECT * FROM teams WHERE id = ? FOR UPDATE", id);
        if (rows.isEmpty()) throw new Api.ApiException(HttpStatus.NOT_FOUND, "队伍不存在");
        return rows.get(0);
    }
    public void requireLive(Map<String,Object> team) {
        if (!"OPEN".equals(team.get("status")) || ActivityRepository.ended(team)) throw new Api.ApiException(HttpStatus.CONFLICT, "队伍已关闭或结束");
        if (team.get("activity_id") != null) {
            var activity = activities.lock(((Number) team.get("activity_id")).longValue());
            ActivityRepository.requireLive(activity);
            if (((Number) activity.get("teaming_enabled")).intValue() == 0) throw new Api.ApiException(HttpStatus.CONFLICT, "活动尚未开放组队");
        }
    }
    public int activeCount(long id) {
        return db.queryForList("SELECT user_id FROM team_member WHERE team_id = ? AND status = 'ACTIVE' FOR UPDATE", Long.class, id).size();
    }
    public void changed(long id) { db.update("UPDATE teams SET version = version + 1 WHERE id = ?", id); }
    public Map<String,Object> request(long teamId, long requestId) {
        var rows = db.queryForList("SELECT * FROM team_join_request WHERE team_id = ? AND id = ? FOR UPDATE", teamId, requestId);
        if (rows.isEmpty()) throw new Api.ApiException(HttpStatus.NOT_FOUND, "入队申请不存在");
        return rows.get(0);
    }
}
