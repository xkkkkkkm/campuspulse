package com.campuspulse.team;

import com.campuspulse.common.Api;
import com.campuspulse.activity.ActivityRepository;
import com.campuspulse.security.AuthContext;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.sql.Timestamp;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.*;

@org.springframework.stereotype.Service
public class TeamService {
    private final JdbcTemplate jdbcTemplate;
    private final TeamRepository repository;
    private final ActivityRepository activities;

    public TeamService(JdbcTemplate jdbcTemplate, TeamRepository repository, ActivityRepository activities) {
        this.repository = repository;
        this.activities = activities;
        this.jdbcTemplate = jdbcTemplate;
    }

    public Api.ApiResponse<List<TeamCard>> list(Long activityId,
            String keyword, String tag, int page,
            int size) {
        return Api.ok(page(activityId, keyword, tag, page, size).data().items());
    }

    public Api.ApiResponse<TeamPage> page(Long activityId,
            String keyword, String tag, int page,
            int size) {
        var viewer = AuthContext.userOrNull();
        int p = Math.min(Math.max(page, 1), 1000000), limit = Math.min(Math.max(size, 1), 50);
        String where = " WHERE t.status='OPEN' AND (COALESCE(t.end_time,t.start_time) IS NULL OR COALESCE(t.end_time,t.start_time)>CURRENT_TIMESTAMP) AND (t.activity_id IS NULL OR (a.status='PUBLISHED' AND a.audit_status='APPROVED' AND a.teaming_enabled=1 AND COALESCE(a.end_time,a.start_time)>CURRENT_TIMESTAMP))";
        List<Object> args = new ArrayList<>();
        if (activityId != null) { where += " AND t.activity_id=?"; args.add(activityId); }
        if (keyword != null && !keyword.isBlank()) {
            if (keyword.length()>200) throw new Api.ApiException(HttpStatus.BAD_REQUEST, "关键词过长");
            var translated = com.campuspulse.content.ContentSearch.teams(keyword);
            var activityTitles = com.campuspulse.content.ContentSearch.activityTitles(keyword);
            where += " AND (t.title LIKE ? OR t.description LIKE ? OR a.title LIKE ? OR " + translated.sql() + " OR " + activityTitles.sql() + ")";
            for (int i=0;i<3;i++) args.add("%"+keyword.trim()+"%");
            args.addAll(translated.parameters());
            args.addAll(activityTitles.parameters());
        }
        if (tag != null && !tag.isBlank()) {
            where += " AND (EXISTS(SELECT 1 FROM team_tag tt JOIN tags tg ON tg.id=tt.tag_id WHERE tt.team_id=t.id AND tg.name=?) OR EXISTS(SELECT 1 FROM activity_tag at2 JOIN tags tg ON tg.id=at2.tag_id WHERE at2.activity_id=t.activity_id AND tg.name=?))";
            String canonicalTag = com.campuspulse.content.ContentVocabulary.canonicalTag(tag);
            args.add(canonicalTag); args.add(canonicalTag);
        }
        long total = Optional.ofNullable(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM teams t LEFT JOIN activities a ON a.id=t.activity_id"+where, Long.class, args.toArray())).orElse(0L);
        List<Object> queryArgs = new ArrayList<>();
        queryArgs.add(viewer == null ? 0L : viewer.id()); queryArgs.add(viewer == null ? 0L : viewer.id());
        queryArgs.addAll(args); queryArgs.add(limit); queryArgs.add((p-1)*limit);
        var rows = jdbcTemplate.queryForList("""
                SELECT t.*, u.nickname AS creator_name, a.title AS activity_title,
                    (SELECT COUNT(*) FROM team_member tm WHERE tm.team_id=t.id AND tm.status='ACTIVE') AS member_count,
                    EXISTS(SELECT 1 FROM team_member tm WHERE tm.team_id=t.id AND tm.user_id=? AND tm.status='ACTIVE') AS joined,
                    EXISTS(SELECT 1 FROM team_join_request tr WHERE tr.team_id=t.id AND tr.user_id=? AND tr.status='PENDING') AS pending
                FROM teams t JOIN users u ON u.id=t.creator_id LEFT JOIN activities a ON a.id=t.activity_id
                """ + where + " ORDER BY t.created_at DESC,t.id DESC LIMIT ? OFFSET ?", queryArgs.toArray());
        List<TeamCard> items = rows.stream().map(r -> new TeamCard(
            ((Number)r.get("id")).longValue(), r.get("activity_id") == null ? null : ((Number)r.get("activity_id")).longValue(),
            String.valueOf(r.get("title")), r.get("description") == null ? "" : String.valueOf(r.get("description")),
            ((Number)r.get("creator_id")).longValue(), String.valueOf(r.get("creator_name")), (String)r.get("activity_title"),
            ((Number)r.get("member_count")).intValue(), ((Number)r.get("max_members")).intValue(),
            toLocalDateTimeString(r.get("start_time")), toLocalDateTimeString(r.get("end_time")),
            ((Number)r.get("joined")).intValue()>0, ((Number)r.get("joined")).intValue()==0 && ((Number)r.get("pending")).intValue()>0
        )).toList();
        return Api.ok(new TeamPage(items,total,p,limit));
    }

    public record TeamPage(List<TeamCard> items, long total, int page, int size) {}

    public Api.ApiResponse<TeamDetail> detail(long id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT t.id, t.activity_id, a.title AS activity_title,
                       t.title, t.description, t.max_members, t.status, t.start_time, t.end_time, t.version,
                       u.nickname AS creator_name, u.id AS creator_id
                FROM teams t
                JOIN users u ON u.id = t.creator_id
                LEFT JOIN activities a ON a.id = t.activity_id
                WHERE t.id = ?
                LIMIT 1
                """, id);
        if (rows.isEmpty()) {
            throw new Api.ApiException(HttpStatus.NOT_FOUND, "队伍不存在");
        }
        Map<String, Object> t = rows.get(0);
        if ("ARCHIVED".equals(t.get("status"))) {
            var viewer=AuthContext.userOrNull();
            if(viewer==null || (!"ADMIN".equals(viewer.role()) && ((Number)t.get("creator_id")).longValue()!=viewer.id() && jdbcTemplate.queryForObject("SELECT COUNT(*) FROM team_member WHERE team_id=? AND user_id=?",Integer.class,id,viewer.id())==0)) throw new Api.ApiException(HttpStatus.NOT_FOUND,"队伍不存在");
        }
        List<Member> members = jdbcTemplate.queryForList("""
                SELECT u.id AS user_id, u.nickname, tm.role
                FROM team_member tm
                JOIN users u ON u.id = tm.user_id
                WHERE tm.team_id = ? AND tm.status = 'ACTIVE'
                ORDER BY tm.joined_at ASC
                """, id).stream().map(r -> new Member(
                ((Number) r.get("user_id")).longValue(),
                String.valueOf(r.get("nickname")),
                String.valueOf(r.get("role"))
        )).toList();
        long creatorId = ((Number) t.get("creator_id")).longValue();
        PublicUserProfile creatorProfile = fetchPublicUserProfile(creatorId);

        return Api.ok(new TeamDetail(
                ((Number) t.get("id")).longValue(),
                t.get("activity_id") == null ? null : ((Number) t.get("activity_id")).longValue(),
                t.get("activity_title") == null ? null : String.valueOf(t.get("activity_title")),
                String.valueOf(t.get("title")),
                t.get("description") == null ? "" : String.valueOf(t.get("description")),
                String.valueOf(t.get("status")),
                ((Number) t.get("max_members")).intValue(),
                toLocalDateTimeString(t.get("start_time")),
                toLocalDateTimeString(t.get("end_time")),
                creatorId,
                String.valueOf(t.get("creator_name")),
                members,
                creatorProfile, ((Number)t.get("version")).longValue()
        ));
    }

    @Transactional
    public Api.ApiResponse<Map<String, Object>> create(CreateTeamRequest req) {
        AuthContext.User p = requireLogin();
        validateCreate(req);
        if (req.activityId() != null) {
            var activity = activities.lock(req.activityId());
            ActivityRepository.requireLive(activity);
            if (((Number)activity.get("teaming_enabled")).intValue() == 0) throw new Api.ApiException(HttpStatus.CONFLICT, "活动尚未开放组队");
        }
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var ps = connection.prepareStatement("""
                    INSERT INTO teams (activity_id, title, description, start_time, end_time, creator_id, max_members, status)
                    VALUES (?, ?, ?, ?, ?, ?, ?, 'OPEN')
                    """, Statement.RETURN_GENERATED_KEYS);
            if (req.activityId() == null) ps.setObject(1, null);
            else ps.setLong(1, req.activityId());
            ps.setString(2, req.title());
            ps.setString(3, req.description());
            ps.setTimestamp(4, req.startTime() == null ? null : Timestamp.valueOf(req.startTime()));
            ps.setTimestamp(5, req.endTime() == null ? null : Timestamp.valueOf(req.endTime()));
            ps.setLong(6, p.id());
            ps.setInt(7, req.maxMembers() == null || req.maxMembers() <= 0 ? 0 : req.maxMembers());
            return ps;
        }, keyHolder);
        Long teamId = keyHolder.getKey() == null ? null : keyHolder.getKey().longValue();
        if (teamId == null) {
            throw new Api.ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "创建失败");
        }
        jdbcTemplate.update("INSERT IGNORE INTO team_member (team_id, user_id, role, status) VALUES (?, ?, 'CREATOR', 'ACTIVE')", teamId, p.id());
        syncTags(teamId,req.tags());
        return Api.ok(Map.of("id", teamId));
    }

    @Transactional
    public Api.ApiResponse<Void> update(long id, UpdateTeamRequest req) {
        AuthContext.User p = requireLogin();
        validateUpdate(req);
        var previous = teamForManage(id, p);
        repository.requireLive(previous);
        if (req.version()!=null && req.version().longValue()!=((Number)previous.get("version")).longValue()) throw new Api.ApiException(HttpStatus.CONFLICT,"队伍已更新，请刷新后重试");
        int activeMembers = repository.activeCount(id);
        int maxMembers = req.maxMembers() == null || req.maxMembers() <= 0 ? 0 : req.maxMembers();
        if (maxMembers > 0 && maxMembers < activeMembers) {
            throw new Api.ApiException(HttpStatus.BAD_REQUEST, "队伍人数上限不能低于当前成员数");
        }
        jdbcTemplate.update("""
                UPDATE teams
                SET title = ?, description = ?, start_time = ?, end_time = ?, max_members = ?, version = version + 1
                WHERE id = ?
                """,
                req.title().trim(),
                trimToNull(req.description()),
                req.startTime() == null ? null : Timestamp.valueOf(req.startTime()),
                req.endTime() == null ? null : Timestamp.valueOf(req.endTime()),
                maxMembers,
                id
        );
        if (req.tags()!=null) syncTags(id,req.tags());
        return Api.ok();
    }

    @Transactional
    public Api.ApiResponse<Void> delete(long id) {
        AuthContext.User p = requireLogin();
        Map<String, Object> team = teamForManage(id, p);
        if ("ARCHIVED".equals(team.get("status"))) return Api.ok();
        jdbcTemplate.update("""
                INSERT INTO notifications (user_id, type, title, content)
                SELECT DISTINCT tm.user_id, 'TEAM_CANCELLED', '队伍已删除', ?
                FROM team_member tm
                WHERE tm.team_id = ? AND tm.user_id <> ?
                """, "你参与的队伍《" + String.valueOf(team.get("title")) + "》已被队长删除。", id, p.id());
        jdbcTemplate.update("UPDATE teams SET status = 'ARCHIVED', archived_at = CURRENT_TIMESTAMP, version = version + 1 WHERE id = ?", id);
        jdbcTemplate.update("UPDATE team_join_request SET status = 'CANCELLED' WHERE team_id = ? AND status = 'PENDING'", id);
        return Api.ok();
    }

    @Transactional
    public Api.ApiResponse<Void> requestJoin(long id, JoinRequest req) {
        AuthContext.User p = requireLogin();
        var team = repository.lock(id);
        repository.requireLive(team);
        if (req != null && req.message() != null && req.message().length() > 1000) throw new Api.ApiException(HttpStatus.BAD_REQUEST, "申请说明不能超过 1000 字");
        int max = ((Number)team.get("max_members")).intValue();
        if (max > 0 && repository.activeCount(id) >= max) throw new Api.ApiException(HttpStatus.CONFLICT, "队伍已满员");
        Long already = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM team_member WHERE team_id = ? AND user_id = ? AND status = 'ACTIVE'", Long.class, id, p.id());
        if (already != null && already > 0) {
            throw new Api.ApiException(HttpStatus.CONFLICT, "您已在队伍中");
        }
        List<Map<String, Object>> existingRows = jdbcTemplate.queryForList("""
                SELECT id, status
                FROM team_join_request
                WHERE team_id = ? AND user_id = ?
                LIMIT 1
                """, id, p.id());
        if (!existingRows.isEmpty()) {
            Map<String, Object> existing = existingRows.get(0);
            String existingStatus = String.valueOf(existing.get("status"));
            if ("PENDING".equals(existingStatus)) {
                return Api.ok();
            }
            if ("APPROVED".equals(existingStatus)) {
                throw new Api.ApiException(HttpStatus.CONFLICT, "您已在队伍中");
            }
            jdbcTemplate.update("""
                    UPDATE team_join_request
                    SET message = ?, status = 'PENDING', created_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """, req == null ? null : trimToNull(req.message()), ((Number) existing.get("id")).longValue());
        } else {
            jdbcTemplate.update("""
                    INSERT INTO team_join_request (team_id, user_id, message, status)
                    VALUES (?, ?, ?, 'PENDING')
                    """, id, p.id(), req == null ? null : trimToNull(req.message()));
        }
        jdbcTemplate.update("""
                INSERT INTO user_behavior_log (user_id, target_type, target_id, event_type, scene)
                VALUES (?, 'TEAM', ?, 'TEAM_APPLY', 'team_detail')
                """, p.id(), id);
        Long creatorId = jdbcTemplate.queryForObject("SELECT creator_id FROM teams WHERE id = ? LIMIT 1", Long.class, id);
        if (creatorId != null) {
            String applicantName = jdbcTemplate.queryForObject("SELECT nickname FROM users WHERE id = ? LIMIT 1", String.class, p.id());
            jdbcTemplate.update("""
                    INSERT INTO notifications (user_id, type, title, content)
                    VALUES (?, 'TEAM_JOIN_REQUEST', '新的入队申请', ?)
                    """, creatorId, (applicantName == null || applicantName.isBlank() ? "有新申请" : applicantName) + " 申请加入你的队伍，点击查看并处理。");
        }
        return Api.ok();
    }

    @Transactional
    public Api.ApiResponse<Void> cancelJoinRequest(long id) {
        AuthContext.User p = requireLogin();
        repository.lock(id);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT tjr.id, t.title, t.creator_id, u.nickname AS applicant_name
                FROM team_join_request tjr
                JOIN teams t ON t.id = tjr.team_id
                JOIN users u ON u.id = tjr.user_id
                WHERE tjr.team_id = ?
                  AND tjr.user_id = ?
                  AND tjr.status = 'PENDING'
                LIMIT 1
                """, id, p.id());
        if (rows.isEmpty()) {
            throw new Api.ApiException(HttpStatus.NOT_FOUND, "未找到可取消的入队申请");
        }
        Map<String, Object> row = rows.get(0);
        int changed = jdbcTemplate.update("UPDATE team_join_request SET status = 'CANCELLED' WHERE id = ? AND status = 'PENDING'", ((Number) row.get("id")).longValue());
        if (changed != 1) throw new Api.ApiException(HttpStatus.CONFLICT, "申请状态已变化");
        repository.changed(id);
        jdbcTemplate.update("""
                INSERT INTO notifications (user_id, type, title, content)
                VALUES (?, 'TEAM_JOIN_CANCELLED', '入队申请已取消', ?)
                """,
                ((Number) row.get("creator_id")).longValue(),
                String.valueOf(row.get("applicant_name")) + " 取消了加入队伍《" + String.valueOf(row.get("title")) + "》的申请。"
        );
        return Api.ok();
    }

    public Api.ApiResponse<List<JoinRequestItem>> listRequests(long id) {
        AuthContext.User p = requireLogin();
        var creators=jdbcTemplate.queryForList("SELECT creator_id FROM teams WHERE id=?",Long.class,id);
        Long creatorId=creators.isEmpty()?null:creators.get(0);
        if (creatorId == null) {
            throw new Api.ApiException(HttpStatus.NOT_FOUND, "队伍不存在");
        }
        if (!Objects.equals(creatorId, p.id()) && !"ADMIN".equals(p.role())) {
            throw new Api.ApiException(HttpStatus.FORBIDDEN, "无权限查看申请列表");
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT r.id, r.user_id, u.nickname, u.avatar_url, u.student_no, u.college, u.major, u.education_level,
                       r.message, r.status, r.created_at
                FROM team_join_request r
                JOIN users u ON u.id = r.user_id
                WHERE r.team_id = ?
                ORDER BY r.created_at DESC
                """, id);
        List<JoinRequestItem> list = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            list.add(new JoinRequestItem(
                    ((Number) r.get("id")).longValue(),
                    ((Number) r.get("user_id")).longValue(),
                    String.valueOf(r.get("nickname")),
                    r.get("avatar_url") == null ? null : String.valueOf(r.get("avatar_url")),
                    r.get("student_no") == null ? null : String.valueOf(r.get("student_no")),
                    r.get("college") == null ? null : String.valueOf(r.get("college")),
                    r.get("major") == null ? null : String.valueOf(r.get("major")),
                    r.get("education_level") == null ? null : String.valueOf(r.get("education_level")),
                    r.get("message") == null ? "" : String.valueOf(r.get("message")),
                    String.valueOf(r.get("status")),
                    String.valueOf(r.get("created_at"))
            ));
        }
        return Api.ok(list);
    }

    @Transactional
    public Api.ApiResponse<Void> approve(long teamId, long requestId) {
        AuthContext.User p = requireLogin();
        var team = teamForManage(teamId, p);
        repository.requireLive(team);
        Map<String, Object> req = repository.request(teamId, requestId);
        String currentStatus = String.valueOf(req.get("status"));
        if ("APPROVED".equals(currentStatus)) {
            return Api.ok();
        }
        if (!"PENDING".equals(currentStatus)) {
            throw new Api.ApiException(HttpStatus.CONFLICT, "当前申请状态不可改为通过");
        }
        int members = repository.activeCount(teamId);
        int max = ((Number)team.get("max_members")).intValue();
        if (max > 0 && members >= max) {
            throw new Api.ApiException(HttpStatus.CONFLICT, "队伍已满员");
        }
        long userId = ((Number) req.get("user_id")).longValue();
        int changed = jdbcTemplate.update("UPDATE team_join_request SET status = 'APPROVED' WHERE id = ? AND status = 'PENDING'", requestId);
        if (changed != 1) throw new Api.ApiException(HttpStatus.CONFLICT, "申请状态已变化");
        repository.changed(teamId);
        Long memberExists = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM team_member WHERE team_id = ? AND user_id = ?", Long.class, teamId, userId);
        if (memberExists != null && memberExists > 0) {
            jdbcTemplate.update("UPDATE team_member SET status = 'ACTIVE' WHERE team_id = ? AND user_id = ?", teamId, userId);
        } else {
            jdbcTemplate.update("INSERT IGNORE INTO team_member (team_id, user_id, role, status) VALUES (?, ?, 'MEMBER', 'ACTIVE')", teamId, userId);
        }
        jdbcTemplate.update("""
                INSERT INTO user_behavior_log (user_id, target_type, target_id, event_type, scene)
                VALUES (?, 'TEAM', ?, 'TEAM_JOIN', 'team_manage')
                """, userId, teamId);
        jdbcTemplate.update("""
                INSERT INTO notifications (user_id, type, title, content)
                VALUES (?, 'TEAM_JOIN_APPROVED', '入队申请已通过', ?)
                """, userId, "你申请加入的队伍已通过，快去和队友交流吧。");
        return Api.ok();
    }

    @Transactional
    public Api.ApiResponse<Void> reject(long teamId, long requestId) {
        AuthContext.User p = requireLogin();
        var team = teamForManage(teamId, p);
        Map<String, Object> req = repository.request(teamId, requestId);
        String currentStatus = String.valueOf(req.get("status"));
        if ("REJECTED".equals(currentStatus)) {
            return Api.ok();
        }
        if (!"PENDING".equals(currentStatus) && !"APPROVED".equals(currentStatus)) {
            throw new Api.ApiException(HttpStatus.CONFLICT, "当前申请状态不可改为拒绝");
        }
        long userId = ((Number) req.get("user_id")).longValue();
        if (((Number)team.get("creator_id")).longValue() == userId) throw new Api.ApiException(HttpStatus.CONFLICT, "请先转让队长身份");
        int changed = jdbcTemplate.update("UPDATE team_join_request SET status = 'REJECTED' WHERE id = ? AND status = ?", requestId, currentStatus);
        if (changed != 1) throw new Api.ApiException(HttpStatus.CONFLICT, "申请状态已变化");
        repository.changed(teamId);
        jdbcTemplate.update("UPDATE team_member SET status = 'REMOVED' WHERE team_id = ? AND user_id = ?", teamId, userId);
        jdbcTemplate.update("""
                INSERT INTO notifications (user_id, type, title, content)
                VALUES (?, 'TEAM_JOIN_REJECTED', '入队申请未通过', ?)
                """, userId, "你的入队申请暂未通过，可以尝试联系队长或加入其他队伍。");
        return Api.ok();
    }

    @Transactional
    public Api.ApiResponse<Void> leave(long id) {
        var user = requireLogin();
        var team = repository.lock(id);
        if (((Number)team.get("creator_id")).longValue() == user.id()) throw new Api.ApiException(HttpStatus.CONFLICT, "队长请先转让身份或关闭队伍");
        int changed = jdbcTemplate.update("UPDATE team_member SET status = 'LEFT' WHERE team_id = ? AND user_id = ? AND status = 'ACTIVE'", id, user.id());
        if (changed == 0) return Api.ok();
        jdbcTemplate.update("UPDATE team_join_request SET status = 'CANCELLED' WHERE team_id = ? AND user_id = ? AND status IN ('PENDING','APPROVED')", id, user.id());
        repository.changed(id);
        jdbcTemplate.update("INSERT INTO notifications (user_id,type,title,content) VALUES (?, 'TEAM_LEFT', '队员已退出', ?)", team.get("creator_id"), "用户 " + user.id() + " 已退出队伍《" + team.get("title") + "》。");
        return Api.ok();
    }

    @Transactional
    public Api.ApiResponse<Void> transfer(long id, TransferRequest req) {
        var user = requireLogin();
        var team = teamForManage(id, user);
        repository.requireLive(team);
        if (req == null || req.userId() == null) throw new Api.ApiException(HttpStatus.BAD_REQUEST, "请选择新队长");
        long previous = ((Number)team.get("creator_id")).longValue();
        if (req.userId() == previous) return Api.ok();
        int changed = jdbcTemplate.update("UPDATE team_member SET role = 'CREATOR' WHERE team_id = ? AND user_id = ? AND status = 'ACTIVE'", id, req.userId());
        if (changed != 1) throw new Api.ApiException(HttpStatus.CONFLICT, "新队长必须为有效队员");
        jdbcTemplate.update("UPDATE team_member SET role = 'MEMBER' WHERE team_id = ? AND user_id = ?", id, previous);
        jdbcTemplate.update("UPDATE teams SET creator_id = ?, version = version + 1 WHERE id = ?", req.userId(), id);
        jdbcTemplate.update("INSERT INTO notifications (user_id,type,title,content) VALUES (?, 'TEAM_TRANSFER', '你已成为队长', ?)", req.userId(), "队伍《" + team.get("title") + "》的队长已转让给你。");
        return Api.ok();
    }

    @Transactional
    public Api.ApiResponse<Void> close(long id) {
        var user = requireLogin();
        var team = teamForManage(id, user);
        if (!"OPEN".equals(team.get("status"))) return Api.ok();
        jdbcTemplate.update("UPDATE teams SET status = 'CLOSED', version = version + 1 WHERE id = ?", id);
        jdbcTemplate.update("UPDATE team_join_request SET status = 'CANCELLED' WHERE team_id = ? AND status = 'PENDING'", id);
        jdbcTemplate.update("INSERT INTO notifications (user_id,type,title,content) SELECT user_id,'TEAM_CLOSED','队伍已关闭',? FROM team_member WHERE team_id = ? AND status = 'ACTIVE'", "队伍《" + team.get("title") + "》已关闭，历史记录已保留。", id);
        return Api.ok();
    }

    @Transactional
    public Api.ApiResponse<Void> removeMember(long id, long userId) {
        var user = requireLogin();
        var team = teamForManage(id, user);
        if (((Number)team.get("creator_id")).longValue() == userId) throw new Api.ApiException(HttpStatus.CONFLICT, "请先转让队长身份");
        int changed = jdbcTemplate.update("UPDATE team_member SET status = 'REMOVED' WHERE team_id = ? AND user_id = ? AND status = 'ACTIVE'", id, userId);
        if (changed == 0) return Api.ok();
        jdbcTemplate.update("UPDATE team_join_request SET status = 'REJECTED' WHERE team_id = ? AND user_id = ? AND status IN ('PENDING','APPROVED')", id, userId);
        repository.changed(id);
        jdbcTemplate.update("INSERT INTO notifications (user_id,type,title,content) VALUES (?, 'TEAM_REMOVED','已被移出队伍',?)", userId, "你已被移出队伍《" + team.get("title") + "》。");
        return Api.ok();
    }

    public record TransferRequest(Long userId) {}

    private void syncTags(long teamId,List<String> names) {
        if(names==null) return;
        if(names.size()>20) throw new Api.ApiException(HttpStatus.BAD_REQUEST,"标签不能超过 20 个");
        jdbcTemplate.update("DELETE FROM team_tag WHERE team_id=?",teamId);
        for(String name:new LinkedHashSet<>(names)) {
            if(name==null||name.isBlank()) throw new Api.ApiException(HttpStatus.BAD_REQUEST,"标签不合法");
            var ids=jdbcTemplate.queryForList("SELECT id FROM tags WHERE name=?",Long.class,name.trim());
            if(ids.isEmpty()) throw new Api.ApiException(HttpStatus.BAD_REQUEST,"标签不存在");
            jdbcTemplate.update("INSERT INTO team_tag (team_id,tag_id) VALUES (?,?)",teamId,ids.get(0));
        }
    }

    private AuthContext.User requireLogin() {
        return AuthContext.requireUser();
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    @com.campuspulse.content.ContentEntity("TEAM")
    public record TeamCard(long id, Long activityId, String title, String description, long creatorId, String creatorName, String activityTitle, int members, int maxMembers,
                           String startTime,
                           String endTime,
                           boolean joined, boolean pending) {
    }

    @com.campuspulse.content.ContentEntity("TEAM")
    public record TeamDetail(long id, Long activityId, String activityTitle, String title, String description, String status, int maxMembers,
                             String startTime,
                             String endTime,
                             long creatorId, String creatorName, List<Member> members,
                             PublicUserProfile creatorProfile, long version) {
    }

    public record Member(long userId, String nickname, String role) {
    }

    public record PublicUserProfile(long userId, String nickname, String avatarUrl, String college, String campus,
                                    String major, String educationLevel, String grade, String bio) {
    }

    public record CreateTeamRequest(Long activityId, String title, String description, Integer maxMembers, LocalDateTime startTime, LocalDateTime endTime, List<String> tags) {
    }

    public record UpdateTeamRequest(String title, String description, Integer maxMembers, LocalDateTime startTime, LocalDateTime endTime, Long version, List<String> tags) {
    }

    public record JoinRequest(String message) {
    }

    public record JoinRequestItem(long id, long userId, String nickname, String avatarUrl, String studentNo,
                                  String college, String major, String educationLevel,
                                  String message, String status, String createdAt) {
    }

    private void validateCreate(CreateTeamRequest req) {
        if (req == null) throw new Api.ApiException(HttpStatus.BAD_REQUEST, "参数错误");
        if (req.title() == null || req.title().trim().isEmpty() || req.title().length() > 200) {
            throw new Api.ApiException(HttpStatus.BAD_REQUEST, "队伍名称不能为空且长度不超过 200");
        }
        if (req.description() != null && req.description().length() > 800) {
            throw new Api.ApiException(HttpStatus.BAD_REQUEST, "队伍简介长度不超过 800");
        }
        if (req.maxMembers() != null && req.maxMembers() == 1) {
            throw new Api.ApiException(HttpStatus.BAD_REQUEST, "队伍人数至少为 2");
        }
        if (req.maxMembers() != null && req.maxMembers() < 0) {
            throw new Api.ApiException(HttpStatus.BAD_REQUEST, "队伍人数不能为负数");
        }
        if (req.maxMembers() != null && req.maxMembers() > 50) {
            throw new Api.ApiException(HttpStatus.BAD_REQUEST, "队伍人数上限不能超过 50");
        }
        if (req.startTime() != null && req.endTime() != null && req.endTime().isBefore(req.startTime())) {
            throw new Api.ApiException(HttpStatus.BAD_REQUEST, "结束时间不能早于开始时间");
        }
    }

    private void validateUpdate(UpdateTeamRequest req) {
        if (req == null) throw new Api.ApiException(HttpStatus.BAD_REQUEST, "参数错误");
        if (req.title() == null || req.title().trim().isEmpty() || req.title().length() > 200) {
            throw new Api.ApiException(HttpStatus.BAD_REQUEST, "队伍名称不能为空且长度不超过 200");
        }
        if (req.description() != null && req.description().length() > 800) {
            throw new Api.ApiException(HttpStatus.BAD_REQUEST, "队伍简介长度不超过 800");
        }
        if (req.maxMembers() != null && req.maxMembers() == 1) {
            throw new Api.ApiException(HttpStatus.BAD_REQUEST, "队伍人数至少为 2");
        }
        if (req.maxMembers() != null && req.maxMembers() < 0) {
            throw new Api.ApiException(HttpStatus.BAD_REQUEST, "队伍人数不能为负数");
        }
        if (req.maxMembers() != null && req.maxMembers() > 50) {
            throw new Api.ApiException(HttpStatus.BAD_REQUEST, "队伍人数上限不能超过 50");
        }
        if (req.startTime() != null && req.endTime() != null && req.endTime().isBefore(req.startTime())) {
            throw new Api.ApiException(HttpStatus.BAD_REQUEST, "结束时间不能早于开始时间");
        }
    }

    private Map<String, Object> teamForManage(long teamId, AuthContext.User user) {
        Map<String,Object> team = repository.lock(teamId);
        long creatorId = ((Number) team.get("creator_id")).longValue();
        if (creatorId != user.id() && !"ADMIN".equals(user.role())) {
            throw new Api.ApiException(HttpStatus.FORBIDDEN, "无权限");
        }
        return team;
    }

    private static String toLocalDateTimeString(Object value) {
        if (value == null) return null;
        if (value instanceof Timestamp ts) return ts.toLocalDateTime().toString();
        if (value instanceof LocalDateTime ldt) return ldt.toString();
        return String.valueOf(value);
    }

    private static LocalDateTime toLocalDateTime(Object value) {
        if (value == null) return null;
        if (value instanceof LocalDateTime ldt) return ldt;
        if (value instanceof Timestamp ts) return ts.toLocalDateTime();
        if (value instanceof java.util.Date d) return new Timestamp(d.getTime()).toLocalDateTime();
        if (value instanceof String s) {
            String t = s.trim();
            if (t.contains(" ") && !t.contains("T")) {
                t = t.replace(" ", "T");
            }
            return LocalDateTime.parse(t);
        }
        throw new IllegalStateException("Unsupported datetime type: " + value.getClass().getName());
    }

    private PublicUserProfile fetchPublicUserProfile(long userId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT id, nickname, avatar_url, college, campus, major, education_level, grade, student_no, bio
                FROM users
                WHERE id = ?
                LIMIT 1
                """, userId);
        if (rows.isEmpty()) return null;
        Map<String, Object> user = rows.get(0);
        String grade = trimToNull(user.get("grade") == null ? null : String.valueOf(user.get("grade")));
        if (grade == null) {
            String studentNo = trimToNull(user.get("student_no") == null ? null : String.valueOf(user.get("student_no")));
            if (studentNo != null && studentNo.length() >= 4) {
                grade = studentNo.substring(0, 4);
            }
        }
        return new PublicUserProfile(
                ((Number) user.get("id")).longValue(),
                String.valueOf(user.get("nickname")),
                user.get("avatar_url") == null ? null : String.valueOf(user.get("avatar_url")),
                user.get("college") == null ? null : String.valueOf(user.get("college")),
                user.get("campus") == null ? null : String.valueOf(user.get("campus")),
                user.get("major") == null ? null : String.valueOf(user.get("major")),
                user.get("education_level") == null ? null : String.valueOf(user.get("education_level")),
                grade,
                user.get("bio") == null ? null : String.valueOf(user.get("bio"))
        );
    }
}
