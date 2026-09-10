package com.campuspulse.profile;

import com.campuspulse.common.Api;
import com.campuspulse.upload.UploadService;
import org.springframework.transaction.annotation.Transactional;
import com.campuspulse.security.AuthContext;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {
    private static final List<String> SUPPORTED_CAMPUSES = List.of("天赐庄校区", "独墅湖校区", "阳澄湖校区", "未来校区");
    private final JdbcTemplate jdbcTemplate;
    private final UploadService uploads;

    public ProfileController(JdbcTemplate jdbcTemplate, UploadService uploads) {
        this.uploads=uploads;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PutMapping("/me")
    public Api.ApiResponse<Void> updateMe(@RequestBody UpdateMeRequest req) {
        AuthContext.User p = requireLogin();
        if (req == null) throw new Api.ApiException(HttpStatus.BAD_REQUEST, "参数错误");
        String college = req.college() == null ? null : req.college().trim();
        String campus = req.campus() == null ? null : req.campus().trim();
        String major = req.major() == null ? null : req.major().trim();
        String educationLevel = req.educationLevel() == null ? null : req.educationLevel().trim();
        String bio = req.bio() == null ? null : req.bio().trim();

        if (college != null && college.length() > 128) {
            throw new Api.ApiException(HttpStatus.BAD_REQUEST, "学院长度不超过 128");
        }
        if (major != null && major.length() > 128) {
            throw new Api.ApiException(HttpStatus.BAD_REQUEST, "专业长度不超过 128");
        }
        if (campus != null && !campus.isBlank() && !SUPPORTED_CAMPUSES.contains(campus)) {
            throw new Api.ApiException(HttpStatus.BAD_REQUEST, "校区选项不支持");
        }
        if (bio != null && bio.length() > 300) {
            throw new Api.ApiException(HttpStatus.BAD_REQUEST, "个人简介长度不超过 300");
        }
        if (educationLevel != null && !educationLevel.isBlank()) {
            if (!List.of("UNDERGRAD", "MASTER", "PHD", "OTHER").contains(educationLevel)) {
                throw new Api.ApiException(HttpStatus.BAD_REQUEST, "学历选项不支持");
            }
        }

        jdbcTemplate.update("""
                UPDATE users
                SET college = ?,
                    campus = ?,
                    major = ?,
                    education_level = ?,
                    bio = ?
                WHERE id = ?
                """,
                emptyToNull(college),
                emptyToNull(campus),
                emptyToNull(major),
                emptyToNull(educationLevel),
                emptyToNull(bio),
                p.id()
        );
        return Api.ok();
    }

    public Api.ApiResponse<List<ActivityLite>> favorites() { return favorites(1,100); }

    @GetMapping("/favorites")
    public Api.ApiResponse<List<ActivityLite>> favorites(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "100") int size) {
        int limit = pageSize(size), offset = pageOffset(page, limit);
        AuthContext.User p = requireLogin();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT a.id, a.title, a.location, a.start_time, a.cover_url, a.organizer_id, u.nickname AS organizer_name
                FROM favorites f
                JOIN activities a ON a.id = f.activity_id
                JOIN users u ON u.id = a.organizer_id
                WHERE f.user_id = ?
                ORDER BY f.created_at DESC, a.id DESC
                LIMIT ? OFFSET ?
                """, p.id(), limit, offset);
        List<ActivityLite> list = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            list.add(new ActivityLite(
                    ((Number) r.get("id")).longValue(),
                    String.valueOf(r.get("title")),
                    String.valueOf(r.get("location")),
                    Objects.requireNonNull(toLocalDateTime(r.get("start_time"))).toString(),
                    r.get("cover_url") == null ? null : String.valueOf(r.get("cover_url")),
                    ((Number) r.get("organizer_id")).longValue(),
                    String.valueOf(r.get("organizer_name"))
            ));
        }
        return Api.ok(list);
    }

    public Api.ApiResponse<List<RegistrationItem>> registrations() { return registrations(1,100); }

    @GetMapping("/registrations")
    public Api.ApiResponse<List<RegistrationItem>> registrations(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "100") int size) {
        int limit = pageSize(size), offset = pageOffset(page, limit);
        AuthContext.User p = requireLogin();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT r.id, r.status, r.created_at,
                       a.id AS activity_id, a.title, a.location, a.start_time, a.organizer_id, u.nickname AS organizer_name
                FROM registrations r
                JOIN activities a ON a.id = r.activity_id
                JOIN users u ON u.id = a.organizer_id
                WHERE r.user_id = ?
                  AND r.status IN ('APPLIED','APPROVED')
                ORDER BY r.created_at DESC, r.id DESC
                LIMIT ? OFFSET ?
                """, p.id(), limit, offset);
        List<RegistrationItem> list = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            list.add(new RegistrationItem(
                    ((Number) r.get("id")).longValue(),
                    String.valueOf(r.get("status")),
                    String.valueOf(r.get("created_at")),
                    new ActivityLite(
                            ((Number) r.get("activity_id")).longValue(),
                            String.valueOf(r.get("title")),
                            String.valueOf(r.get("location")),
                            Objects.requireNonNull(toLocalDateTime(r.get("start_time"))).toString(),
                            null,
                            ((Number) r.get("organizer_id")).longValue(),
                            String.valueOf(r.get("organizer_name"))
                    )
            ));
        }
        return Api.ok(list);
    }

    public Api.ApiResponse<List<ActivityLite>> published() { return published(1,100); }

    @GetMapping("/published")
    public Api.ApiResponse<List<ActivityLite>> published(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "100") int size) {
        int limit = pageSize(size), offset = pageOffset(page, limit);
        AuthContext.User p = requireLogin();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT a.id, a.title, a.location, a.start_time, a.cover_url, a.organizer_id, u.nickname AS organizer_name
                FROM activities a
                JOIN users u ON u.id = a.organizer_id
                WHERE a.organizer_id = ?
                ORDER BY a.created_at DESC, a.id DESC
                LIMIT ? OFFSET ?
                """, p.id(), limit, offset);
        List<ActivityLite> list = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            list.add(new ActivityLite(
                    ((Number) r.get("id")).longValue(),
                    String.valueOf(r.get("title")),
                    String.valueOf(r.get("location")),
                    Objects.requireNonNull(toLocalDateTime(r.get("start_time"))).toString(),
                    r.get("cover_url") == null ? null : String.valueOf(r.get("cover_url")),
                    ((Number) r.get("organizer_id")).longValue(),
                    String.valueOf(r.get("organizer_name"))
            ));
        }
        return Api.ok(list);
    }

    @PutMapping("/avatar")
    @Transactional
    public Api.ApiResponse<Void> updateAvatar(@RequestBody Map<String, String> body) {
        AuthContext.User p = requireLogin();
        String rawUrl = body == null ? null : body.get("avatarUrl");
        String url = rawUrl == null ? null : rawUrl.trim();
        if (url == null || url.isBlank()) throw new com.campuspulse.common.Api.ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "头像地址不能为空");
        jdbcTemplate.queryForObject("SELECT id FROM users WHERE id=? FOR UPDATE",Long.class,p.id());
        uploads.requireOwned(p.id(),url,"avatars");
        jdbcTemplate.update("UPDATE users SET avatar_url = ? WHERE id = ?", url, p.id());
        return Api.ok();
    }

    @GetMapping("/default-avatars")
    public Api.ApiResponse<List<String>> defaultAvatars() {
        List<String> list = List.of(
                "https://images.unsplash.com/photo-1502685104226-ee32379fefbe?w=256&q=80",
                "https://images.unsplash.com/photo-1544005313-94ddf0286df2?w=256&q=80",
                "https://images.unsplash.com/photo-1527980965255-d3b416303d12?w=256&q=80",
                "https://images.unsplash.com/photo-1547425260-76bcadfb4f2c?w=256&q=80",
                "https://images.unsplash.com/photo-1529665253569-6d01c0eaf7b6?w=256&q=80"
        );
        return Api.ok(list);
    }

    public Api.ApiResponse<List<TeamLite>> myTeams() { return myTeams(1,100); }

    @GetMapping("/teams")
    public Api.ApiResponse<List<TeamLite>> myTeams(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "100") int size) {
        int limit = pageSize(size), offset = pageOffset(page, limit);
        AuthContext.User p = requireLogin();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT t.id, t.title, t.status, tm.role,
                       (SELECT COUNT(*) FROM team_member tm WHERE tm.team_id = t.id AND tm.status = 'ACTIVE') AS member_count,
                       t.max_members
                FROM team_member tm
                JOIN teams t ON t.id = tm.team_id
                WHERE tm.user_id = ? AND tm.status = 'ACTIVE'
                ORDER BY tm.joined_at DESC, t.id DESC
                LIMIT ? OFFSET ?
                """, p.id(), limit, offset);
        List<TeamLite> list = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            list.add(new TeamLite(
                    ((Number) r.get("id")).longValue(),
                    String.valueOf(r.get("title")),
                    String.valueOf(r.get("status")),
                    ((Number) r.get("member_count")).intValue(),
                    ((Number) r.get("max_members")).intValue(),
                    String.valueOf(r.get("role")),
                    "JOINED"
            ));
        }
        return Api.ok(list);
    }

    public Api.ApiResponse<List<TeamLite>> joinedTeams() { return joinedTeams(1,100); }

    @GetMapping("/teams-joined")
    public Api.ApiResponse<List<TeamLite>> joinedTeams(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "100") int size) {
        int limit = pageSize(size), offset = pageOffset(page, limit);
        AuthContext.User p = requireLogin();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT t.id, t.title, t.status, tm.role, 'JOINED' AS participation_status,
                       (SELECT COUNT(*) FROM team_member tm2 WHERE tm2.team_id = t.id AND tm2.status = 'ACTIVE') AS member_count,
                       t.max_members
                FROM team_member tm
                JOIN teams t ON t.id = tm.team_id
                WHERE tm.user_id = ? AND tm.status = 'ACTIVE' AND tm.role <> 'CREATOR'
                UNION ALL
                SELECT t.id, t.title, t.status, 'APPLICANT' AS role, 'PENDING' AS participation_status,
                       (SELECT COUNT(*) FROM team_member tm2 WHERE tm2.team_id = t.id AND tm2.status = 'ACTIVE') AS member_count,
                       t.max_members
                FROM team_join_request tjr
                JOIN teams t ON t.id = tjr.team_id
                WHERE tjr.user_id = ?
                  AND tjr.status = 'PENDING'
                  AND NOT EXISTS (
                      SELECT 1
                      FROM team_member tm
                      WHERE tm.team_id = tjr.team_id
                        AND tm.user_id = tjr.user_id
                        AND tm.status = 'ACTIVE'
                  )
                ORDER BY id DESC
                LIMIT ? OFFSET ?
                """, p.id(), p.id(), limit, offset);
        List<TeamLite> list = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            list.add(new TeamLite(
                    ((Number) r.get("id")).longValue(),
                    String.valueOf(r.get("title")),
                    String.valueOf(r.get("status")),
                    ((Number) r.get("member_count")).intValue(),
                    ((Number) r.get("max_members")).intValue(),
                    String.valueOf(r.get("role")),
                    String.valueOf(r.get("participation_status"))
            ));
        }
        return Api.ok(list);
    }

    public Api.ApiResponse<List<TeamLite>> createdTeams() { return createdTeams(1,100); }

    @GetMapping("/teams-created")
    public Api.ApiResponse<List<TeamLite>> createdTeams(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "100") int size) {
        int limit = pageSize(size), offset = pageOffset(page, limit);
        AuthContext.User p = requireLogin();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT t.id, t.title, t.status, 'CREATOR' AS role,
                       (SELECT COUNT(*) FROM team_member tm WHERE tm.team_id = t.id AND tm.status = 'ACTIVE') AS member_count,
                       t.max_members
                FROM teams t
                WHERE t.creator_id = ?
                ORDER BY t.created_at DESC, t.id DESC
                LIMIT ? OFFSET ?
                """, p.id(), limit, offset);
        List<TeamLite> list = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            list.add(new TeamLite(
                    ((Number) r.get("id")).longValue(),
                    String.valueOf(r.get("title")),
                    String.valueOf(r.get("status")),
                    ((Number) r.get("member_count")).intValue(),
                    ((Number) r.get("max_members")).intValue(),
                    String.valueOf(r.get("role")),
                    "CREATOR"
            ));
        }
        return Api.ok(list);
    }

    public Api.ApiResponse<List<TeamLite>> teamChats() { return teamChats(1,100); }

    @GetMapping("/team-chats")
    public Api.ApiResponse<List<TeamLite>> teamChats(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "100") int size) {
        int limit = pageSize(size), offset = pageOffset(page, limit);
        AuthContext.User p = requireLogin();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT t.id, t.title, t.status, tm.role,
                       (SELECT COUNT(*) FROM team_member tm2 WHERE tm2.team_id = t.id AND tm2.status = 'ACTIVE') AS member_count,
                       t.max_members
                FROM team_member tm
                JOIN teams t ON t.id = tm.team_id
                WHERE tm.user_id = ? AND tm.status = 'ACTIVE' AND t.status='OPEN' AND (COALESCE(t.end_time,t.start_time) IS NULL OR COALESCE(t.end_time,t.start_time)>CURRENT_TIMESTAMP) AND (t.activity_id IS NULL OR EXISTS(SELECT 1 FROM activities a WHERE a.id=t.activity_id AND a.status='PUBLISHED' AND a.audit_status='APPROVED' AND a.teaming_enabled=1 AND COALESCE(a.end_time,a.start_time)>CURRENT_TIMESTAMP))
                ORDER BY tm.joined_at DESC, t.id DESC
                LIMIT ? OFFSET ?
                """, p.id(), limit, offset);
        List<TeamLite> list = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            list.add(new TeamLite(
                    ((Number) r.get("id")).longValue(),
                    String.valueOf(r.get("title")),
                    String.valueOf(r.get("status")),
                    ((Number) r.get("member_count")).intValue(),
                    ((Number) r.get("max_members")).intValue(),
                    String.valueOf(r.get("role")),
                    "JOINED"
            ));
        }
        return Api.ok(list);
    }

    public Api.ApiResponse<List<ActivityLite>> activityChats() { return activityChats(1,100); }

    @GetMapping("/activity-chats")
    public Api.ApiResponse<List<ActivityLite>> activityChats(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "100") int size) {
        int limit = pageSize(size), offset = pageOffset(page, limit);
        AuthContext.User p = requireLogin();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT DISTINCT a.id, a.title, a.location, a.start_time, a.cover_url, a.organizer_id, u.nickname AS organizer_name
                FROM activities a
                JOIN users u ON u.id = a.organizer_id
                LEFT JOIN registrations r ON r.activity_id = a.id AND r.user_id = ? AND r.status = 'APPROVED'
                WHERE a.chat_enabled = 1 AND a.status='PUBLISHED' AND a.audit_status='APPROVED' AND COALESCE(a.end_time,a.start_time)>CURRENT_TIMESTAMP
                  AND a.audit_status = 'APPROVED'
                  AND a.status = 'PUBLISHED'
                  AND (a.organizer_id = ? OR r.id IS NOT NULL)
                ORDER BY a.start_time DESC, a.id DESC
                LIMIT ? OFFSET ?
                """, p.id(), p.id(), limit, offset);
        List<ActivityLite> list = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            list.add(new ActivityLite(
                    ((Number) r.get("id")).longValue(),
                    String.valueOf(r.get("title")),
                    String.valueOf(r.get("location")),
                    Objects.requireNonNull(toLocalDateTime(r.get("start_time"))).toString(),
                    r.get("cover_url") == null ? null : String.valueOf(r.get("cover_url")),
                    ((Number) r.get("organizer_id")).longValue(),
                    String.valueOf(r.get("organizer_name"))
            ));
        }
        return Api.ok(list);
    }

    private static int pageSize(int size) { return Math.min(Math.max(size,1),100); }
    private static int pageOffset(int page,int size) { return (Math.min(Math.max(page,1),1000000)-1)*size; }

    private AuthContext.User requireLogin() {
        return AuthContext.requireUser();
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
    public record ActivityLite(long id, String title, String location, String startTime, String coverUrl, Long organizerId, String organizerName) {
    }

    @com.campuspulse.content.ContentEntity("TEAM")
    public record TeamLite(long id, String title, String status, int members, int maxMembers, String role, String participationStatus) {
    }

    public record RegistrationItem(long id, String status, String createdAt, ActivityLite activity) {
    }

    public record UpdateMeRequest(String college, String campus, String major, String educationLevel, String bio) {
    }
}
