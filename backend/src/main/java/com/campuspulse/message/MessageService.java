package com.campuspulse.message;

import com.campuspulse.activity.ActivityRepository;
import com.campuspulse.team.TeamRepository;
import com.campuspulse.common.Api;
import com.campuspulse.realtime.ChatRealtimeService;
import com.campuspulse.security.AuthContext;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.*;

/** Database is the delivery log; SSE is a post-commit hint, and clients recover by message ID. */
@Service
public class MessageService {
    private final JdbcTemplate db;
    private final ActivityRepository activities;
    private final TeamRepository teams;
    private final ChatRealtimeService realtime;
    public MessageService(JdbcTemplate db, ActivityRepository activities, TeamRepository teams, ChatRealtimeService realtime) {
        this.db=db; this.activities=activities; this.teams=teams; this.realtime=realtime;
    }
    public enum Channel {
        TEAM("messages", "team_id"), ACTIVITY("activity_chat_message", "activity_id"), DIRECT("dm_message", "receiver_id");
        final String table, scope;
        Channel(String table, String scope) {this.table=table;this.scope=scope;}
    }
    @Transactional
    public List<Map<String,Object>> list(Channel channel, long target, Long afterId, Long beforeId, int size) {
        long user = authorize(channel,target,false);
        if (afterId != null && beforeId != null || afterId != null && afterId < 0 || beforeId != null && beforeId <= 0)
            throw new Api.ApiException(HttpStatus.BAD_REQUEST,"消息游标不合法");
        List<Object> args = new ArrayList<>();
        String filter = filter(channel,target,user,args);
        // Missing cursor returns the latest page; all pages are returned chronologically.
        boolean forward = afterId != null;
        if (afterId != null) { filter += " AND m.id > ?"; args.add(afterId); }
        if (beforeId != null) { filter += " AND m.id < ?"; args.add(beforeId); }
        args.add(Math.min(Math.max(size,1),100));
        String reads=channel==Channel.DIRECT ? "(SELECT COUNT(*) FROM dm_read_state rs WHERE rs.user_id=m.receiver_id AND rs.peer_id=m.sender_id AND rs.last_read_id>=m.id)"
                : "(SELECT COUNT(*) FROM group_read_state rs WHERE rs.channel='"+channel.name()+"' AND rs.target_id=m."+channel.scope+" AND rs.user_id<>m.sender_id AND rs.last_read_id>=m.id)";
        var rows = new ArrayList<>(db.queryForList("SELECT m.*,u.nickname,"+reads+" AS read_count FROM "+channel.table+" m JOIN users u ON u.id=m.sender_id WHERE "+filter+" ORDER BY m.id "+(forward?"ASC":"DESC")+" LIMIT ?",args.toArray()));
        if (!forward) Collections.reverse(rows);
        return rows;
    }
    @Transactional
    public long send(Channel channel,long target,String content,String clientMessageId) {
        return send(channel,target,content,clientMessageId,"TEXT",null);
    }
    @Transactional
    public long send(Channel channel,long target,String content,String clientMessageId,String contentType,String imageUrl) {
        long user=authorize(channel,target,true);
        String type=contentType==null?"TEXT":contentType.toUpperCase(Locale.ROOT);
        if(!Set.of("TEXT","IMAGE").contains(type)) throw new Api.ApiException(HttpStatus.BAD_REQUEST,"Unsupported message type");
        if("TEXT".equals(type) && (content==null || content.isBlank() || content.trim().length()>1000 || imageUrl!=null)) throw new Api.ApiException(HttpStatus.BAD_REQUEST,"消息内容不能为空且长度不超过 1000");
        if("IMAGE".equals(type)) {
            if(imageUrl==null || !imageUrl.matches("/api/chat-media/[a-f0-9]{32}") || content!=null && content.length()>1000) throw new Api.ApiException(HttpStatus.BAD_REQUEST,"Invalid chat image");
            var owned=db.queryForList("SELECT id FROM uploaded_file WHERE user_id=? AND kind='chat' AND url=? FOR UPDATE",user,imageUrl);
            if(owned.size()!=1) throw new Api.ApiException(HttpStatus.FORBIDDEN,"Only your own chat uploads may be attached");
        }
        if (clientMessageId!=null && !clientMessageId.matches("[A-Za-z0-9_-]{8,80}")) throw new Api.ApiException(HttpStatus.BAD_REQUEST,"消息幂等标识不合法");
        String normalized=content==null?"":content.trim();
        if (clientMessageId == null) clientMessageId=UUID.randomUUID().toString();
        db.update("INSERT INTO "+channel.table+" ("+channel.scope+",sender_id,content,client_message_id,content_type,image_url) VALUES (?,?,?,?,?,?) ON DUPLICATE KEY UPDATE id=LAST_INSERT_ID(id)",target,user,normalized,clientMessageId,type,imageUrl);
        var row=db.queryForMap("SELECT id,"+channel.scope+",content,content_type,image_url FROM "+channel.table+" WHERE sender_id=? AND client_message_id=? FOR UPDATE",user,clientMessageId);
        if (((Number)row.get(channel.scope)).longValue()!=target || !normalized.equals(row.get("content")) || !type.equals(row.get("content_type")) || !Objects.equals(imageUrl,row.get("image_url"))) throw new Api.ApiException(HttpStatus.CONFLICT,"此消息标识已被其他内容使用");
        long id=((Number)row.get("id")).longValue();
        final Runnable publish=() -> {
            switch(channel) {
                case TEAM -> realtime.notifyTeamMessage(target,id);
                case ACTIVITY -> realtime.notifyActivityMessage(target,id);
                case DIRECT -> realtime.notifyDirectMessage(user,target,id);
            }
        };
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { try { publish.run(); } catch (RuntimeException e) { org.slf4j.LoggerFactory.getLogger(MessageService.class).warn("Realtime hint failed; message remains available by cursor id={}", id, e); } }
        });
        return id;
    }
    private long authorize(Channel channel,long target,boolean sending) {
        var user=AuthContext.requireUser();
        if (target<=0) throw new Api.ApiException(HttpStatus.BAD_REQUEST,"参数错误");
        if (channel==Channel.TEAM) {
            var team=teams.lock(target);
            teams.requireLive(team);
            var members=db.queryForList("SELECT user_id FROM team_member WHERE team_id=? AND user_id=? AND status='ACTIVE' FOR UPDATE",Long.class,target,user.id());
            if (members.isEmpty()) throw new Api.ApiException(HttpStatus.FORBIDDEN,"仅队伍成员可查看或发送消息");
        } else if (channel==Channel.ACTIVITY) {
            var activity=activities.lock(target);
            ActivityRepository.requireLive(activity);
            if (((Number)activity.get("chat_enabled")).intValue()!=1) throw new Api.ApiException(HttpStatus.CONFLICT,"群聊未开启");
            if (((Number)activity.get("organizer_id")).longValue()!=user.id() && !"ADMIN".equals(user.role())) {
                var members=db.queryForList("SELECT id FROM registrations WHERE activity_id=? AND user_id=? AND status='APPROVED' FOR UPDATE",Long.class,target,user.id());
                if (members.isEmpty()) throw new Api.ApiException(HttpStatus.FORBIDDEN,"报名通过后才可进入活动群聊");
            }
        } else {
            if (target==user.id()) throw new Api.ApiException(HttpStatus.BAD_REQUEST,"不能给自己发送私信");
            var peers=db.queryForList("SELECT id FROM users WHERE id=? AND status=1",Long.class,target);
            if (peers.isEmpty()) throw new Api.ApiException(HttpStatus.NOT_FOUND,"对方用户不存在");
        }
        if(channel==Channel.DIRECT && sending) {
            long low=Math.min(user.id(),target), high=Math.max(user.id(),target);
            db.update("INSERT IGNORE INTO direct_message_guard(low_id,high_id) VALUES (?,?)",low,high);
            db.queryForMap("SELECT low_id FROM direct_message_guard WHERE low_id=? AND high_id=? FOR UPDATE",low,high);
        }
        return user.id();
    }
    @Transactional
    public void markRead(Channel channel,long target,long lastReadId) {
        long uid=authorize(channel,target,false);
        if(channel==Channel.DIRECT) throw new Api.ApiException(HttpStatus.BAD_REQUEST,"Use the direct read endpoint");
        long maximum=db.queryForObject("SELECT COALESCE(MAX(id),0) FROM "+channel.table+" WHERE "+channel.scope+"=?",Long.class,target);
        db.update("INSERT INTO group_read_state(channel,target_id,user_id,last_read_id) VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE last_read_id=GREATEST(last_read_id,VALUES(last_read_id))",
                channel.name(),target,uid,Math.min(Math.max(lastReadId,0),maximum));
    }
    @Transactional
    public void requireMediaAccess(String id) {
        long uid=AuthContext.requireUser().id();
        var files=db.queryForList("SELECT user_id FROM uploaded_file WHERE id=? AND kind='chat'",Long.class,id);
        if(files.isEmpty()) throw new Api.ApiException(HttpStatus.NOT_FOUND,"Image not found");
        if(files.get(0)==uid) return;
        String url="/api/chat-media/"+id;
        if(db.queryForObject("SELECT COUNT(*) FROM dm_message WHERE image_url=? AND (sender_id=? OR receiver_id=?)",Long.class,url,uid,uid)>0) return;
        for(Channel channel:List.of(Channel.TEAM,Channel.ACTIVITY)) {
            for(long target:db.queryForList("SELECT DISTINCT "+channel.scope+" FROM "+channel.table+" WHERE image_url=?",Long.class,url)) {
                try {authorize(channel,target,false);return;} catch(Api.ApiException ignored) { }
            }
        }
        throw new Api.ApiException(HttpStatus.FORBIDDEN,"This image belongs to another conversation");
    }
    private static String filter(Channel channel,long target,long user,List<Object> args) {
        if (channel!=Channel.DIRECT) { args.add(target); return "m."+channel.scope+"=?"; }
        Collections.addAll(args,user,target,target,user);
        return "((m.sender_id=? AND m.receiver_id=?) OR (m.sender_id=? AND m.receiver_id=?))";
    }
}
