package com.campuspulse.recommend;

import com.campuspulse.common.Api;
import com.campuspulse.security.AuthContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class RecommendService {
    private final JdbcTemplate jdbcTemplate;

    public RecommendService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Api.ApiResponse<List<ActivityItem>> feed(int size) {
        return activities(size);
    }

    public Api.ApiResponse<List<ActivityItem>> activities(int size) {
        int limit = Math.min(Math.max(size, 1), 30);
        AuthContext.User user = AuthContext.userOrNull();
        Set<String> interests = user == null ? Set.of() : loadInterests(user.id());
        Map<Long, ModelPrediction> modelScores = user == null ? Map.of() : loadActivityModelScores(user.id());

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT a.id, a.title, a.location, a.start_time, a.cover_url, a.max_participants,
                       COALESCE(ofi.weight, f.weight, 0) AS featured_weight,
                       (SELECT COUNT(*) FROM registrations r WHERE r.activity_id = a.id AND r.status = 'APPROVED') AS reg_count,
                       (SELECT COUNT(*) FROM favorites fv WHERE fv.activity_id = a.id) AS fav_count,
                       (SELECT COUNT(*) FROM user_behavior_log b WHERE b.target_type = 'ACTIVITY' AND b.target_id = a.id AND b.event_type = 'CLICK') AS click_count
                FROM activities a
                LEFT JOIN ops_featured_activity f ON f.activity_id = a.id
                     AND (f.start_time IS NULL OR f.start_time<=NOW()) AND (f.end_time IS NULL OR f.end_time>NOW())
                LEFT JOIN ops_featured_item ofi ON ofi.target_type = 'ACTIVITY'
                     AND ofi.target_id = a.id
                     AND ofi.status = 'ACTIVE'
                     AND (ofi.start_time IS NULL OR ofi.start_time <= NOW())
                     AND (ofi.end_time IS NULL OR ofi.end_time >= NOW())
                WHERE a.status = 'PUBLISHED' AND a.audit_status = 'APPROVED' AND a.archived_at IS NULL
                  AND COALESCE(a.end_time,a.start_time)>NOW()
                  AND a.start_time >= (NOW() - INTERVAL 1 DAY)
                  AND a.start_time <= (NOW() + INTERVAL 180 DAY)
                ORDER BY a.start_time ASC
                LIMIT 300
                """);

        Map<Long, List<String>> tagMap = loadActivityTags(rows);
        LocalDateTime now = LocalDateTime.now();
        List<ScoredItem<ActivityItem>> scored = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            long id = number(r.get("id")).longValue();
            LocalDateTime start = toLocalDateTime(r.get("start_time"));
            List<String> tags = tagMap.getOrDefault(id, List.of());
            int registrations = number(r.get("reg_count")).intValue();
            int favorites = number(r.get("fav_count")).intValue();
            int clicks = number(r.get("click_count")).intValue();
            int featured = number(r.get("featured_weight")).intValue();
            ModelPrediction prediction = modelScores.get(id);
            double modelScore = prediction == null ? 0.0 : prediction.score();
            double ruleScore = featuredWeight(featured)
                    + popularityScore(registrations, favorites, clicks)
                    + timeScore(now, start)
                    + interestScore(interests, tags);
            double score = prediction != null ? modelScore * 100.0 + ruleScore * 0.25 : ruleScore;
            String reason = prediction != null ? nullSafeReason(prediction.reason(), "模型排序") : fallbackReason(interests, tags, featured);
            scored.add(new ScoredItem<>(score, new ActivityItem(
                    id,
                    String.valueOf(r.get("title")),
                    String.valueOf(r.get("location")),
                    start == null ? null : start.toString(),
                    r.get("cover_url") == null ? null : String.valueOf(r.get("cover_url")),
                    registrations,
                    number(r.get("max_participants")).intValue(),
                    tags,
                    featured > 0,
                    reason
            )));
        }
        scored.sort(Comparator.comparingDouble(ScoredItem<ActivityItem>::score).reversed()
                .thenComparing(si -> nullSafe(si.item().startTime())));
        return Api.ok(scored.stream().limit(limit).map(ScoredItem::item).toList());
    }

    public Api.ApiResponse<List<TeamItem>> teams(int size) {
        int limit = Math.min(Math.max(size, 1), 30);
        AuthContext.User user = AuthContext.userOrNull();
        Set<String> interests = user == null ? Set.of() : loadInterests(user.id());
        Map<Long, ModelPrediction> modelScores = user == null ? Map.of() : loadTeamModelScores(user.id());

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT t.id, t.activity_id, t.title, t.description, t.start_time, t.end_time,
                       t.max_members, t.creator_id, u.nickname AS creator_name, a.title AS activity_title,
                       COALESCE(ofi.weight, 0) AS featured_weight,
                       (SELECT COUNT(*) FROM team_member tm WHERE tm.team_id = t.id AND tm.status = 'ACTIVE') AS member_count,
                       (SELECT COUNT(*) FROM team_join_request tjr WHERE tjr.team_id = t.id AND tjr.status = 'PENDING') AS pending_count,
                       (SELECT COUNT(*) FROM user_behavior_log b WHERE b.target_type = 'TEAM' AND b.target_id = t.id AND b.event_type = 'CLICK') AS click_count
                FROM teams t
                JOIN users u ON u.id = t.creator_id
                LEFT JOIN activities a ON a.id = t.activity_id
                LEFT JOIN ops_featured_item ofi ON ofi.target_type = 'TEAM'
                     AND ofi.target_id = t.id
                     AND ofi.status = 'ACTIVE'
                     AND (ofi.start_time IS NULL OR ofi.start_time <= NOW())
                     AND (ofi.end_time IS NULL OR ofi.end_time >= NOW())
                WHERE t.status = 'OPEN' AND t.archived_at IS NULL
                  AND (t.end_time IS NULL OR t.end_time>NOW())
                  AND (t.activity_id IS NULL OR (a.status='PUBLISHED' AND a.audit_status='APPROVED' AND a.teaming_enabled=1 AND COALESCE(a.end_time,a.start_time)>NOW()))
                ORDER BY t.created_at DESC
                LIMIT 300
                """);

        Map<Long, List<String>> tagMap = loadTeamTags(rows);
        Map<Long,Boolean> joinedMap=new HashMap<>(), pendingMap=new HashMap<>();
        if(user!=null) {
            for(var row:jdbcTemplate.queryForList("SELECT team_id FROM team_member WHERE user_id=? AND status='ACTIVE'",user.id())) joinedMap.put(number(row.get("team_id")).longValue(),true);
            for(var row:jdbcTemplate.queryForList("SELECT team_id FROM team_join_request WHERE user_id=? AND status='PENDING'",user.id())) pendingMap.put(number(row.get("team_id")).longValue(),true);
        }
        List<ScoredItem<TeamItem>> scored = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            long id = number(r.get("id")).longValue();
            long creatorId = number(r.get("creator_id")).longValue();
            int members = number(r.get("member_count")).intValue();
            int maxMembers = number(r.get("max_members")).intValue();
            int pending = number(r.get("pending_count")).intValue();
            int clicks = number(r.get("click_count")).intValue();
            int featured = number(r.get("featured_weight")).intValue();
            List<String> tags = tagMap.getOrDefault(id, List.of());
            boolean joined = joinedMap.getOrDefault(id,false);
            boolean userPending = !joined && pendingMap.getOrDefault(id,false);
            int vacancy = maxMembers <= 0 ? 5 : Math.max(maxMembers - members, 0);
            ModelPrediction prediction = modelScores.get(id);
            double modelScore = prediction == null ? 0.0 : prediction.score();
            double ruleScore = featuredWeight(featured)
                    + interestScore(interests, tags)
                    + Math.min(vacancy, 5) * 4.0
                    + Math.log1p(clicks + pending * 1.5) * 4.0
                    + (r.get("activity_id") == null ? 0.0 : 3.0)
                    + (joined || userPending ? -40.0 : 0.0);
            double score = prediction != null ? modelScore * 100.0 + ruleScore * 0.25 : ruleScore;
            String reason = prediction != null ? nullSafeReason(prediction.reason(), "模型排序") : fallbackReason(interests, tags, featured);
            scored.add(new ScoredItem<>(score, new TeamItem(
                    id,
                    r.get("activity_id") == null ? null : number(r.get("activity_id")).longValue(),
                    String.valueOf(r.get("title")),
                    r.get("description") == null ? "" : String.valueOf(r.get("description")),
                    creatorId,
                    String.valueOf(r.get("creator_name")),
                    r.get("activity_title") == null ? null : String.valueOf(r.get("activity_title")),
                    members,
                    maxMembers,
                    toLocalDateTimeString(r.get("start_time")),
                    toLocalDateTimeString(r.get("end_time")),
                    joined,
                    userPending,
                    tags,
                    featured > 0,
                    reason
            )));
        }
        scored.sort(Comparator.comparingDouble(ScoredItem<TeamItem>::score).reversed()
                .thenComparing(si -> si.item().id()));
        return Api.ok(scored.stream().limit(limit).map(ScoredItem::item).toList());
    }

    private Map<Long, ModelPrediction> loadActivityModelScores(long userId) {
        return loadModelScores("""
                SELECT s.activity_id AS target_id, s.score, s.reason
                FROM recommend_activity_score s
                JOIN recommendation_active active ON active.id=1 AND active.version=s.model_version
                JOIN recommendation_release release_info ON release_info.version=active.version AND release_info.expires_at>NOW()
                WHERE s.user_id = ?
                ORDER BY score DESC
                LIMIT 500
                """, userId);
    }

    private Map<Long, ModelPrediction> loadTeamModelScores(long userId) {
        return loadModelScores("""
                SELECT s.team_id AS target_id, s.score, s.reason
                FROM recommend_team_score s
                JOIN recommendation_active active ON active.id=1 AND active.version=s.model_version
                JOIN recommendation_release release_info ON release_info.version=active.version AND release_info.expires_at>NOW()
                WHERE s.user_id = ?
                ORDER BY score DESC
                LIMIT 500
                """, userId);
    }

    private Map<Long, ModelPrediction> loadModelScores(String sql, long userId) {
        Map<Long, ModelPrediction> map = new HashMap<>();
        for (Map<String, Object> r : jdbcTemplate.queryForList(sql, userId)) {
            map.put(number(r.get("target_id")).longValue(), new ModelPrediction(
                    number(r.get("score")).doubleValue(),
                    r.get("reason") == null ? null : String.valueOf(r.get("reason"))
            ));
        }
        return map;
    }

    private Set<String> loadInterests(long userId) {
        List<String> tags = jdbcTemplate.queryForList("""
                SELECT t.name
                FROM user_interest ui
                JOIN tags t ON t.id = ui.tag_id
                WHERE ui.user_id = ?
                """, String.class, userId);
        if (!tags.isEmpty()) return new HashSet<>(tags);
        List<String> fromBehavior = jdbcTemplate.queryForList("""
                SELECT x.name
                FROM (
                    SELECT t.name, MAX(b.event_time) AS last_time
                    FROM user_behavior_log b
                    JOIN activity_tag at2 ON at2.activity_id = b.target_id AND b.target_type = 'ACTIVITY'
                    JOIN tags t ON t.id = at2.tag_id
                    WHERE b.user_id = ? AND b.event_type IN ('CLICK','FAVORITE','REGISTER')
                    GROUP BY t.name
                ) x
                ORDER BY x.last_time DESC
                LIMIT 10
                """, String.class, userId);
        return new HashSet<>(fromBehavior);
    }

    private Map<Long, List<String>> loadActivityTags(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) return Map.of();
        List<Long> ids = rows.stream().map(r -> number(r.get("id")).longValue()).toList();
        String in = String.join(",", ids.stream().map(x -> "?").toList());
        List<Map<String, Object>> tagRows = jdbcTemplate.queryForList("""
                SELECT at2.activity_id, t.name
                FROM activity_tag at2
                JOIN tags t ON t.id = at2.tag_id
                WHERE at2.activity_id IN (%s)
                """.formatted(in), ids.toArray());
        Map<Long, List<String>> map = new HashMap<>();
        for (Map<String, Object> r : tagRows) {
            map.computeIfAbsent(number(r.get("activity_id")).longValue(), k -> new ArrayList<>()).add(String.valueOf(r.get("name")));
        }
        map.values().forEach(list -> list.sort(String::compareTo));
        return map;
    }

    private Map<Long,List<String>> loadTeamTags(List<Map<String,Object>> rows) {
        if(rows.isEmpty()) return Map.of();
        List<Long> ids=rows.stream().map(r->number(r.get("id")).longValue()).toList();
        String placeholders=String.join(",",Collections.nCopies(ids.size(),"?"));
        Map<Long,List<String>> result=new HashMap<>();
        List<Object> args=new ArrayList<>(ids);args.addAll(ids);
        for(var row:jdbcTemplate.queryForList("SELECT tt.team_id,tg.name FROM team_tag tt JOIN tags tg ON tg.id=tt.tag_id WHERE tt.team_id IN ("+placeholders+") UNION SELECT t.id AS team_id,tg.name FROM teams t JOIN activity_tag a ON a.activity_id=t.activity_id JOIN tags tg ON tg.id=a.tag_id WHERE t.id IN ("+placeholders+")",args.toArray()))
            result.computeIfAbsent(number(row.get("team_id")).longValue(),k->new ArrayList<>()).add(String.valueOf(row.get("name")));
        return result;
    }

    private long count(String sql, Object... args) {
        return Optional.ofNullable(jdbcTemplate.queryForObject(sql, Long.class, args)).orElse(0L);
    }

    private double featuredWeight(int featured) {
        return Math.max(featured, 0) * 2.0;
    }

    private double popularityScore(int registrations, int favorites, int clicks) {
        return Math.log1p(registrations * 1.0 + favorites * 0.6 + clicks * 0.2) * 8.0;
    }

    private double timeScore(LocalDateTime now, LocalDateTime start) {
        if (start == null) return 0.0;
        long hours = Duration.between(now, start).toHours();
        if (hours < -24) return -20.0;
        if (hours < 0) return 2.0;
        if (hours <= 24) return 18.0;
        if (hours <= 72) return 10.0;
        if (hours <= 168) return 6.0;
        return 2.0;
    }

    private double interestScore(Set<String> interests, List<String> tags) {
        if (interests == null || interests.isEmpty() || tags == null || tags.isEmpty()) return 0.0;
        long match = tags.stream().filter(interests::contains).count();
        return Math.min(30.0, match * 10.0);
    }

    private String fallbackReason(Set<String> interests, List<String> tags, int featured) {
        if (featured > 0) return "运营推荐";
        if (interests != null && tags != null && tags.stream().anyMatch(interests::contains)) return "兴趣匹配";
        return "近期热门";
    }

    private String nullSafeReason(String reason, String fallback) {
        return reason == null || reason.isBlank() ? fallback : reason;
    }

    private static Number number(Object value) {
        return value instanceof Number n ? n : 0;
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private static LocalDateTime toLocalDateTime(Object value) {
        if (value == null) return null;
        if (value instanceof LocalDateTime ldt) return ldt;
        if (value instanceof Timestamp ts) return ts.toLocalDateTime();
        if (value instanceof java.util.Date d) return new Timestamp(d.getTime()).toLocalDateTime();
        return LocalDateTime.parse(String.valueOf(value).replace(' ', 'T'));
    }

    private static String toLocalDateTimeString(Object value) {
        LocalDateTime ldt = toLocalDateTime(value);
        return ldt == null ? null : ldt.toString();
    }

    @com.campuspulse.content.ContentEntity("ACTIVITY")
    public record ActivityItem(long id, String title, String location, String startTime, String coverUrl,
                               int participants, int maxParticipants, List<String> tags,
                               boolean promoted, String reason) {
    }

    @com.campuspulse.content.ContentEntity("TEAM")
    public record TeamItem(long id, Long activityId, String title, String description,
                           long creatorId, String creatorName, String activityTitle,
                           int members, int maxMembers, String startTime, String endTime,
                           boolean joined, boolean pending, List<String> tags,
                           boolean promoted, String reason) {
    }

    private record ScoredItem<T>(double score, T item) {
    }

    private record ModelPrediction(double score, String reason) {
    }
}
