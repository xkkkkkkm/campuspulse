package com.campuspulse.admin;

import com.campuspulse.common.Api;
import com.campuspulse.activity.ActivityRepository;
import org.springframework.transaction.annotation.Transactional;
import com.campuspulse.security.AuthContext;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final JdbcTemplate jdbcTemplate;
    private final ActivityRepository activityRepository;

    public AdminController(JdbcTemplate jdbcTemplate, ActivityRepository activityRepository) {
        this.activityRepository = activityRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    public Api.ApiResponse<List<TagItem>> tags() {return tags(1,100);}

    @GetMapping("/tags")
    public Api.ApiResponse<List<TagItem>> tags(@RequestParam(defaultValue = "1") int page,@RequestParam(defaultValue = "100") int size) {
        int limit=pageSize(size), offset=pageOffset(page,limit);
        requireAdmin();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT id, name, type FROM tags ORDER BY type, name, id LIMIT ? OFFSET ?",limit,offset);
        List<TagItem> list = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            list.add(new TagItem(((Number) r.get("id")).longValue(), String.valueOf(r.get("name")), String.valueOf(r.get("type"))));
        }
        return Api.ok(list);
    }

    @PostMapping("/tags")
    @Transactional
    public Api.ApiResponse<Void> createTag(@RequestBody CreateTagRequest req) {
        requireAdmin();
        if (req == null || req.name() == null || req.name().trim().isEmpty() || req.name().trim().length() > 64) {
            throw new Api.ApiException(HttpStatus.BAD_REQUEST, "标签名不合法");
        }
        if (req.type() == null) throw new Api.ApiException(HttpStatus.BAD_REQUEST,"标签类型不支持");
        String type = req.type().trim().toUpperCase(java.util.Locale.ROOT);
        if (!List.of("CATEGORY","INTEREST").contains(type)) throw new Api.ApiException(HttpStatus.BAD_REQUEST,"标签类型不支持");
        jdbcTemplate.update("INSERT INTO tags (name, type) VALUES (?, ?)", req.name().trim(), type);
        audit("CREATE_TAG","TAG",null,null,req.name().trim()+":"+type,null);
        return Api.ok();
    }

    public Api.ApiResponse<List<FeaturedItem>> featured() {return featured(1,100);}

    @GetMapping("/featured")
    public Api.ApiResponse<List<FeaturedItem>> featured(@RequestParam(defaultValue = "1") int page,@RequestParam(defaultValue = "100") int size) {
        int limit=pageSize(size), offset=pageOffset(page,limit);
        requireAdmin();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT f.id, f.activity_id, f.weight, a.title, f.start_time, f.end_time
                FROM ops_featured_activity f
                JOIN activities a ON a.id = f.activity_id
                ORDER BY f.weight DESC, f.id DESC
                LIMIT ? OFFSET ?
                """,limit,offset);
        List<FeaturedItem> list = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            list.add(new FeaturedItem(
                    ((Number) r.get("id")).longValue(),
                    ((Number) r.get("activity_id")).longValue(),
                    String.valueOf(r.get("title")),
                    ((Number) r.get("weight")).intValue(),
                    toLocalDateTimeString(r.get("start_time")),
                    toLocalDateTimeString(r.get("end_time"))
            ));
        }
        return Api.ok(list);
    }

    @PostMapping("/featured")
    @Transactional
    public Api.ApiResponse<Void> upsertFeatured(@RequestBody UpsertFeaturedRequest req) {
        requireAdmin();
        if (req == null || req.activityId() == null || req.weight() == null) {
            throw new Api.ApiException(HttpStatus.BAD_REQUEST, "参数错误");
        }
        if (req.weight() < 0 || req.weight() > 1000) throw new Api.ApiException(HttpStatus.BAD_REQUEST,"推荐权重必须在 0 至 1000 之间");
        var activity = activityRepository.lock(req.activityId());
        ActivityRepository.requireLive(activity);
        jdbcTemplate.update("INSERT INTO ops_featured_activity (activity_id,weight) VALUES (?,?) ON DUPLICATE KEY UPDATE weight=VALUES(weight)", req.activityId(),req.weight());
        jdbcTemplate.update("INSERT INTO ops_featured_item (target_type,target_id,weight,status) VALUES ('ACTIVITY',?,?,'ACTIVE') ON DUPLICATE KEY UPDATE weight=VALUES(weight),status='ACTIVE'",req.activityId(),req.weight());
        audit("SET_FEATURED","ACTIVITY",req.activityId(),null,String.valueOf(req.weight()),null);
        return Api.ok();
    }

    @GetMapping("/stats")
    public Api.ApiResponse<Map<String, Object>> stats() {
        requireAdmin();
        long userCount = valueOf("SELECT COUNT(*) FROM users");
        long enabledUsers = valueOf("SELECT COUNT(*) FROM users WHERE status = 1");
        long disabledUsers = valueOf("SELECT COUNT(*) FROM users WHERE status = 0");
        long activityCount = valueOf("SELECT COUNT(*) FROM activities");
        long publishedActivities = valueOf("SELECT COUNT(*) FROM activities WHERE status = 'PUBLISHED'");
        long pendingActivities = valueOf("SELECT COUNT(*) FROM activities WHERE audit_status = 'PENDING'");
        long featuredActivities = valueOf("SELECT COUNT(*) FROM ops_featured_activity");
        long teamCount = valueOf("SELECT COUNT(*) FROM teams");
        long registrationCount = valueOf("SELECT COUNT(*) FROM registrations");
        long messageCount = valueOf("SELECT COUNT(*) FROM messages") + valueOf("SELECT COUNT(*) FROM dm_message") + valueOf("SELECT COUNT(*) FROM activity_chat_message");
        return Api.ok(Map.of(
                "userCount", userCount,
                "enabledUsers", enabledUsers,
                "disabledUsers", disabledUsers,
                "activityCount", activityCount,
                "publishedActivities", publishedActivities,
                "pendingActivities", pendingActivities,
                "featuredActivities", featuredActivities,
                "teamCount", teamCount,
                "registrationCount", registrationCount,
                "messageCount", messageCount
        ));
    }

    public Api.ApiResponse<List<UserAdminItem>> users(String keyword,String role,Integer status,int size) {return users(keyword,role,status,1,size);}

    @GetMapping("/users")
    public Api.ApiResponse<List<UserAdminItem>> users(@RequestParam(required = false) String keyword,
                                                      @RequestParam(required = false) String role,
                                                      @RequestParam(required = false) Integer status,
                                                      @RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "100") int size) {
        requireAdmin();
        int limit = pageSize(size), offset = pageOffset(page,limit);
        StringBuilder sql = new StringBuilder("""
                SELECT u.id, u.username, u.student_no, u.nickname, u.college, u.role, u.status, u.created_at,
                       (SELECT COUNT(*) FROM activities a WHERE a.organizer_id = u.id) AS published_count,
                       (SELECT COUNT(*) FROM registrations r WHERE r.user_id = u.id) AS registration_count,
                       (SELECT COUNT(*) FROM team_member tm WHERE tm.user_id = u.id AND tm.status = 'ACTIVE') AS team_count
                FROM users u
                WHERE 1 = 1
                """);
        List<Object> args = new ArrayList<>();
        String kw = keyword == null ? "" : keyword.trim();
        if (!kw.isBlank()) {
            String like = "%" + kw + "%";
            sql.append(" AND (u.username LIKE ? OR u.nickname LIKE ? OR COALESCE(u.student_no, '') LIKE ? OR COALESCE(u.college, '') LIKE ?)");
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
        }
        String rl = role == null ? "" : role.trim();
        if (!rl.isBlank()) {
            if (List.of("ORG","ORGANIZER").contains(rl)) sql.append(" AND u.role IN ('ORG','ORGANIZER')");
            else { sql.append(" AND u.role = ?"); args.add(rl); }
        }
        if (status != null) {
            sql.append(" AND u.status = ?");
            args.add(status);
        }
        sql.append(" ORDER BY u.created_at DESC, u.id DESC LIMIT ? OFFSET ?");
        args.add(limit);
        args.add(offset);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), args.toArray());
        List<UserAdminItem> list = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            list.add(new UserAdminItem(
                    ((Number) row.get("id")).longValue(),
                    String.valueOf(row.get("username")),
                    row.get("student_no") == null ? null : String.valueOf(row.get("student_no")),
                    String.valueOf(row.get("nickname")),
                    row.get("college") == null ? null : String.valueOf(row.get("college")),
                    normalizeRole(String.valueOf(row.get("role"))),
                    ((Number) row.get("status")).intValue() == 1,
                    toLocalDateTimeString(row.get("created_at")),
                    ((Number) row.get("published_count")).intValue(),
                    ((Number) row.get("registration_count")).intValue(),
                    ((Number) row.get("team_count")).intValue()
            ));
        }
        return Api.ok(list);
    }

    @GetMapping("/users/{id}/detail")
    public Api.ApiResponse<UserAdminDetail> userDetail(@PathVariable long id) {
        requireAdmin();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT u.id, u.username, u.student_no, u.nickname, u.college, u.major, u.education_level,
                       u.bio, u.phone, u.role, u.status, u.created_at,
                       (SELECT COUNT(*) FROM activities a WHERE a.organizer_id = u.id) AS published_count,
                       (SELECT COUNT(*) FROM registrations r WHERE r.user_id = u.id) AS registration_count,
                       (SELECT COUNT(*) FROM team_member tm WHERE tm.user_id = u.id AND tm.status = 'ACTIVE') AS team_count
                FROM users u
                WHERE u.id = ?
                LIMIT 1
                """, id);
        if (rows.isEmpty()) {
            throw new Api.ApiException(HttpStatus.NOT_FOUND, "用户不存在");
        }
        Map<String, Object> user = rows.get(0);

        List<UserPublishedActivity> published = jdbcTemplate.queryForList("""
                SELECT a.id, a.title, a.location, a.start_time, a.status, a.audit_status,
                       (SELECT COUNT(*) FROM registrations r WHERE r.activity_id = a.id AND r.status IN ('APPLIED','APPROVED')) AS registered_count
                FROM activities a
                WHERE a.organizer_id = ?
                ORDER BY a.created_at DESC
                LIMIT 12
                """, id).stream().map(row -> new UserPublishedActivity(
                ((Number) row.get("id")).longValue(),
                String.valueOf(row.get("title")),
                String.valueOf(row.get("location")),
                toLocalDateTimeString(row.get("start_time")),
                String.valueOf(row.get("status")),
                String.valueOf(row.get("audit_status")),
                ((Number) row.get("registered_count")).intValue()
        )).toList();

        List<UserTeamMembership> teams = jdbcTemplate.queryForList("""
                SELECT t.id, t.title, t.status, t.activity_id,
                       COALESCE(a.title, '') AS activity_title,
                       tm.role AS member_role,
                       (SELECT COUNT(*) FROM team_member tm2 WHERE tm2.team_id = t.id AND tm2.status = 'ACTIVE') AS member_count,
                       t.max_members
                FROM team_member tm
                JOIN teams t ON t.id = tm.team_id
                LEFT JOIN activities a ON a.id = t.activity_id
                WHERE tm.user_id = ? AND tm.status = 'ACTIVE'
                ORDER BY tm.joined_at DESC
                LIMIT 12
                """, id).stream().map(row -> new UserTeamMembership(
                ((Number) row.get("id")).longValue(),
                row.get("activity_id") == null ? null : ((Number) row.get("activity_id")).longValue(),
                String.valueOf(row.get("title")),
                String.valueOf(row.get("status")),
                row.get("activity_title") == null || String.valueOf(row.get("activity_title")).isBlank() ? null : String.valueOf(row.get("activity_title")),
                String.valueOf(row.get("member_role")),
                ((Number) row.get("member_count")).intValue(),
                ((Number) row.get("max_members")).intValue()
        )).toList();

        List<UserRegistrationSummary> registrations = jdbcTemplate.queryForList("""
                SELECT r.id, r.status, r.created_at, a.id AS activity_id, a.title
                FROM registrations r
                JOIN activities a ON a.id = r.activity_id
                WHERE r.user_id = ?
                ORDER BY r.created_at DESC
                LIMIT 12
                """, id).stream().map(row -> new UserRegistrationSummary(
                ((Number) row.get("id")).longValue(),
                ((Number) row.get("activity_id")).longValue(),
                String.valueOf(row.get("title")),
                String.valueOf(row.get("status")),
                toLocalDateTimeString(row.get("created_at"))
        )).toList();

        return Api.ok(new UserAdminDetail(
                ((Number) user.get("id")).longValue(),
                String.valueOf(user.get("username")),
                user.get("student_no") == null ? null : String.valueOf(user.get("student_no")),
                String.valueOf(user.get("nickname")),
                user.get("college") == null ? null : String.valueOf(user.get("college")),
                user.get("major") == null ? null : String.valueOf(user.get("major")),
                user.get("education_level") == null ? null : String.valueOf(user.get("education_level")),
                user.get("bio") == null ? null : String.valueOf(user.get("bio")),
                user.get("phone") == null ? null : String.valueOf(user.get("phone")),
                normalizeRole(String.valueOf(user.get("role"))),
                ((Number) user.get("status")).intValue() == 1,
                toLocalDateTimeString(user.get("created_at")),
                ((Number) user.get("published_count")).intValue(),
                ((Number) user.get("registration_count")).intValue(),
                ((Number) user.get("team_count")).intValue(),
                published,
                teams,
                registrations
        ));
    }

    @PostMapping("/users/{id}/status")
    @Transactional
    public Api.ApiResponse<Void> updateUserStatus(@PathVariable long id, @RequestBody UpdateUserStatusRequest req) {
        AuthContext.User admin = requireAdmin();
        if (req == null || req.enabled() == null) {
            throw new Api.ApiException(HttpStatus.BAD_REQUEST, "参数错误");
        }
        if (admin.id() == id && !req.enabled()) {
            throw new Api.ApiException(HttpStatus.BAD_REQUEST, "不能停用当前管理员账号");
        }
        jdbcTemplate.queryForObject("SELECT id FROM admin_governance_lock WHERE id=1 FOR UPDATE",Integer.class);
        var rows=jdbcTemplate.queryForList("SELECT role,status FROM users WHERE id=? FOR UPDATE",id);
        if(rows.isEmpty()) throw new Api.ApiException(HttpStatus.NOT_FOUND,"用户不存在");
        var previous=rows.get(0);
        protectLastAdmin(id,String.valueOf(previous.get("role")),req.enabled());
        jdbcTemplate.update("UPDATE users SET status=?,token_version=token_version+1 WHERE id=?",req.enabled()?1:0,id);
        audit("USER_STATUS","USER",id,String.valueOf(previous.get("status")),req.enabled()?"1":"0",null);
        return Api.ok();
    }

    public Api.ApiResponse<List<ActivityAuditItem>> activities(String auditStatus,String status,String keyword,int size) {return activities(auditStatus,status,keyword,1,size);}

    @GetMapping("/activities")
    public Api.ApiResponse<List<ActivityAuditItem>> activities(@RequestParam(required = false) String auditStatus,
                                                               @RequestParam(required = false) String status,
                                                               @RequestParam(required = false) String keyword,
                                                               @RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "100") int size) {
        requireAdmin();
        int limit = pageSize(size), offset = pageOffset(page,limit);
        StringBuilder sql = new StringBuilder("""
                SELECT a.id, a.title, a.location, a.start_time, a.created_at, a.audit_status, a.status, a.version,
                       a.organizer_id, u.nickname AS organizer_name,
                       (SELECT COUNT(*) FROM registrations r WHERE r.activity_id = a.id AND r.status IN ('APPLIED','APPROVED')) AS registered_count,
                       (SELECT COUNT(*) FROM favorites fv WHERE fv.activity_id = a.id) AS favorite_count,
                       (SELECT COUNT(*) FROM teams t WHERE t.activity_id = a.id AND t.status = 'OPEN') AS team_count,
                       (SELECT f.weight FROM ops_featured_activity f WHERE f.activity_id = a.id LIMIT 1) AS featured_weight
                FROM activities a
                JOIN users u ON u.id = a.organizer_id
                WHERE 1 = 1
                """);
        List<Object> args = new ArrayList<>();
        String audit = auditStatus == null ? "" : auditStatus.trim();
        if (!audit.isBlank()) {
            sql.append(" AND a.audit_status = ?");
            args.add(audit);
        }
        String lifecycle = status == null ? "" : status.trim();
        if (!lifecycle.isBlank()) {
            sql.append(" AND a.status = ?");
            args.add(lifecycle);
        }
        String kw = keyword == null ? "" : keyword.trim();
        if (!kw.isBlank()) {
            String like = "%" + kw + "%";
            sql.append(" AND (a.title LIKE ? OR a.location LIKE ? OR COALESCE(a.description, '') LIKE ? OR u.nickname LIKE ?)");
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
        }
        sql.append(" ORDER BY CASE WHEN a.status = 'PUBLISHED' THEN 0 ELSE 1 END, a.start_time ASC, a.created_at DESC, a.id DESC LIMIT ? OFFSET ?");
        args.add(limit);
        args.add(offset);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), args.toArray());
        var tagMap = activityRepository.tags(rows.stream().map(r -> ((Number)r.get("id")).longValue()).toList());
        List<ActivityAuditItem> list = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            long activityId = ((Number) row.get("id")).longValue();
            List<String> tags = tagMap.getOrDefault(activityId,List.of());
            list.add(new ActivityAuditItem(
                    activityId,
                    String.valueOf(row.get("title")),
                    String.valueOf(row.get("location")),
                    toLocalDateTimeString(row.get("start_time")),
                    String.valueOf(row.get("audit_status")),
                    String.valueOf(row.get("status")),
                    ((Number) row.get("organizer_id")).longValue(),
                    String.valueOf(row.get("organizer_name")),
                    tags,
                    toLocalDateTimeString(row.get("created_at")),
                    ((Number) row.get("registered_count")).intValue(),
                    ((Number) row.get("favorite_count")).intValue(),
                    ((Number) row.get("team_count")).intValue(),
                    row.get("featured_weight") == null ? null : ((Number) row.get("featured_weight")).intValue(),
                    ((Number)row.get("version")).longValue()
            ));
        }
        return Api.ok(list);
    }

    @PostMapping("/activities/{id}/status")
    @Transactional
    public Api.ApiResponse<Void> updateActivityStatus(@PathVariable long id, @RequestBody UpdateActivityStatusRequest req) {
        requireAdmin();
        if (req == null || req.status() == null || req.status().trim().isEmpty()) {
            throw new Api.ApiException(HttpStatus.BAD_REQUEST, "参数错误");
        }
        String status = req.status().trim().toUpperCase();
        if (!List.of("PUBLISHED", "CANCELLED").contains(status)) {
            throw new Api.ApiException(HttpStatus.BAD_REQUEST, "活动状态不支持");
        }
        Map<String,Object> activity = activityRepository.lock(id);
        ActivityRepository.requireEditable(activity);
        if (req.reason() != null && req.reason().length()>1000) throw new Api.ApiException(HttpStatus.BAD_REQUEST,"操作原因过长");
        if (req.version() != null && req.version().longValue()!=((Number)activity.get("version")).longValue()) throw new Api.ApiException(HttpStatus.CONFLICT,"活动已更新，请刷新后重试");
        jdbcTemplate.update("UPDATE activities SET status=?,version=version+1 WHERE id=?", status, id);
        audit("ACTIVITY_STATUS","ACTIVITY",id,String.valueOf(activity.get("status")),status,req.reason());
        jdbcTemplate.update("""
                INSERT INTO notifications (user_id, type, title, content)
                VALUES (?, 'ADMIN_ACTIVITY_STATUS', ?, ?)
                """,
                ((Number) activity.get("organizer_id")).longValue(),
                "管理员已更新你的活动状态",
                notificationSummary("活动《" + String.valueOf(activity.get("title")) + "》当前状态已变更为 " + status + "。" + (req.reason()==null||req.reason().isBlank()?"":" 原因："+req.reason().trim()))
        );
        return Api.ok();
    }

    @PostMapping("/activities/{id}/audit")
    @Transactional
    public Api.ApiResponse<Void> auditActivity(@PathVariable long id, @RequestBody AuditActivityRequest req) {
        requireAdmin();
        if (req == null || req.auditStatus() == null || req.auditStatus().trim().isEmpty()) {
            throw new Api.ApiException(HttpStatus.BAD_REQUEST, "参数错误");
        }
        String audit = req.auditStatus().trim().toUpperCase();
        if (!List.of("APPROVED", "REJECTED", "PENDING").contains(audit)) {
            throw new Api.ApiException(HttpStatus.BAD_REQUEST, "审核状态不支持");
        }
        Map<String,Object> activity = activityRepository.lock(id);
        ActivityRepository.requireEditable(activity);
        if (req.reason() != null && req.reason().length()>1000) throw new Api.ApiException(HttpStatus.BAD_REQUEST,"操作原因过长");
        if (req.version() != null && req.version().longValue()!=((Number)activity.get("version")).longValue()) throw new Api.ApiException(HttpStatus.CONFLICT,"活动已更新，请刷新后重试");
        jdbcTemplate.update("UPDATE activities SET audit_status=?,version=version+1 WHERE id=?", audit, id);
        audit("ACTIVITY_AUDIT","ACTIVITY",id,String.valueOf(activity.get("audit_status")),audit,req.reason());
        jdbcTemplate.update("""
                INSERT INTO notifications (user_id, type, title, content)
                VALUES (?, 'ADMIN_ACTIVITY_AUDIT', ?, ?)
                """,
                ((Number) activity.get("organizer_id")).longValue(),
                "管理员已更新你的活动审核结果",
                notificationSummary("活动《" + String.valueOf(activity.get("title")) + "》当前审核状态已变更为 " + audit + "。" + (req.reason()==null||req.reason().isBlank()?"":" 原因："+req.reason().trim()))
        );
        return Api.ok();
    }

    @GetMapping("/audit-log")
    public Api.ApiResponse<Map<String,Object>> auditLog(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size) {
        requireAdmin();
        int p=Math.min(Math.max(page,1),1000000),limit=Math.min(Math.max(size,1),100);
        var items=jdbcTemplate.queryForList("SELECT l.*,u.nickname AS actor_name FROM admin_audit_log l LEFT JOIN users u ON u.id=l.actor_id ORDER BY l.id DESC LIMIT ? OFFSET ?",limit,(p-1)*limit);
        return Api.ok(Map.of("items",items,"total",valueOf("SELECT COUNT(*) FROM admin_audit_log"),"page",p,"size",limit));
    }

    @PostMapping("/users/{id}/role")
    @Transactional
    public Api.ApiResponse<Void> updateUserRole(@PathVariable long id,@RequestBody RoleRequest req) {
        requireAdmin();
        if(req==null || req.role()==null || !List.of("USER","ORG","ORGANIZER","ADMIN").contains(req.role())) throw new Api.ApiException(HttpStatus.BAD_REQUEST,"角色不支持");
        if(req.reason()!=null && req.reason().length()>1000) throw new Api.ApiException(HttpStatus.BAD_REQUEST,"操作原因过长");
        jdbcTemplate.queryForObject("SELECT id FROM admin_governance_lock WHERE id=1 FOR UPDATE",Integer.class);
        var rows=jdbcTemplate.queryForList("SELECT role,status FROM users WHERE id=? FOR UPDATE",id);
        if(rows.isEmpty()) throw new Api.ApiException(HttpStatus.NOT_FOUND,"用户不存在");
        var previous=rows.get(0);
        protectLastAdmin(id,normalizeRole(req.role()),((Number)previous.get("status")).intValue()==1);
        jdbcTemplate.update("UPDATE users SET role=?,token_version=token_version+1 WHERE id=?",normalizeRole(req.role()),id);
        audit("USER_ROLE","USER",id,String.valueOf(previous.get("role")),normalizeRole(req.role()),req.reason());
        return Api.ok();
    }

    @PutMapping("/tags/{id}")
    @Transactional
    public Api.ApiResponse<Void> updateTag(@PathVariable long id,@RequestBody CreateTagRequest req) {
        requireAdmin();
        if(req==null || req.name()==null || req.name().isBlank() || req.name().length()>64 || req.type()==null || !List.of("CATEGORY","INTEREST").contains(req.type())) throw new Api.ApiException(HttpStatus.BAD_REQUEST,"标签不合法");
        var rows=jdbcTemplate.queryForList("SELECT name,type FROM tags WHERE id=? FOR UPDATE",id);
        if(rows.isEmpty()) throw new Api.ApiException(HttpStatus.NOT_FOUND,"标签不存在");
        jdbcTemplate.update("UPDATE tags SET name=?,type=? WHERE id=?",req.name().trim(),req.type(),id);
        audit("UPDATE_TAG","TAG",id,rows.get(0).toString(),req.name()+":"+req.type(),null);
        return Api.ok();
    }

    @DeleteMapping("/tags/{id}")
    @Transactional
    public Api.ApiResponse<Void> deleteTag(@PathVariable long id) {
        requireAdmin();
        var rows=jdbcTemplate.queryForList("SELECT name,type FROM tags WHERE id=? FOR UPDATE",id);
        if(rows.isEmpty()) return Api.ok();
        Long references=jdbcTemplate.queryForObject("SELECT (SELECT COUNT(*) FROM activity_tag WHERE tag_id=?)+(SELECT COUNT(*) FROM team_tag WHERE tag_id=?)+(SELECT COUNT(*) FROM user_interest WHERE tag_id=?)",Long.class,id,id,id);
        if(references!=null && references>0) throw new Api.ApiException(HttpStatus.CONFLICT,"标签仍在使用，请先移除关联");
        jdbcTemplate.update("DELETE FROM tags WHERE id=?",id);
        audit("DELETE_TAG","TAG",id,rows.get(0).toString(),null,null);
        return Api.ok();
    }

    @DeleteMapping("/featured/{id}")
    @Transactional
    public Api.ApiResponse<Void> deleteFeatured(@PathVariable long id) {
        requireAdmin();
        var rows=jdbcTemplate.queryForList("SELECT activity_id,weight FROM ops_featured_activity WHERE id=? FOR UPDATE",id);
        if(rows.isEmpty()) return Api.ok();
        long aid=((Number)rows.get(0).get("activity_id")).longValue();
        jdbcTemplate.update("DELETE FROM ops_featured_activity WHERE id=?",id);
        jdbcTemplate.update("UPDATE ops_featured_item SET status='INACTIVE' WHERE target_type='ACTIVITY' AND target_id=?",aid);
        audit("REMOVE_FEATURED","ACTIVITY",aid,rows.get(0).toString(),null,null);
        return Api.ok();
    }

    private static int pageSize(int size) {return Math.min(Math.max(size,1),100);}
    private static int pageOffset(int page,int size) {return (Math.min(Math.max(page,1),1000000)-1)*size;}
    private static String notificationSummary(String text) {
        if(text.codePointCount(0,text.length())<=1000) return text;
        return text.substring(0,text.offsetByCodePoints(0,999))+"…";
    }

    private static String normalizeRole(String role) { return "ORG".equals(role)?"ORGANIZER":role; }

    private void protectLastAdmin(long target,String role,boolean enabled) {
        var active=jdbcTemplate.queryForList("SELECT id FROM users WHERE role='ADMIN' AND status=1 ORDER BY id FOR UPDATE",Long.class);
        if(active.size()==1 && active.get(0)==target && (!"ADMIN".equals(role)||!enabled)) throw new Api.ApiException(HttpStatus.CONFLICT,"不能移除最后一位有效管理员");
    }
    private void audit(String action,String type,Long id,String before,String after,String reason) {
        jdbcTemplate.update("INSERT INTO admin_audit_log (actor_id,action,target_type,target_id,before_value,after_value,reason) VALUES (?,?,?,?,?,?,?)",requireAdmin().id(),action,type,id,before,after,reason);
    }
    public record RoleRequest(String role,String reason) {}

    private AuthContext.User requireAdmin() {
        AuthContext.User p = AuthContext.requireUser();
        if (!"ADMIN".equals(p.role())) {
            throw new Api.ApiException(HttpStatus.FORBIDDEN, "需要管理员权限");
        }
        return p;
    }

    public record TagItem(long id, String name, String type) {
    }

    public record CreateTagRequest(String name, String type) {
    }

    public record FeaturedItem(long id, long activityId, String activityTitle, int weight, String startTime, String endTime) {
    }

    public record UpsertFeaturedRequest(Long activityId, Integer weight) {
    }

    public record UserAdminItem(long id, String username, String studentNo, String nickname, String college,
                                String role, boolean enabled, String createdAt, int publishedCount,
                                int registrationCount, int teamCount) {
    }

    public record UpdateUserStatusRequest(Boolean enabled) {
    }

    @com.campuspulse.content.ContentEntity("ACTIVITY")
    public record ActivityAuditItem(long id, String title, String location, String startTime, String auditStatus, String status,
                                    long organizerId, String organizerName, List<String> tags, String createdAt,
                                    int registeredCount, int favoriteCount, int teamCount, Integer featuredWeight, long version) {
    }

    public record AuditActivityRequest(String auditStatus, String reason, Long version) {
    }

    public record UpdateActivityStatusRequest(String status, String reason, Long version) {
    }

    public record UserAdminDetail(long id, String username, String studentNo, String nickname, String college,
                                  String major, String educationLevel, String bio, String phone, String role,
                                  boolean enabled, String createdAt, int publishedCount, int registrationCount,
                                  int teamCount, List<UserPublishedActivity> publishedActivities,
                                  List<UserTeamMembership> teams, List<UserRegistrationSummary> registrations) {
    }

    @com.campuspulse.content.ContentEntity("ACTIVITY")
    public record UserPublishedActivity(long id, String title, String location, String startTime,
                                        String status, String auditStatus, int registeredCount) {
    }

    @com.campuspulse.content.ContentEntity("TEAM")
    public record UserTeamMembership(long id, Long activityId, String title, String status, String activityTitle,
                                     String memberRole, int memberCount, int maxMembers) {
    }

    public record UserRegistrationSummary(long id, long activityId, String activityTitle, String status, String createdAt) {
    }

    private long valueOf(String sql) {
        return Optional.ofNullable(jdbcTemplate.queryForObject(sql, Long.class)).orElse(0L);
    }

    private static String toLocalDateTimeString(Object value) {
        if (value == null) return null;
        if (value instanceof LocalDateTime ldt) return ldt.toString();
        if (value instanceof Timestamp ts) return ts.toLocalDateTime().toString();
        if (value instanceof java.util.Date d) return new Timestamp(d.getTime()).toLocalDateTime().toString();
        return String.valueOf(value).replace(" ", "T");
    }
}
