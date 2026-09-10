package com.campuspulse.activity;

import com.campuspulse.common.Api;
import com.campuspulse.upload.UploadService;
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
public class ActivityService {
    private final JdbcTemplate jdbcTemplate;
    private final ActivityRepository repository;
    private final UploadService uploads;

    public ActivityService(JdbcTemplate jdbcTemplate, ActivityRepository repository, UploadService uploads) {
        this.uploads=uploads;
        this.repository = repository;
        this.jdbcTemplate = jdbcTemplate;
    }

    public Api.ApiResponse<PageResult<ActivityCard>> list(
            String keyword,
            String tag,
            String category,
            String status,
            String sort,
            int page,
            int size
    ) {
        int p = Math.min(Math.max(page, 1), 1000000);
        int s = Math.min(Math.max(size, 1), 50);
        int offset = (p - 1) * s;

        String kw = keyword == null ? null : keyword.trim();
        String tg = com.campuspulse.content.ContentVocabulary.canonicalTag(tag == null ? category : tag);
        if (kw != null && kw.length() > 200) throw new Api.ApiException(HttpStatus.BAD_REQUEST, "关键词过长");

        String where = """
                WHERE a.status = 'PUBLISHED' AND a.audit_status = 'APPROVED'
                """;
        List<Object> args = new ArrayList<>();
        if (kw != null && !kw.isBlank()) {
            var translated = com.campuspulse.content.ContentSearch.activities(kw);
            where += " AND (a.title LIKE ? OR a.location LIKE ? OR a.description LIKE ? OR " + translated.sql() + ")";
            String like = "%" + kw + "%";
            args.add(like);
            args.add(like);
            args.add(like);
            args.addAll(translated.parameters());
        }
        if (tg != null && !tg.isBlank()) {
            where += " AND EXISTS (SELECT 1 FROM activity_tag at2 JOIN tags t2 ON t2.id = at2.tag_id WHERE at2.activity_id = a.id AND t2.name = ?)";
            args.add(tg);
        }

        if ("ended".equals(status)) where += " AND COALESCE(a.end_time, a.start_time) <= CURRENT_TIMESTAMP";
        else if ("ongoing".equals(status)) where += " AND a.start_time <= CURRENT_TIMESTAMP AND a.end_time > CURRENT_TIMESTAMP";
        else if (!"all".equals(status)) where += " AND COALESCE(a.end_time, a.start_time) > CURRENT_TIMESTAMP";
        String ordering = "latest".equals(sort) ? "a.created_at DESC, a.id DESC" : "popular".equals(sort) ? "registered_count DESC, a.id DESC" : "a.start_time ASC, a.id DESC";
        long total = Optional.ofNullable(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM activities a " + where, Long.class, args.toArray()))
                .orElse(0L);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT a.id, a.title, a.location, a.start_time, a.cover_url, a.max_participants,
                       (SELECT COUNT(*) FROM registrations r WHERE r.activity_id = a.id AND r.status = 'APPROVED') AS registered_count
                FROM activities a
                %s
                ORDER BY %s
                LIMIT ? OFFSET ?
                """.formatted(where, ordering), concatArgs(args, s, offset));

        Map<Long,List<String>> tagMap = repository.tags(rows.stream().map(r -> ((Number)r.get("id")).longValue()).toList());
        List<ActivityCard> items = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            long id = ((Number) r.get("id")).longValue();
            int registered = ((Number)r.get("registered_count")).intValue();
            List<String> tags = tagMap.getOrDefault(id, List.of());
            items.add(new ActivityCard(
                    id,
                    String.valueOf(r.get("title")),
                    String.valueOf(r.get("location")),
                    Objects.requireNonNull(toLocalDateTime(r.get("start_time"))).toString(),
                    r.get("cover_url") == null ? null : String.valueOf(r.get("cover_url")),
                    registered,
                    ((Number) r.get("max_participants")).intValue(),
                    tags
            ));
        }

        return Api.ok(new PageResult<>(items, total, p, s));
    }

    public Api.ApiResponse<List<ActivityCard>> highlights(int size) {
        int s = Math.min(Math.max(size, 1), 50);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT a.id, a.title, a.location, a.start_time, a.cover_url, a.max_participants,
                       (SELECT COUNT(*) FROM registrations r WHERE r.activity_id = a.id AND r.status = 'APPROVED') AS registered_count
                FROM activities a
                WHERE a.status = 'PUBLISHED' AND a.audit_status = 'APPROVED' AND COALESCE(a.end_time, a.start_time) > CURRENT_TIMESTAMP
                ORDER BY a.created_at DESC
                LIMIT ?
                """, s);
        Map<Long,List<String>> tagMap = repository.tags(rows.stream().map(r -> ((Number)r.get("id")).longValue()).toList());
        List<ActivityCard> items = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            long id = ((Number) r.get("id")).longValue();
            int registered = ((Number)r.get("registered_count")).intValue();
            List<String> tags = tagMap.getOrDefault(id, List.of());
            items.add(new ActivityCard(
                    id,
                    String.valueOf(r.get("title")),
                    String.valueOf(r.get("location")),
                    Objects.requireNonNull(toLocalDateTime(r.get("start_time"))).toString(),
                    r.get("cover_url") == null ? null : String.valueOf(r.get("cover_url")),
                    registered,
                    ((Number) r.get("max_participants")).intValue(),
                    tags
            ));
        }
        return Api.ok(items);
    }

    public Api.ApiResponse<ActivityDetail> detail(long id) {
        AuthContext.User viewer = userOrNull();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT a.*, u.nickname AS organizer_name
                FROM activities a
                JOIN users u ON u.id = a.organizer_id
                WHERE a.id = ?
                LIMIT 1
                """, id);
        if (rows.isEmpty()) {
            throw new Api.ApiException(HttpStatus.NOT_FOUND, "活动不存在");
        }
        Map<String, Object> a = rows.get(0);
        String auditStatus = String.valueOf(a.get("audit_status"));
        long organizerId = ((Number) a.get("organizer_id")).longValue();
        boolean canPreviewPending = viewer != null
                && (Objects.equals(viewer.id(), organizerId) || "ADMIN".equals(viewer.role()));
        if ((!"APPROVED".equals(auditStatus) || !"PUBLISHED".equals(a.get("status"))) && !canPreviewPending) {
            throw new Api.ApiException(HttpStatus.NOT_FOUND, "活动不存在");
        }
        int registered = approvedRegistrationCount(id);
        int pending = pendingRegistrationCount(id);
        List<String> tags = jdbcTemplate.queryForList("""
                SELECT t.name FROM activity_tag at2
                JOIN tags t ON t.id = at2.tag_id
                WHERE at2.activity_id = ?
                ORDER BY t.name
                """, String.class, id);
        boolean favorited = false;
        if (viewer != null) {
            Long fav = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM favorites WHERE user_id = ? AND activity_id = ?", Long.class, viewer.id(), id);
            favorited = fav != null && fav > 0;
        }
        return Api.ok(new ActivityDetail(
                ((Number) a.get("id")).longValue(),
                String.valueOf(a.get("title")),
                a.get("description") == null ? "" : String.valueOf(a.get("description")),
                String.valueOf(a.get("location")),
                Objects.requireNonNull(toLocalDateTime(a.get("start_time"))).toString(),
                a.get("end_time") == null ? null : Objects.requireNonNull(toLocalDateTime(a.get("end_time"))).toString(),
                a.get("cover_url") == null ? null : String.valueOf(a.get("cover_url")),
                ((Number) a.get("max_participants")).intValue(),
                registered,
                pending,
                organizerId,
                String.valueOf(a.get("organizer_name")),
                tags,
                favorited,
                auditStatus,
                a.get("chat_enabled") != null && ((Number) a.get("chat_enabled")).intValue() == 1,
                a.get("teaming_enabled") == null || ((Number) a.get("teaming_enabled")).intValue() == 1,
                String.valueOf(a.get("status")), ((Number)a.get("version")).longValue()
        ));
    }

    @Transactional
    public Api.ApiResponse<Map<String, Object>> create(UpsertActivityRequest req) {
        AuthContext.User p = requireLogin();
        validateUpsert(req);
        uploads.requireOwned(p.id(),req.coverUrl(),"covers");
        String coverUrl = emptyToNull(req.coverUrl());
        String auditStatus = "PENDING";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var ps = connection.prepareStatement("""
                    INSERT INTO activities (title, description, location, start_time, end_time, organizer_id, cover_url, max_participants, status, audit_status, teaming_enabled)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PUBLISHED', ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, req.title());
            ps.setString(2, emptyToNull(req.description()));
            ps.setString(3, req.location());
            ps.setTimestamp(4, Timestamp.valueOf(req.startTime()));
            ps.setTimestamp(5, req.endTime() == null ? null : Timestamp.valueOf(req.endTime()));
            ps.setLong(6, p.id());
            ps.setString(7, coverUrl);
            ps.setInt(8, req.maxParticipants());
            ps.setString(9, auditStatus);
            ps.setInt(10, req.teamingEnabled() == null || req.teamingEnabled() ? 1 : 0);
            return ps;
        }, keyHolder);
        Long id = keyHolder.getKey() == null ? null : keyHolder.getKey().longValue();
        if (id == null) {
            throw new Api.ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "创建失败");
        }
        syncActivityTags(id, req.tags());
        return Api.ok(Map.of("id", id, "auditStatus", auditStatus));
    }

    @Transactional
    public Api.ApiResponse<Void> update(long id, UpsertActivityRequest req) {
        AuthContext.User p = requireLogin();
        validateUpsert(req);
        var previous = ownedActivityForManage(id, p);
        ActivityRepository.requireEditable(previous);
        if (req.version() != null && req.version().longValue()!=((Number)previous.get("version")).longValue()) throw new Api.ApiException(HttpStatus.CONFLICT,"活动已更新，请刷新后重试");
        if (!Objects.equals(req.coverUrl(),previous.get("cover_url"))) uploads.requireOwned(p.id(),req.coverUrl(),"covers");
        int approved = repository.approvedCount(id);
        int maxParticipants = Optional.ofNullable(req.maxParticipants()).orElse(0);
        if (maxParticipants > 0 && maxParticipants < approved) {
            throw new Api.ApiException(HttpStatus.BAD_REQUEST, "人数上限不能低于当前已通过人数");
        }
        String auditStatus = "PENDING";
        jdbcTemplate.update("""
                UPDATE activities
                SET title = ?, description = ?, location = ?, start_time = ?, end_time = ?, cover_url = ?, max_participants = ?, audit_status = ?, teaming_enabled = ?, version = version + 1
                WHERE id = ?
                """,
                req.title(),
                emptyToNull(req.description()),
                req.location(),
                Timestamp.valueOf(req.startTime()),
                req.endTime() == null ? null : Timestamp.valueOf(req.endTime()),
                emptyToNull(req.coverUrl()),
                maxParticipants,
                auditStatus,
                req.teamingEnabled() == null || req.teamingEnabled() ? 1 : 0,
                id
        );
        syncActivityTags(id, req.tags());
        return Api.ok();
    }

    @Transactional
    public Api.ApiResponse<Void> delete(long id) {
        AuthContext.User p = requireLogin();
        Map<String, Object> activity = ownedActivityForManage(id, p);
        if ("ARCHIVED".equals(activity.get("status"))) return Api.ok();
        jdbcTemplate.update("""
                INSERT INTO notifications (user_id, type, title, content)
                SELECT DISTINCT r.user_id, 'ACTIVITY_CANCELLED', '活动已删除', ?
                FROM registrations r
                WHERE r.activity_id = ?
                """, "你报名过的《" + String.valueOf(activity.get("title")) + "》已被发布者删除。", id);
        jdbcTemplate.update("UPDATE activities SET status = 'ARCHIVED', archived_at = CURRENT_TIMESTAMP, chat_enabled = 0, version = version + 1 WHERE id = ?", id);
        return Api.ok();
    }

    @Transactional
    public Api.ApiResponse<Map<String, Object>> toggleFavorite(long id, Boolean favorited) {
        AuthContext.User p = requireLogin();
        var activity=repository.lock(id);
        Long exists = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM favorites WHERE user_id = ? AND activity_id = ?", Long.class, p.id(), id);
        boolean previous = exists != null && exists > 0;
        if (favorited != null && favorited == previous) return Api.ok(Map.of("favorited", previous));
        boolean now = false;
        if (exists != null && exists > 0) {
            jdbcTemplate.update("DELETE FROM favorites WHERE user_id = ? AND activity_id = ?", p.id(), id);
        } else {
            ActivityRepository.requireLive(activity);
            jdbcTemplate.update("INSERT IGNORE INTO favorites (user_id, activity_id) VALUES (?, ?)", p.id(), id);
            now = true;
        }
        jdbcTemplate.update("INSERT INTO event_log (user_id, activity_id, event_type) VALUES (?, ?, ?)", p.id(), id, now ? "FAVORITE" : "UNFAVORITE");
        jdbcTemplate.update("""
                INSERT INTO user_behavior_log (user_id, target_type, target_id, event_type, scene)
                VALUES (?, 'ACTIVITY', ?, ?, 'activity_detail')
                """, p.id(), id, now ? "FAVORITE" : "UNFAVORITE");
        return Api.ok(Map.of("favorited", now));
    }

    @Transactional
    public Api.ApiResponse<Void> register(long id, RegisterActivityRequest req) {
        AuthContext.User p = requireLogin();
        validateRegister(req);
        Map<String,Object> activity = repository.lock(id);
        ActivityRepository.requireLive(activity);
        int max = ((Number)activity.get("max_participants")).intValue();
        long organizerId = ((Number)activity.get("organizer_id")).longValue();
        String actTitle = String.valueOf(activity.get("title"));
        int registered = repository.approvedCount(id);
        if (max > 0 && registered >= max) {
            throw new Api.ApiException(HttpStatus.CONFLICT, "名额已满");
        }
        List<Map<String, Object>> existingRows = jdbcTemplate.queryForList("""
                SELECT id, status
                FROM registrations
                WHERE activity_id = ? AND user_id = ?
                LIMIT 1
                """, id, p.id());
        if (!existingRows.isEmpty()) {
            Map<String, Object> existing = existingRows.get(0);
            String existingStatus = String.valueOf(existing.get("status"));
            if ("APPLIED".equals(existingStatus) || "APPROVED".equals(existingStatus)) {
                throw new Api.ApiException(HttpStatus.CONFLICT, "您已报名该活动");
            }
            jdbcTemplate.update("""
                    UPDATE registrations
                    SET real_name = ?, phone = ?, college = ?, intro = ?, status = 'APPLIED', created_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """,
                    req.realName(),
                    req.phone(),
                    req.college(),
                    emptyToNull(req.intro()),
                    ((Number) existing.get("id")).longValue()
            );
        } else {
            jdbcTemplate.update("""
                    INSERT INTO registrations (activity_id, user_id, real_name, phone, college, intro, status)
                    VALUES (?, ?, ?, ?, ?, ?, 'APPLIED')
                    """,
                    id,
                    p.id(),
                    req.realName(),
                    req.phone(),
                    req.college(),
                    emptyToNull(req.intro())
            );
        }
        jdbcTemplate.update("INSERT INTO event_log (user_id, activity_id, event_type) VALUES (?, ?, 'REGISTER')", p.id(), id);
        jdbcTemplate.update("""
                INSERT INTO user_behavior_log (user_id, target_type, target_id, event_type, scene)
                VALUES (?, 'ACTIVITY', ?, 'REGISTER', 'activity_detail')
                """, p.id(), id);
        Map<String, Object> me = jdbcTemplate.queryForMap("SELECT nickname FROM users WHERE id = ? LIMIT 1", p.id());
        String nick = String.valueOf(me.get("nickname"));
        jdbcTemplate.update("""
                INSERT INTO notifications (user_id, type, title, content)
                VALUES (?, 'ACTIVITY_REGISTER', '有新报名申请', ?)
                """, organizerId, nick + " 申请报名《" + actTitle + "》，请及时审批。");
        return Api.ok();
    }

    @Transactional
    public Api.ApiResponse<Void> cancelRegistration(long id) {
        AuthContext.User p = requireLogin();
        repository.lock(id);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT r.id, a.title, a.organizer_id
                FROM registrations r
                JOIN activities a ON a.id = r.activity_id
                WHERE r.activity_id = ? AND r.user_id = ? AND r.status IN ('APPLIED','APPROVED')
                LIMIT 1
                """, id, p.id());
        if (rows.isEmpty()) {
            throw new Api.ApiException(HttpStatus.NOT_FOUND, "未找到可取消的报名记录");
        }
        Map<String, Object> row = rows.get(0);
        jdbcTemplate.update("UPDATE registrations SET status = 'CANCELLED' WHERE id = ? AND status IN ('APPLIED','APPROVED')", ((Number) row.get("id")).longValue());
        repository.changed(id);
        jdbcTemplate.update("INSERT INTO user_behavior_log (user_id,target_type,target_id,event_type,scene) VALUES (?, 'ACTIVITY', ?, 'CANCEL_REGISTER', 'activity_detail')", p.id(), id);
        Map<String, Object> me = jdbcTemplate.queryForMap("SELECT nickname FROM users WHERE id = ? LIMIT 1", p.id());
        String nick = String.valueOf(me.get("nickname"));
        jdbcTemplate.update("""
                INSERT INTO notifications (user_id, type, title, content)
                VALUES (?, 'ACTIVITY_REGISTER_CANCELLED', '报名已取消', ?)
                """,
                ((Number) row.get("organizer_id")).longValue(),
                nick + " 取消了《" + String.valueOf(row.get("title")) + "》的报名。");
        return Api.ok();
    }

    public Api.ApiResponse<MyRegistration> myRegistration(long id) {
        AuthContext.User p = requireLogin();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT id, status, created_at
                FROM registrations
                WHERE activity_id = ? AND user_id = ?
                LIMIT 1
                """, id, p.id());
        if (rows.isEmpty()) return Api.ok(new MyRegistration(false, null, null, null));
        Map<String, Object> r = rows.get(0);
        return Api.ok(new MyRegistration(
                true,
                String.valueOf(r.get("status")),
                String.valueOf(r.get("created_at")),
                ((Number) r.get("id")).longValue()
        ));
    }

    public Api.ApiResponse<OrganizerContact> organizerContact(long id) {
        AuthContext.User p = requireLogin();
        List<Map<String,Object>> contacts = jdbcTemplate.queryForList("""
                SELECT a.organizer_id, u.nickname AS organizer_name, u.phone, u.phone_verified
                FROM activities a
                JOIN users u ON u.id = a.organizer_id
                WHERE a.id = ? AND a.audit_status = 'APPROVED'
                LIMIT 1
                """, id);
        if (contacts.isEmpty()) throw new Api.ApiException(HttpStatus.NOT_FOUND,"活动不存在");
        Map<String,Object> a = contacts.get(0);
        long organizerId = ((Number) a.get("organizer_id")).longValue();
        boolean isOrganizer = organizerId == p.id();
        Long reg = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM registrations WHERE activity_id = ? AND user_id = ? AND status IN ('APPLIED', 'APPROVED')", Long.class, id, p.id());
        boolean isRegistered = reg != null && reg > 0;
        if (!isOrganizer && !isRegistered && !"ADMIN".equals(p.role())) {
            throw new Api.ApiException(HttpStatus.FORBIDDEN, "报名后可查看发布人联系方式");
        }
        boolean verified = a.get("phone_verified") != null && ((Number) a.get("phone_verified")).intValue() == 1;
        String phone = a.get("phone") == null ? null : String.valueOf(a.get("phone"));
        return Api.ok(new OrganizerContact(
                organizerId,
                String.valueOf(a.get("organizer_name")),
                phone,
                verified
        ));
    }

    public Api.ApiResponse<List<RegistrationViewItem>> registrationsForOrganizer(long id) {return registrationsForOrganizer(id,1,100);}

    public Api.ApiResponse<List<RegistrationViewItem>> registrationsForOrganizer(long id,int page,int size) {
        int limit=Math.min(Math.max(size,1),100), offset=(Math.min(Math.max(page,1),1000000)-1)*limit;
        AuthContext.User p = requireLogin();
        var owners = jdbcTemplate.queryForList("SELECT organizer_id FROM activities WHERE id = ? LIMIT 1", Long.class, id);
        Long owner = owners.isEmpty() ? null : owners.get(0);
        if (owner == null) throw new Api.ApiException(HttpStatus.NOT_FOUND, "活动不存在");
        if (!Objects.equals(owner, p.id()) && !"ADMIN".equals(p.role())) {
            throw new Api.ApiException(HttpStatus.FORBIDDEN, "无权限查看报名信息");
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT r.id, r.user_id, u.nickname, u.avatar_url, u.student_no, u.major, u.education_level, u.campus, u.bio,
                       r.real_name, r.phone, r.college, r.intro, r.status, r.created_at
                FROM registrations r
                JOIN users u ON u.id = r.user_id
                WHERE r.activity_id = ?
                ORDER BY r.created_at DESC, r.id DESC
                LIMIT ? OFFSET ?
                """, id,limit,offset);
        List<RegistrationViewItem> list = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            list.add(new RegistrationViewItem(
                    ((Number) r.get("id")).longValue(),
                    ((Number) r.get("user_id")).longValue(),
                    String.valueOf(r.get("nickname")),
                    r.get("avatar_url") == null ? null : String.valueOf(r.get("avatar_url")),
                    r.get("student_no") == null ? null : String.valueOf(r.get("student_no")),
                    r.get("major") == null ? null : String.valueOf(r.get("major")),
                    r.get("education_level") == null ? null : String.valueOf(r.get("education_level")),
                    r.get("campus") == null ? null : String.valueOf(r.get("campus")),
                    r.get("bio") == null ? null : String.valueOf(r.get("bio")),
                    String.valueOf(r.get("real_name")),
                    String.valueOf(r.get("phone")),
                    String.valueOf(r.get("college")),
                    r.get("intro") == null ? null : String.valueOf(r.get("intro")),
                    String.valueOf(r.get("status")),
                    String.valueOf(r.get("created_at"))
            ));
        }
        return Api.ok(list);
    }

    @Transactional
    public Api.ApiResponse<Void> approveRegistration(long activityId, long registrationId) {
        AuthContext.User p = requireLogin();
        Map<String, Object> activity = ownedActivityForManage(activityId, p);
        ActivityRepository.requireLive(activity);
        Map<String, Object> registration = registrationForManage(activityId, registrationId);
        String currentStatus = String.valueOf(registration.get("status"));
        if ("APPROVED".equals(currentStatus)) {
            return Api.ok();
        }
        if (!"APPLIED".equals(currentStatus)) {
            throw new Api.ApiException(HttpStatus.CONFLICT, "当前报名状态不可改为通过");
        }
        int max = ((Number) activity.get("max_participants")).intValue();
        int approved = repository.approvedCount(activityId);
        if (max > 0 && approved >= max) {
            throw new Api.ApiException(HttpStatus.CONFLICT, "活动名额已满");
        }
        int updated = jdbcTemplate.update("UPDATE registrations SET status = 'APPROVED' WHERE id = ? AND status = 'APPLIED'", registrationId);
        if (updated != 1) throw new Api.ApiException(HttpStatus.CONFLICT, "报名状态已变化");
        repository.changed(activityId);
        jdbcTemplate.update("""
                INSERT INTO notifications (user_id, type, title, content)
                VALUES (?, 'ACTIVITY_REGISTER_APPROVED', '活动报名已通过', ?)
                """,
                ((Number) registration.get("user_id")).longValue(),
                "你报名的《" + String.valueOf(activity.get("title")) + "》已通过，快去查看活动详情。");
        return Api.ok();
    }

    @Transactional
    public Api.ApiResponse<Void> rejectRegistration(long activityId, long registrationId) {
        AuthContext.User p = requireLogin();
        Map<String, Object> activity = ownedActivityForManage(activityId, p);
        Map<String, Object> registration = registrationForManage(activityId, registrationId);
        String currentStatus = String.valueOf(registration.get("status"));
        if ("REJECTED".equals(currentStatus)) {
            return Api.ok();
        }
        if (!"APPLIED".equals(currentStatus) && !"APPROVED".equals(currentStatus)) {
            throw new Api.ApiException(HttpStatus.CONFLICT, "当前报名状态不可改为拒绝");
        }
        int updated = jdbcTemplate.update("UPDATE registrations SET status = 'REJECTED' WHERE id = ? AND status = ?", registrationId, currentStatus);
        if (updated != 1) throw new Api.ApiException(HttpStatus.CONFLICT, "报名状态已变化");
        repository.changed(activityId);
        String title = String.valueOf(activity.get("title"));
        String content = "APPLIED".equals(currentStatus)
                ? "你报名的《" + title + "》暂未通过，可以调整信息后重新申请。"
                : "你已被移出《" + title + "》，报名资格已撤销，活动群聊资格也会同步失效。";
        jdbcTemplate.update("""
                INSERT INTO notifications (user_id, type, title, content)
                VALUES (?, 'ACTIVITY_REGISTER_REJECTED', '活动报名未通过', ?)
                """,
                ((Number) registration.get("user_id")).longValue(),
                content);
        return Api.ok();
    }

    public Api.ApiResponse<List<TeamBrief>> activityTeams(long id) {
        AuthContext.User viewer = userOrNull();
        var flags = jdbcTemplate.queryForList("SELECT teaming_enabled FROM activities WHERE id=?",Integer.class,id);
        if(flags.isEmpty()) throw new Api.ApiException(HttpStatus.NOT_FOUND,"活动不存在");
        Integer teamingEnabled=flags.get(0);
        if (teamingEnabled != null && teamingEnabled == 0) {
            return Api.ok(List.of());
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT t.id, t.title, t.description, t.max_members, u.id AS creator_id, u.nickname AS creator_name,
                       EXISTS(SELECT 1 FROM team_member tm WHERE tm.team_id=t.id AND tm.user_id=? AND tm.status='ACTIVE') AS joined,
                       EXISTS(SELECT 1 FROM team_join_request tr WHERE tr.team_id=t.id AND tr.user_id=? AND tr.status='PENDING') AS pending,
                       (SELECT COUNT(*) FROM team_member tm WHERE tm.team_id = t.id AND tm.status = 'ACTIVE') AS member_count
                FROM teams t
                JOIN users u ON u.id = t.creator_id
                WHERE t.activity_id = ? AND t.status = 'OPEN' AND (COALESCE(t.end_time,t.start_time) IS NULL OR COALESCE(t.end_time,t.start_time) > CURRENT_TIMESTAMP) AND EXISTS(SELECT 1 FROM activities a WHERE a.id=t.activity_id AND a.status='PUBLISHED' AND a.audit_status='APPROVED' AND COALESCE(a.end_time,a.start_time)>CURRENT_TIMESTAMP)
                ORDER BY t.created_at DESC
                """, viewer == null ? 0L : viewer.id(), viewer == null ? 0L : viewer.id(), id);
        List<TeamBrief> list = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            long teamId = ((Number) r.get("id")).longValue();
            boolean joined = ((Number)r.get("joined")).intValue() > 0;
            boolean pending = !joined && ((Number)r.get("pending")).intValue() > 0;
            list.add(new TeamBrief(
                    teamId,
                    String.valueOf(r.get("title")),
                    r.get("description") == null ? "" : String.valueOf(r.get("description")),
                    ((Number) r.get("creator_id")).longValue(),
                    String.valueOf(r.get("creator_name")),
                    ((Number) r.get("member_count")).intValue(),
                    ((Number) r.get("max_members")).intValue(),
                    joined,
                    pending
            ));
        }
        return Api.ok(list);
    }

    private void syncActivityTags(long activityId, List<String> tags) {
        jdbcTemplate.update("DELETE FROM activity_tag WHERE activity_id = ?", activityId);
        if (tags == null || tags.isEmpty()) {
            return;
        }
        for (String tagName : tags) {
            if (tagName == null || tagName.isBlank()) continue;
            String t = tagName.trim();
            var tagIds = jdbcTemplate.queryForList("SELECT id FROM tags WHERE name = ? LIMIT 1", Long.class, t);
            Long tagId = tagIds.isEmpty() ? null : tagIds.get(0);
            if (tagId == null) {
                throw new Api.ApiException(HttpStatus.BAD_REQUEST, "标签不存在：" + t);
            }
            jdbcTemplate.update("INSERT IGNORE INTO activity_tag (activity_id, tag_id) VALUES (?, ?)", activityId, tagId);
        }
    }

    private int approvedRegistrationCount(long activityId) {
        return Optional.ofNullable(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM registrations
                WHERE activity_id = ? AND status = 'APPROVED'
                """, Integer.class, activityId)).orElse(0);
    }

    private int pendingRegistrationCount(long activityId) {
        return Optional.ofNullable(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM registrations
                WHERE activity_id = ? AND status = 'APPLIED'
                """, Integer.class, activityId)).orElse(0);
    }

    private Map<String, Object> ownedActivityForManage(long activityId, AuthContext.User user) {
        Map<String,Object> activity = repository.lock(activityId);
        long organizerId = ((Number) activity.get("organizer_id")).longValue();
        if (organizerId != user.id() && !"ADMIN".equals(user.role())) {
            throw new Api.ApiException(HttpStatus.FORBIDDEN, "无权限管理该活动");
        }
        return activity;
    }

    private Map<String, Object> registrationForManage(long activityId, long registrationId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT id, user_id, status
                FROM registrations
                WHERE id = ? AND activity_id = ?
                LIMIT 1 FOR UPDATE
                """, registrationId, activityId);
        if (rows.isEmpty()) {
            throw new Api.ApiException(HttpStatus.NOT_FOUND, "报名记录不存在");
        }
        return rows.get(0);
    }

    private AuthContext.User requireLogin() {
        return AuthContext.requireUser();
    }

    private AuthContext.User requireRole(Set<String> roles) {
        return AuthContext.requireRole(roles);
    }

    private AuthContext.User userOrNull() {
        return AuthContext.userOrNull();
    }

    private static Object[] concatArgs(List<Object> args, Object... tail) {
        Object[] arr = new Object[args.size() + tail.length];
        for (int i = 0; i < args.size(); i++) arr[i] = args.get(i);
        System.arraycopy(tail, 0, arr, args.size(), tail.length);
        return arr;
    }

    private static String emptyToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
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

    @com.campuspulse.content.ContentEntity("ACTIVITY")
    public record ActivityCard(long id, String title, String location, String startTime, String coverUrl,
                               int participants, int maxParticipants, List<String> tags) {
    }

    @com.campuspulse.content.ContentEntity("ACTIVITY")
    public record ActivityDetail(long id, String title, String description, String location, String startTime, String endTime,
                                 String coverUrl, int maxParticipants, int participants, int pendingRegistrations,
                                 long organizerId, String organizerName, List<String> tags, boolean favorited,
                                 String auditStatus, boolean chatEnabled, boolean teamingEnabled, String status, long version) {
    }

    public record PageResult<T>(List<T> items, long total, int page, int size) {
    }

    @com.campuspulse.content.ContentEntity("TEAM")
    public record TeamBrief(long id, String title, String description, long creatorId, String creatorName, int members, int maxMembers,
                            boolean joined, boolean pending) {
    }

    public record UpsertActivityRequest(
            String title,
            String location,
            LocalDateTime startTime,
            LocalDateTime endTime,
            Integer maxParticipants,
            String coverUrl,
            String description,
            List<String> tags,
            Boolean teamingEnabled,
            Long version
    ) {
    }

    public record RegisterActivityRequest(
            String realName,
            String phone,
            String college,
            String intro
    ) {
    }

    public record MyRegistration(boolean registered, String status, String createdAt, Long registrationId) {
    }

    public record OrganizerContact(long organizerId, String organizerName, String phone, boolean phoneVerified) {
    }

    public record RegistrationViewItem(long id, long userId, String nickname, String avatarUrl,
                                       String studentNo, String major, String educationLevel, String campus, String bio,
                                       String realName, String phone, String college, String intro,
                                       String status, String createdAt) {
    }


    private void validateUpsert(UpsertActivityRequest req) {
        if (req == null) throw new Api.ApiException(HttpStatus.BAD_REQUEST, "参数错误");
        if (req.title() == null || req.title().trim().isEmpty() || req.title().length() > 200) {
            throw new Api.ApiException(HttpStatus.BAD_REQUEST, "标题不能为空且长度不超过 200");
        }
        if (req.location() == null || req.location().trim().isEmpty() || req.location().length() > 200) {
            throw new Api.ApiException(HttpStatus.BAD_REQUEST, "地点不能为空且长度不超过 200");
        }
        if (req.startTime() == null) throw new Api.ApiException(HttpStatus.BAD_REQUEST, "开始时间不能为空");
        if (req.endTime() != null && req.endTime().isBefore(req.startTime())) {
            throw new Api.ApiException(HttpStatus.BAD_REQUEST, "结束时间不能早于开始时间");
        }
        if (req.maxParticipants() == null || req.maxParticipants() < 0 || req.maxParticipants() > 1000000) throw new Api.ApiException(HttpStatus.BAD_REQUEST, "最大人数不合法");
        if (req.description() != null && req.description().length() > 10000) throw new Api.ApiException(HttpStatus.BAD_REQUEST, "活动介绍过长");
        if (req.coverUrl() != null && req.coverUrl().length() > 512) throw new Api.ApiException(HttpStatus.BAD_REQUEST, "封面地址过长");
        if (req.tags() != null && req.tags().size() > 20) throw new Api.ApiException(HttpStatus.BAD_REQUEST, "标签不能超过 20 个");
        if (req.tags() == null || req.tags().isEmpty()) throw new Api.ApiException(HttpStatus.BAD_REQUEST, "活动标签至少选择 1 个");
    }

    private void validateRegister(RegisterActivityRequest req) {
        if (req == null) throw new Api.ApiException(HttpStatus.BAD_REQUEST, "参数错误");
        if (req.intro() != null && req.intro().length() > 512) throw new Api.ApiException(HttpStatus.BAD_REQUEST, "自我介绍长度不超过 512");
        if (req.realName() == null || req.realName().trim().isEmpty() || req.realName().length() > 64) {
            throw new Api.ApiException(HttpStatus.BAD_REQUEST, "真实姓名不能为空且长度不超过 64");
        }
        if (req.phone() == null || req.phone().trim().isEmpty() || req.phone().length() > 32) {
            throw new Api.ApiException(HttpStatus.BAD_REQUEST, "手机号不能为空且长度不超过 32");
        }
        if (req.college() == null || req.college().trim().isEmpty() || req.college().length() > 128) {
            throw new Api.ApiException(HttpStatus.BAD_REQUEST, "学院不能为空且长度不超过 128");
        }
    }

}
