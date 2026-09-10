package com.campuspulse.search;

import com.campuspulse.common.Api;
import com.campuspulse.activity.ActivityRepository;
import com.campuspulse.security.AuthContext;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

/** Exact SQL filtering/counting and stable ranking across every page; no candidate truncation. */
@RestController
@RequestMapping("/api/search")
public class SearchController {
    private final JdbcTemplate db;
    private final ActivityRepository activities;
    public SearchController(JdbcTemplate db, ActivityRepository activities) {this.db=db;this.activities=activities;}
    @GetMapping
    public Api.ApiResponse<SearchPage> search(@RequestParam String keyword,
            @RequestParam(defaultValue="all") String type, @RequestParam(defaultValue="1") int page,
            @RequestParam(defaultValue="10") int size) {
        String kw=keyword==null?"":keyword.trim();
        int p=Math.min(Math.max(page,1),1000000),limit=Math.min(Math.max(size,1),30);
        if (kw.isEmpty()) return Api.ok(new SearchPage(List.of(),0,p,limit));
        if (kw.length()>200) throw new Api.ApiException(HttpStatus.BAD_REQUEST,"关键词过长");
        String kind=type.toLowerCase(Locale.ROOT);
        if (!Set.of("all","activity","team").contains(kind)) throw new Api.ApiException(HttpStatus.BAD_REQUEST,"搜索类型不支持");
        var viewer=AuthContext.userOrNull();
        long uid=viewer==null?0L:viewer.id();
        String like="%"+kw+"%";
        List<String> selects=new ArrayList<>();
        List<Object> args=new ArrayList<>();
        var tagMatch = com.campuspulse.content.ContentSearch.tags(kw);
        if (!"team".equals(kind)) {
            var translated = com.campuspulse.content.ContentSearch.activities(kw);
            selects.add("""
                SELECT 'ACTIVITY' AS target_type, a.id,a.title,a.description,a.location,a.start_time,a.end_time,a.cover_url,
                    a.max_participants,NULL AS activity_id,NULL AS activity_title,a.organizer_id AS creator_id,u.nickname AS creator_name,
                    0 AS joined,0 AS pending,
                    (SELECT COUNT(*) FROM registrations r WHERE r.activity_id=a.id AND r.status='APPROVED') AS participants,
                    COALESCE(f.weight,0) AS featured_weight,
                    CASE WHEN a.title=? THEN 100 WHEN a.title LIKE ? THEN 50 ELSE 12 END + COALESCE(f.weight,0) AS score
                FROM activities a JOIN users u ON u.id=a.organizer_id
                LEFT JOIN ops_featured_item f ON f.target_type='ACTIVITY' AND f.target_id=a.id AND f.status='ACTIVE'
                    AND (f.start_time IS NULL OR f.start_time<=CURRENT_TIMESTAMP) AND (f.end_time IS NULL OR f.end_time>CURRENT_TIMESTAMP)
                WHERE a.status='PUBLISHED' AND a.audit_status='APPROVED' AND COALESCE(a.end_time,a.start_time)>CURRENT_TIMESTAMP
                  AND (a.title LIKE ? OR a.description LIKE ? OR a.location LIKE ? OR EXISTS(
                    SELECT 1 FROM activity_tag at2 JOIN tags tg ON tg.id=at2.tag_id WHERE at2.activity_id=a.id AND %s) OR %s)
                """.formatted(tagMatch.sql(), translated.sql()));
            Collections.addAll(args,kw,like,like,like,like);
            args.addAll(tagMatch.parameters());
            args.addAll(translated.parameters());
        }
        if (!"activity".equals(kind)) {
            var translated = com.campuspulse.content.ContentSearch.teams(kw);
            var activityTitles = com.campuspulse.content.ContentSearch.activityTitles(kw);
            selects.add("""
                SELECT 'TEAM' AS target_type,t.id,t.title,t.description,NULL AS location,t.start_time,t.end_time,NULL AS cover_url,
                    t.max_members AS max_participants,t.activity_id,a.title AS activity_title,t.creator_id,u.nickname AS creator_name,
                    EXISTS(SELECT 1 FROM team_member tm WHERE tm.team_id=t.id AND tm.user_id=? AND tm.status='ACTIVE') AS joined,
                    EXISTS(SELECT 1 FROM team_join_request tr WHERE tr.team_id=t.id AND tr.user_id=? AND tr.status='PENDING') AS pending,
                    (SELECT COUNT(*) FROM team_member tm WHERE tm.team_id=t.id AND tm.status='ACTIVE') AS participants,
                    COALESCE(f.weight,0) AS featured_weight,
                    CASE WHEN t.title=? THEN 100 WHEN t.title LIKE ? THEN 50 ELSE 12 END + COALESCE(f.weight,0) AS score
                FROM teams t JOIN users u ON u.id=t.creator_id LEFT JOIN activities a ON a.id=t.activity_id
                LEFT JOIN ops_featured_item f ON f.target_type='TEAM' AND f.target_id=t.id AND f.status='ACTIVE'
                    AND (f.start_time IS NULL OR f.start_time<=CURRENT_TIMESTAMP) AND (f.end_time IS NULL OR f.end_time>CURRENT_TIMESTAMP)
                WHERE t.status='OPEN' AND (COALESCE(t.end_time,t.start_time) IS NULL OR COALESCE(t.end_time,t.start_time)>CURRENT_TIMESTAMP)
                  AND (t.activity_id IS NULL OR (a.status='PUBLISHED' AND a.audit_status='APPROVED' AND a.teaming_enabled=1 AND COALESCE(a.end_time,a.start_time)>CURRENT_TIMESTAMP))
                  AND (t.title LIKE ? OR t.description LIKE ? OR a.title LIKE ? OR u.nickname LIKE ? OR EXISTS(
                    SELECT 1 FROM team_tag tt JOIN tags tg ON tg.id=tt.tag_id WHERE tt.team_id=t.id AND %s) OR %s OR %s)
                """.formatted(tagMatch.sql(), translated.sql(), activityTitles.sql()));
            Collections.addAll(args,uid,uid,kw,like,like,like,like,like);
            args.addAll(tagMatch.parameters());
            args.addAll(translated.parameters());
            args.addAll(activityTitles.parameters());
        }
        String union=String.join(" UNION ALL ",selects);
        long total=Optional.ofNullable(db.queryForObject("SELECT COUNT(*) FROM ("+union+") result",Long.class,args.toArray())).orElse(0L);
        args.add(limit);args.add((p-1)*limit);
        var rows=db.queryForList("SELECT * FROM ("+union+") result ORDER BY score DESC,target_type,id LIMIT ? OFFSET ?",args.toArray());
        Set<Long> activityIds=new HashSet<>();
        for(var row:rows) {
            if ("ACTIVITY".equals(row.get("target_type"))) activityIds.add(number(row.get("id")).longValue());
            else if(row.get("activity_id")!=null) activityIds.add(number(row.get("activity_id")).longValue());
        }
        var tagMap=activities.tags(activityIds);
        var teamIds=rows.stream().filter(r -> "TEAM".equals(r.get("target_type"))).map(r -> number(r.get("id")).longValue()).toList();
        Map<Long,List<String>> teamTags=new HashMap<>();
        if(!teamIds.isEmpty()) for(var row:db.queryForList("SELECT tt.team_id,t.name FROM team_tag tt JOIN tags t ON t.id=tt.tag_id WHERE tt.team_id IN ("+String.join(",",Collections.nCopies(teamIds.size(),"?"))+") ORDER BY t.name",teamIds.toArray()))
            teamTags.computeIfAbsent(number(row.get("team_id")).longValue(),k->new ArrayList<>()).add(String.valueOf(row.get("name")));
        List<SearchItem> items=new ArrayList<>();
        for(var row:rows) {
            long id=number(row.get("id")).longValue();
            boolean activity="ACTIVITY".equals(row.get("target_type")), joined=number(row.get("joined")).intValue()>0;
            Long aid=activity?null:row.get("activity_id")==null?null:number(row.get("activity_id")).longValue();
            Set<String> tags=new LinkedHashSet<>(tagMap.getOrDefault(activity?Long.valueOf(id):aid,List.of()));
            if(!activity) tags.addAll(teamTags.getOrDefault(id,List.of()));
            items.add(new SearchItem(String.valueOf(row.get("target_type")),id,(String)row.get("title"),(String)row.get("description"),(String)row.get("location"),date(row.get("start_time")),date(row.get("end_time")),(String)row.get("cover_url"),number(row.get("participants")).intValue(),number(row.get("max_participants")).intValue(),aid,(String)row.get("activity_title"),number(row.get("creator_id")).longValue(),(String)row.get("creator_name"),joined,!joined&&number(row.get("pending")).intValue()>0,new ArrayList<>(tags),number(row.get("featured_weight")).intValue()>0,"关键词匹配",number(row.get("score")).doubleValue()));
        }
        return Api.ok(new SearchPage(items,total,p,limit));
    }
    private static Number number(Object o) {return o instanceof Number n?n:0;}
    private static String date(Object o) {return o==null?null:o instanceof Timestamp t?t.toLocalDateTime().toString():o.toString().replace(' ','T');}
    public record SearchPage(List<SearchItem> items, long total, int page, int size) {
    }

    @com.campuspulse.content.ContentEntity
    public record SearchItem(String targetType, long id, String title, String description, String location,
                             String startTime, String endTime, String coverUrl, int participants, int maxParticipants,
                             Long activityId, String activityTitle, Long creatorId, String creatorName,
                             boolean joined, boolean pending, List<String> tags, boolean promoted,
                             String reason, double score) {
    }
}
