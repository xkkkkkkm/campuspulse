package com.campuspulse.behavior;

import com.campuspulse.common.Api;
import com.campuspulse.security.RequestRateLimiter;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.Locale;
import java.util.Set;

/** Client observations are untrusted; conversions are written by business transactions only. */
@Service
public class BehaviorService {
    private static final Set<String> OBSERVATIONS=Set.of("IMPRESSION","CLICK","DETAIL_VIEW","SEARCH_CLICK","FEATURED_IMPRESSION","FEATURED_CLICK");
    private final JdbcTemplate db;
    private final RequestRateLimiter limiter;
    public BehaviorService(JdbcTemplate db, RequestRateLimiter limiter) {this.db=db;this.limiter=limiter;}
    public void log(long uid, BehaviorController.BehaviorRequest r) {
        if(r==null) throw bad();
        String type=normalize(r.targetType()), event=normalize(r.eventType());
        if("SEARCH".equals(type)) {
            if(!"SEARCH".equals(event) || r.targetId()!=null || r.query()==null || r.query().isBlank()) throw bad();
        } else {
            if(!Set.of("ACTIVITY","TEAM").contains(type) || !OBSERVATIONS.contains(event) || r.targetId()==null || r.targetId()<1) throw bad();
            String sql="ACTIVITY".equals(type)
                    ? "SELECT COUNT(*) FROM activities WHERE id=? AND status='PUBLISHED' AND audit_status='APPROVED' AND archived_at IS NULL"
                    : "SELECT COUNT(*) FROM teams WHERE id=? AND status='OPEN' AND archived_at IS NULL";
            if(db.queryForObject(sql,Long.class,r.targetId())==0) throw new Api.ApiException(HttpStatus.NOT_FOUND,"目标不存在");
        }
        if(r.rankPosition()!=null && (r.rankPosition()<1 || r.rankPosition()>10000)) throw bad();
        String scene=bounded(r.scene(),64), query=bounded(r.query(),200), request=bounded(r.requestId(),64), extra=bounded(r.extraJson(),2048);
        limiter.require("telemetry:"+uid,240,60);
        db.update("INSERT INTO user_behavior_log(user_id,target_type,target_id,event_type,scene,query_text,request_id,rank_position,extra_json,source) VALUES(?,?,?,?,?,?,?,?,?,'CLIENT')",
                uid,type,r.targetId(),event,scene,query,request,r.rankPosition(),extra);
    }
    private static String normalize(String v) {return v==null?"":v.trim().toUpperCase(Locale.ROOT);}
    private static String bounded(String v,int max) {if(v!=null&&v.length()>max) throw bad(); return v==null||v.isBlank()?null:v.trim();}
    private static Api.ApiException bad() {return new Api.ApiException(HttpStatus.BAD_REQUEST,"不支持的行为或参数过长");}
}
