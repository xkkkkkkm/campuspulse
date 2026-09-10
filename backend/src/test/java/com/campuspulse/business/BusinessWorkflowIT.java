package com.campuspulse.business;

import com.campuspulse.activity.*;
import com.campuspulse.team.*;
import com.campuspulse.admin.AdminController;
import com.campuspulse.profile.ProfileController;
import com.campuspulse.message.MessageService;
import com.campuspulse.search.SearchController;
import com.campuspulse.common.Api;
import com.campuspulse.security.AuthContext;
import com.campuspulse.realtime.ChatRealtimeService;
import com.campuspulse.upload.UploadService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@Testcontainers
class BusinessWorkflowIT {
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>(System.getenv().getOrDefault("TEST_MYSQL_IMAGE", "mysql:8.0.36"));
    static AnnotationConfigApplicationContext context;
    static JdbcTemplate db;
    static ActivityService activities;
    static TeamService teams;
    static MessageService messages;
    static AdminController admin;
    static SearchController search;
    static ProfileController profile;
    static final AtomicLong sequence=new AtomicLong();
    long owner,u1,u2;

    @Configuration @EnableTransactionManagement
    static class Config {
        @Bean DataSource dataSource() {return new DriverManagerDataSource(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword());}
        @Bean JdbcTemplate jdbc(DataSource ds) {return new JdbcTemplate(ds);}
        @Bean PlatformTransactionManager manager(DataSource ds) {return new DataSourceTransactionManager(ds);}
        @Bean ActivityRepository activityRepository(JdbcTemplate db) {return new ActivityRepository(db);}
        @Bean TeamRepository teamRepository(JdbcTemplate db,ActivityRepository a) {return new TeamRepository(db,a);}
        @Bean UploadService uploads() {return mock(UploadService.class);}
        @Bean ChatRealtimeService realtime() {return mock(ChatRealtimeService.class);}
        @Bean ActivityService activity(JdbcTemplate db,ActivityRepository a,UploadService u) {return new ActivityService(db,a,u);}
        @Bean TeamService team(JdbcTemplate db,TeamRepository t,ActivityRepository a) {return new TeamService(db,t,a);}
        @Bean MessageService messages(JdbcTemplate db,ActivityRepository a,TeamRepository t,ChatRealtimeService r) {return new MessageService(db,a,t,r);}
        @Bean AdminController admin(JdbcTemplate db,ActivityRepository a) {return new AdminController(db,a);}
        @Bean SearchController search(JdbcTemplate db,ActivityRepository a) {return new SearchController(db,a);}
        @Bean ProfileController profile(JdbcTemplate db,UploadService uploads) {return new ProfileController(db,uploads);}
    }
    @BeforeAll static void setup() {
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword()).load().migrate();
        context=new AnnotationConfigApplicationContext(Config.class);
        db=context.getBean(JdbcTemplate.class);activities=context.getBean(ActivityService.class);teams=context.getBean(TeamService.class);
        messages=context.getBean(MessageService.class);admin=context.getBean(AdminController.class);search=context.getBean(SearchController.class);profile=context.getBean(ProfileController.class);
        assertEquals("REPEATABLE-READ",db.queryForObject("SELECT @@transaction_isolation",String.class));
    }
    @AfterAll static void close() {if(context!=null) context.close();}
    @BeforeEach void users() {owner=user("USER");u1=user("USER");u2=user("USER");}
    @AfterEach void auth() {AuthContext.clear();}
    static long user(String role) {
        String name="business-"+sequence.incrementAndGet();
        db.update("INSERT INTO users(username,password_hash,nickname,role) VALUES (?,'test',?,?)",name,name,role);
        return db.queryForObject("SELECT id FROM users WHERE username=?",Long.class,name);
    }
    long activity(int capacity) {
        String title="activity-"+sequence.incrementAndGet();
        db.update("INSERT INTO activities(title,location,organizer_id,start_time,end_time,max_participants,audit_status,chat_enabled) VALUES (?,'campus',?,DATE_ADD(NOW(),INTERVAL 2 DAY),DATE_ADD(NOW(),INTERVAL 3 DAY),?,'APPROVED',1)",title,owner,capacity);
        return db.queryForObject("SELECT id FROM activities WHERE title=?",Long.class,title);
    }
    long team(int capacity,Long activity) {
        String title="team-"+sequence.incrementAndGet();
        db.update("INSERT INTO teams(title,creator_id,max_members,activity_id,end_time) VALUES (?,?,?,?,DATE_ADD(NOW(),INTERVAL 3 DAY))",title,owner,capacity,activity);
        long id=db.queryForObject("SELECT id FROM teams WHERE title=?",Long.class,title);
        db.update("INSERT INTO team_member(team_id,user_id,role,status) VALUES (?,?,'CREATOR','ACTIVE')",id,owner);return id;
    }
    long registration(long activity,long user) {
        db.update("INSERT INTO registrations(activity_id,user_id,real_name,phone,college,status) VALUES (?,?,'Tester','123','Test','APPLIED')",activity,user);
        return db.queryForObject("SELECT id FROM registrations WHERE activity_id=? AND user_id=?",Long.class,activity,user);
    }
    long request(long team,long user) {
        db.update("INSERT INTO team_join_request(team_id,user_id,status) VALUES (?,?,'PENDING')",team,user);
        return db.queryForObject("SELECT id FROM team_join_request WHERE team_id=? AND user_id=?",Long.class,team,user);
    }
    static void as(long user,String role,Runnable work) {AuthContext.set(new AuthContext.User(user,"test",role));try{work.run();}finally{AuthContext.clear();}}
    static List<Boolean> race(Runnable one,Runnable two) throws Exception {
        var pool=Executors.newFixedThreadPool(2);var ready=new CountDownLatch(2);var go=new CountDownLatch(1);
        try {
            List<Future<Boolean>> futures=new ArrayList<>();
            for(var work:List.of(one,two)) futures.add(pool.submit(()-> {ready.countDown();go.await();try {work.run();return true;}catch(Api.ApiException conflict){return false;}}));
            assertTrue(ready.await(5,TimeUnit.SECONDS));go.countDown();
            return List.of(futures.get(0).get(15,TimeUnit.SECONDS),futures.get(1).get(15,TimeUnit.SECONDS));
        } finally {pool.shutdownNow();}
    }

    @Test void concurrentFinalActivitySeatUsesCurrentReadAtRepeatableRead() throws Exception {
        long a=activity(1),r1=registration(a,u1),r2=registration(a,u2);
        var result=race(()->as(owner,"USER",()->activities.approveRegistration(a,r1)),()->as(owner,"USER",()->activities.approveRegistration(a,r2)));
        assertEquals(1,result.stream().filter(Boolean::booleanValue).count());
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM registrations WHERE activity_id=? AND status='APPROVED'",Integer.class,a));
    }
    @Test void concurrentFinalTeamSeatCannotOversubscribe() throws Exception {
        long t=team(2,null),r1=request(t,u1),r2=request(t,u2);
        var result=race(()->as(owner,"USER",()->teams.approve(t,r1)),()->as(owner,"USER",()->teams.approve(t,r2)));
        assertEquals(1,result.stream().filter(Boolean::booleanValue).count());
        assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM team_member WHERE team_id=? AND status='ACTIVE'",Integer.class,t));
    }
    @Test void approvalAndCancellationCannotLeaveCancelledMember() throws Exception {
        long t=team(2,null),r=request(t,u1);
        race(()->as(owner,"USER",()->teams.approve(t,r)),()->as(u1,"USER",()->teams.cancelJoinRequest(t)));
        String status=db.queryForObject("SELECT status FROM team_join_request WHERE id=?",String.class,r);
        int active=db.queryForObject("SELECT COUNT(*) FROM team_member WHERE team_id=? AND user_id=? AND status='ACTIVE'",Integer.class,t,u1);
        assertEquals("APPROVED".equals(status)?1:0,active);
        assertTrue(Set.of("APPROVED","CANCELLED").contains(status));
    }
    @Test void shrinkAndApproveShareCapacityLock() throws Exception {
        long t=team(3,null),r1=request(t,u1),r2=request(t,u2);
        as(owner,"USER",()->teams.approve(t,r1));
        race(()->as(owner,"USER",()->teams.approve(t,r2)),()->as(owner,"USER",()->teams.update(t,new TeamService.UpdateTeamRequest("resize",null,2,null,LocalDateTime.now().plusDays(3),null,null))));
        int max=db.queryForObject("SELECT max_members FROM teams WHERE id=?",Integer.class,t);
        int count=db.queryForObject("SELECT COUNT(*) FROM team_member WHERE team_id=? AND status='ACTIVE'",Integer.class,t);
        assertTrue(count<=max);
    }
    @Test void notificationFailureRollsBackApprovalAndMembership() {
        long t=team(2,null),r=request(t,u1);
        db.execute("ALTER TABLE notifications ADD CONSTRAINT fail_business_notification CHECK (user_id <> " + u1 + ")");
        try {assertThrows(RuntimeException.class,()->as(owner,"USER",()->teams.approve(t,r)));}
        finally {db.execute("ALTER TABLE notifications DROP CHECK fail_business_notification");}
        assertEquals("PENDING",db.queryForObject("SELECT status FROM team_join_request WHERE id=?",String.class,r));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM team_member WHERE team_id=? AND user_id=?",Integer.class,t,u1));
    }
    @Test void transferLeaveReapplyAndCloseKeepMembershipConsistent() {
        long t=team(3,null),r=request(t,u1);
        as(owner,"USER",()->teams.approve(t,r));
        as(owner,"USER",()->teams.transfer(t,new TeamService.TransferRequest(u1)));
        as(owner,"USER",()->teams.leave(t));
        assertEquals(u1,db.queryForObject("SELECT creator_id FROM teams WHERE id=?",Long.class,t));
        assertEquals("LEFT",db.queryForObject("SELECT status FROM team_member WHERE team_id=? AND user_id=?",String.class,t,owner));
        as(owner,"USER",()->teams.requestJoin(t,new TeamService.JoinRequest("again")));
        as(u1,"USER",()->teams.close(t));
        assertEquals("CANCELLED",db.queryForObject("SELECT status FROM team_join_request WHERE team_id=? AND user_id=?",String.class,t,owner));
        assertThrows(Api.ApiException.class,()->as(u2,"USER",()->teams.requestJoin(t,new TeamService.JoinRequest("closed"))));
    }
    @Test void archivedActivityRetainsHistoryAndBlocksLinkedTeamApprovalAndChat() {
        long a=activity(10),t=team(3,a),r=request(t,u1);registration(a,u2);
        db.update("INSERT INTO messages(team_id,sender_id,content) VALUES (?,?,'history')",t,owner);
        as(owner,"USER",()->activities.delete(a));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM registrations WHERE activity_id=?",Integer.class,a));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM messages WHERE team_id=?",Integer.class,t));
        assertThrows(Api.ApiException.class,()->as(owner,"USER",()->teams.approve(t,r)));
        assertThrows(Api.ApiException.class,()->as(owner,"USER",()->messages.send(MessageService.Channel.TEAM,t,"closed",null)));
    }
    @Test void removedMemberLosesChatAndCanReapply() {
        long t=team(3,null),r=request(t,u1);
        as(owner,"USER",()->teams.approve(t,r));as(owner,"USER",()->teams.removeMember(t,u1));
        assertEquals("REJECTED",db.queryForObject("SELECT status FROM team_join_request WHERE id=?",String.class,r));
        assertThrows(Api.ApiException.class,()->as(u1,"USER",()->messages.list(MessageService.Channel.TEAM,t,null,null,50)));
        as(u1,"USER",()->teams.requestJoin(t,new TeamService.JoinRequest("retry")));
        assertEquals("PENDING",db.queryForObject("SELECT status FROM team_join_request WHERE id=?",String.class,r));
    }
    @Test void initialMessagesAreLatestHistoryAndCatchupAreStableAndSendingIsIdempotent() {
        long t=team(2,null);
        for(int i=0;i<125;i++) db.update("INSERT INTO messages(team_id,sender_id,content) VALUES (?,?,?)",t,owner,"m"+i);
        as(owner,"USER",()->{
            var latest=messages.list(MessageService.Channel.TEAM,t,null,null,50);
            assertEquals("m75",latest.get(0).get("content"));assertEquals("m124",latest.get(49).get("content"));
            long oldest=((Number)latest.get(0).get("id")).longValue(),last=((Number)latest.get(49).get("id")).longValue();
            var history=messages.list(MessageService.Channel.TEAM,t,null,oldest,50);
            assertEquals("m25",history.get(0).get("content"));assertEquals("m74",history.get(49).get("content"));
            long first=messages.send(MessageService.Channel.TEAM,t,"hello","same-message-123");
            assertEquals(first,messages.send(MessageService.Channel.TEAM,t,"hello","same-message-123"));
            assertThrows(Api.ApiException.class,()->messages.send(MessageService.Channel.TEAM,t,"different","same-message-123"));
            assertEquals(1,messages.list(MessageService.Channel.TEAM,t,last,null,100).size());
        });
    }
    @Test void concurrentDirectMessageRetriesCreateOneRow() throws Exception {
        race(()->as(u1,"USER",()->messages.send(MessageService.Channel.DIRECT,u2,"hello","concurrent-message-123")),()->as(u1,"USER",()->messages.send(MessageService.Channel.DIRECT,u2,"hello","concurrent-message-123")));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM dm_message WHERE sender_id=?",Integer.class,u1));
    }
    @Test void publicDiscoveryAndSearchCountPastOriginalCandidateLimits() {
        String marker="search-"+sequence.incrementAndGet();
        for(int i=0;i<135;i++) db.update("INSERT INTO activities(title,location,organizer_id,start_time,end_time,audit_status) VALUES (?,'campus',?,DATE_ADD(NOW(),INTERVAL 2 DAY),DATE_ADD(NOW(),INTERVAL 3 DAY),'APPROVED')",marker+"-"+i,owner);
        var page=activities.list(marker,null,null,"upcoming","time",3,50).data();
        assertEquals(135,page.total());assertEquals(35,page.items().size());
        var results=search.search(marker,"activity",5,30).data();
        assertEquals(135,results.total());assertEquals(15,results.items().size());
    }
    @Test void standaloneTeamSearchSupportsNullActivityAndExplicitTags() {
        long t=team(3,null);
        String title=db.queryForObject("SELECT title FROM teams WHERE id=?",String.class,t);
        var results=search.search(title,"team",1,10).data();
        assertEquals(1,results.total());
        assertNull(results.items().get(0).activityId());
    }
    @Test void staleEditVersionRejectedAndAuditRecordsRealAdminActions() {
        long a=activity(10),administrator=user("ADMIN");
        as(administrator,"ADMIN",()->admin.auditActivity(a,new AdminController.AuditActivityRequest("APPROVED","reviewed",0L)));
        assertThrows(Api.ApiException.class,()->as(administrator,"ADMIN",()->admin.auditActivity(a,new AdminController.AuditActivityRequest("REJECTED","stale",0L))));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM admin_audit_log WHERE target_id=? AND action='ACTIVITY_AUDIT'",Integer.class,a));
        db.update("UPDATE users SET role='USER' WHERE id=?",administrator);
    }
    @Test void lastActiveAdminCannotBeDemoted() {
        long administrator=user("ADMIN");
        assertThrows(Api.ApiException.class,()->as(administrator,"ADMIN",()->admin.updateUserRole(administrator,new AdminController.RoleRequest("USER","test"))));
        assertEquals("ADMIN",db.queryForObject("SELECT role FROM users WHERE id=?",String.class,administrator));
        db.update("UPDATE users SET role='USER' WHERE id=?",administrator);
    }
    @Test void historicFavoritesCanBeRemovedButCannotBeAdded() {
        for(String lifecycle:List.of("PUBLISHED","CANCELLED","ARCHIVED")) {
            long a=activity(10);
            db.update("UPDATE activities SET status=?,start_time=DATE_SUB(NOW(),INTERVAL 2 DAY),end_time=DATE_SUB(NOW(),INTERVAL 1 DAY) WHERE id=?",lifecycle,a);
            db.update("INSERT INTO favorites(user_id,activity_id) VALUES (?,?)",u1,a);
            as(u1,"USER",()-> {
                assertEquals(false,activities.toggleFavorite(a,false).data().get("favorited"));
                assertEquals(false,activities.toggleFavorite(a,false).data().get("favorited"));
                assertThrows(Api.ApiException.class,()->activities.toggleFavorite(a,true));
            });
            assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM favorites WHERE user_id=? AND activity_id=?",Integer.class,u1,a));
        }
    }

    @Test void registrationIntroductionHonorsDatabaseBoundary() {
        long a=activity(10);
        as(u1,"USER",()->activities.register(a,new ActivityService.RegisterActivityRequest("Name","123","College","a".repeat(512))));
        assertEquals(512,db.queryForObject("SELECT CHAR_LENGTH(intro) FROM registrations WHERE activity_id=? AND user_id=?",Integer.class,a,u1));
        var error=assertThrows(Api.ApiException.class,()->as(u2,"USER",()->activities.register(a,new ActivityService.RegisterActivityRequest("Name","123","College","a".repeat(513)))));
        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST,error.status());
    }

    @Test void nullAvatarAndTagTypeReturnBadRequest() {
        Map<String,String> input=new HashMap<>();input.put("avatarUrl",null);
        var avatarError=assertThrows(Api.ApiException.class,()->as(u1,"USER",()->profile.updateAvatar(input)));
        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST,avatarError.status());
        var createError=assertThrows(Api.ApiException.class,()->as(owner,"ADMIN",()->admin.createTag(new AdminController.CreateTagRequest("tag",null))));
        var updateError=assertThrows(Api.ApiException.class,()->as(owner,"ADMIN",()->admin.updateTag(1,new AdminController.CreateTagRequest("tag",null))));
        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST,createError.status());
        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST,updateError.status());
    }

    @Test void longAuditReasonIsPreservedWhileNotificationFitsItsColumn() {
        long a=activity(10);
        db.update("UPDATE activities SET title=? WHERE id=?","活动".repeat(100),a);
        String reason="审核原因".repeat(250);
        as(owner,"ADMIN",()-> {
            admin.auditActivity(a,new AdminController.AuditActivityRequest("APPROVED",reason,null));
            admin.updateActivityStatus(a,new AdminController.UpdateActivityStatusRequest("CANCELLED",reason,null));
        });
        assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM admin_audit_log WHERE target_id=? AND reason=?",Integer.class,a,reason));
        var notices=db.queryForList("SELECT content FROM notifications WHERE user_id=? AND type IN ('ADMIN_ACTIVITY_STATUS','ADMIN_ACTIVITY_AUDIT')",String.class,owner);
        assertEquals(2,notices.size());
        for(String text:notices) {assertTrue(text.codePointCount(0,text.length())<=1000);assertTrue(text.endsWith("…"));}
    }

    @Test void allProfileListsPageBeyondOneHundredWithoutDuplicates() {
        for(int i=0;i<105;i++) {
            long a=activity(500),t=team(3,null);
            db.update("INSERT INTO favorites(user_id,activity_id) VALUES (?,?)",owner,a);
            db.update("INSERT INTO registrations(activity_id,user_id,real_name,phone,college,status) VALUES (?,?,'Name','123','College','APPROVED')",a,owner);
            db.update("INSERT INTO team_member(team_id,user_id,role,status) VALUES (?,?,'MEMBER','ACTIVE')",t,u1);
        }
        as(owner,"USER",()-> {
            assertPagePair(profile.favorites(1,100).data(),profile.favorites(2,100).data());
            assertPagePair(profile.registrations(1,100).data(),profile.registrations(2,100).data());
            assertPagePair(profile.published(1,100).data(),profile.published(2,100).data());
            assertPagePair(profile.myTeams(1,100).data(),profile.myTeams(2,100).data());
            assertPagePair(profile.createdTeams(1,100).data(),profile.createdTeams(2,100).data());
            assertPagePair(profile.teamChats(1,100).data(),profile.teamChats(2,100).data());
            assertPagePair(profile.activityChats(1,100).data(),profile.activityChats(2,100).data());
            assertEquals(100,profile.favorites(1,Integer.MAX_VALUE).data().size());
            assertEquals(profile.favorites().data(),profile.favorites(0,100).data());
        });
        as(u1,"USER",()->assertPagePair(profile.joinedTeams(1,100).data(),profile.joinedTeams(2,100).data()));
    }

    @Test void adminAndOrganizerListsPageBeyondOneHundredWithoutDuplicates() {
        String marker="page-"+sequence.incrementAndGet()+"-";
        long registrationsActivity=activity(500);
        for(int i=0;i<105;i++) {
            String name=marker+i;
            db.update("INSERT INTO users(username,password_hash,nickname) VALUES (?,'test',?)",name,name);
            long uid=db.queryForObject("SELECT id FROM users WHERE username=?",Long.class,name);
            registration(registrationsActivity,uid);
            long a=activity(500);
            db.update("UPDATE activities SET title=? WHERE id=?",name,a);
            db.update("INSERT INTO tags(name,type) VALUES (?,'CATEGORY')",name);
            db.update("INSERT INTO ops_featured_activity(activity_id,weight) VALUES (?,1)",a);
        }
        as(owner,"ADMIN",()-> {
            assertPagePair(admin.users(marker,null,null,1,100).data(),admin.users(marker,null,null,2,100).data());
            assertPagePair(admin.activities(null,null,marker,1,100).data(),admin.activities(null,null,marker,2,100).data());
            assertPagePair(admin.tags(1,100).data(),admin.tags(2,100).data(),db.queryForObject("SELECT COUNT(*) FROM tags",Integer.class));
            assertPagePair(admin.featured(1,100).data(),admin.featured(2,100).data(),db.queryForObject("SELECT COUNT(*) FROM ops_featured_activity",Integer.class));
            assertPagePair(activities.registrationsForOrganizer(registrationsActivity,1,100).data(),activities.registrationsForOrganizer(registrationsActivity,2,100).data());
        });
    }

    private static void assertPagePair(List<?> first,List<?> second) {
        assertPagePair(first,second,105);
    }
    private static void assertPagePair(List<?> first,List<?> second,int total) {
        assertEquals(100,first.size());assertEquals(total-100,second.size());
        Set<Object> all=new HashSet<>(first);all.addAll(second);assertEquals(total,all.size());
    }

}
