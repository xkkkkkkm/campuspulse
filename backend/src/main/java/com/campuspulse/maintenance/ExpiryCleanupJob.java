package com.campuspulse.maintenance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class ExpiryCleanupJob {
    private static final Logger log=LoggerFactory.getLogger(ExpiryCleanupJob.class);
    private final JdbcTemplate db;
    private final TransactionTemplate transaction;
    public ExpiryCleanupJob(JdbcTemplate db,PlatformTransactionManager manager) {this.db=db;this.transaction=new TransactionTemplate(manager);}

    /** Bounded work per tick. Business, chat and audit histories are retained. */
    @Scheduled(fixedDelayString="${app.retention.interval-ms:3600000}",initialDelay=60000)
    public void cleanupExpiredContent() {
        long start=System.nanoTime(); int removed=0;
        try {
            removed+=db.update("DELETE FROM notifications WHERE created_at<DATE_SUB(CURRENT_TIMESTAMP,INTERVAL 6 MONTH) ORDER BY created_at,id LIMIT 500");
            removed+=db.update("DELETE FROM email_code WHERE expires_at<DATE_SUB(CURRENT_TIMESTAMP,INTERVAL 7 DAY) ORDER BY expires_at,id LIMIT 500");
            removed+=db.update("DELETE FROM user_behavior_log WHERE event_time<DATE_SUB(CURRENT_TIMESTAMP,INTERVAL 12 MONTH) ORDER BY event_time,id LIMIT 500");
            removed+=db.update("DELETE FROM event_log WHERE event_time<DATE_SUB(CURRENT_TIMESTAMP,INTERVAL 12 MONTH) ORDER BY event_time,id LIMIT 500");
            removed+=db.update("DELETE FROM activity_reminder_delivery WHERE created_at<DATE_SUB(CURRENT_TIMESTAMP,INTERVAL 12 MONTH) ORDER BY created_at LIMIT 500");
            log.info("retention completed removed={} durationMs={}",removed,(System.nanoTime()-start)/1000000);
        } catch(RuntimeException e) { log.error("retention failed removed={}",removed,e); }
    }

    /** A durable delivery key survives notification deletion and prevents repeat reminders. */
    @Scheduled(fixedDelay=60000,initialDelay=45000)
    public void remindUpcomingActivities() {
        var candidates=db.queryForList("""
            SELECT a.id AS activity_id,r.user_id,a.start_time FROM activities a JOIN registrations r ON r.activity_id=a.id AND r.status='APPROVED'
            LEFT JOIN activity_reminder_delivery d ON d.activity_id=a.id AND d.user_id=r.user_id AND d.start_time=a.start_time
            WHERE a.status='PUBLISHED' AND a.audit_status='APPROVED' AND a.start_time>CURRENT_TIMESTAMP
              AND a.start_time<=DATE_ADD(CURRENT_TIMESTAMP,INTERVAL 24 HOUR) AND d.activity_id IS NULL ORDER BY a.start_time,a.id,r.user_id LIMIT 100
            """);
        for(var item:candidates) {
            try {transaction.executeWithoutResult(tx -> {
                // Lock activity first, then recheck current registration and schedule before emitting.
                var rows=db.queryForList("SELECT title,start_time,status,audit_status FROM activities WHERE id=? FOR UPDATE",item.get("activity_id"));
                if(rows.isEmpty()) return;
                var a=rows.get(0);
                if(!"PUBLISHED".equals(a.get("status"))||!"APPROVED".equals(a.get("audit_status"))||!a.get("start_time").equals(item.get("start_time"))) return;
                var approved=db.queryForList("SELECT id FROM registrations WHERE activity_id=? AND user_id=? AND status='APPROVED' FOR UPDATE",item.get("activity_id"),item.get("user_id"));
                if(approved.isEmpty()) return;
                int inserted=db.update("INSERT IGNORE INTO activity_reminder_delivery (activity_id,user_id,start_time) VALUES (?,?,?)",item.get("activity_id"),item.get("user_id"),item.get("start_time"));
                if(inserted==1) db.update("INSERT INTO notifications (user_id,type,title,content,dedup_key) VALUES (?,'ACTIVITY_REMINDER','活动即将开始',?,?)",item.get("user_id"),"你报名的《"+a.get("title")+"》将在 "+a.get("start_time")+" 开始。","activity:"+item.get("activity_id")+":"+item.get("start_time"));
            });} catch(RuntimeException e) {log.error("activity reminder failed activityId={}",item.get("activity_id"),e);}
        }
    }
}
