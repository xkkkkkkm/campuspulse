package com.campuspulse.infrastructure;

import com.campuspulse.bootstrap.Bootstrap;
import com.campuspulse.bootstrap.ProductionBootstrap;
import com.campuspulse.behavior.BehaviorService;
import com.campuspulse.behavior.BehaviorController.BehaviorRequest;
import com.campuspulse.common.Api;
import com.campuspulse.activity.ActivityRepository;
import com.campuspulse.team.TeamRepository;
import com.campuspulse.message.MessageService;
import com.campuspulse.realtime.ChatRealtimeService;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.mockito.Mockito.mock;
import com.campuspulse.recommend.RecommendService;
import com.campuspulse.security.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class InfrastructureIT {
    @Container static MySQLContainer<?> mysql=new MySQLContainer<>(System.getenv().getOrDefault("TEST_MYSQL_IMAGE","mysql:8.0")).withCommand("--character-set-server=utf8mb4","--collation-server=utf8mb4_unicode_ci");
    JdbcTemplate db; TransactionTemplate tx; Flyway flyway;
    @BeforeEach void setup() {
        var ds=new DriverManagerDataSource(mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword());
        flyway=Flyway.configure().dataSource(ds).cleanDisabled(false).load();
        flyway.clean();flyway.migrate();db=new JdbcTemplate(ds);tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
    }
    @AfterEach void cleanup() {AuthContext.clear();}
    @Test void demoInitializationIsOneTimeAndScoresNeverPretendToBeTrained() {
        var seed=new Bootstrap(db,new PasswordHasher());tx.executeWithoutResult(s->seed.run());
        assertTrue(db.queryForObject("SELECT COUNT(*) FROM activities",Long.class)>0);
        long id=db.queryForObject("SELECT MIN(id) FROM activities",Long.class);
        db.update("UPDATE activities SET title='User saved title',version=77 WHERE id=?",id);
        db.update("UPDATE registrations SET status='CANCELLED' WHERE activity_id=?",id);
        tx.executeWithoutResult(s->seed.run());
        assertEquals("User saved title",db.queryForObject("SELECT title FROM activities WHERE id=?",String.class,id));
        assertEquals(77,db.queryForObject("SELECT version FROM activities WHERE id=?",Integer.class,id));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM user_behavior_log WHERE source!='SYNTHETIC'",Integer.class));
        assertNull(db.queryForObject("SELECT version FROM recommendation_active WHERE id=1",String.class));
        assertFalse(db.queryForList("SELECT metrics_json FROM recommend_model_version",String.class).stream().anyMatch(s->s.contains("0.83")));
    }
    @Test void freshProductionRequiresExplicitAdminAndNeverSeedsDemoAccounts() {
        assertThrows(IllegalStateException.class,()->tx.executeWithoutResult(s->new ProductionBootstrap(db,new PasswordHasher(),"","","").run()));
        String pass="Secure-first-admin-password-2026!";
        var init=new ProductionBootstrap(db,new PasswordHasher(),"first_admin",pass,"admin@example.invalid");
        tx.executeWithoutResult(s->init.run());tx.executeWithoutResult(s->init.run());
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM users",Integer.class));
        assertTrue(new PasswordHasher().matches(pass,db.queryForObject("SELECT password_hash FROM users",String.class)));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM activities",Integer.class));
    }
    @Test void clientCannotForgeConversionsAndOnlyFreshActiveModelIsServed() {
        db.update("INSERT INTO users(username,password_hash,nickname) VALUES ('tester','test','Tester')");
        long uid=db.queryForObject("SELECT id FROM users",Long.class);
        db.update("INSERT INTO activities(title,location,organizer_id,start_time,end_time,audit_status) VALUES ('Sample','Campus',?,NOW()+INTERVAL 2 DAY,NOW()+INTERVAL 3 DAY,'APPROVED')",uid);
        long aid=db.queryForObject("SELECT id FROM activities",Long.class);
        var telemetry=new BehaviorService(db,new RequestRateLimiter());
        assertThrows(Api.ApiException.class,()->telemetry.log(uid,new BehaviorRequest("ACTIVITY",aid,"REGISTER",null,null,null,null,null)));
        telemetry.log(uid,new BehaviorRequest("ACTIVITY",aid,"IMPRESSION",null,null,null,1,null));
        assertEquals("CLIENT",db.queryForObject("SELECT source FROM user_behavior_log",String.class));
        AuthContext.set(new AuthContext.User(uid,"tester","USER"));
        var recommendations=new RecommendService(db);
        db.update("INSERT INTO recommend_activity_score(user_id,activity_id,score,reason,model_version) VALUES (?,?,.99,'Staged release','release1')",uid,aid);
        assertNotEquals("Staged release",recommendations.activities(1).data().get(0).reason());
        db.update("INSERT INTO recommendation_release(version,metrics_json,artifact_sha256,expires_at) VALUES ('release1','{}','hash',NOW()+INTERVAL 1 DAY)");
        db.update("UPDATE recommendation_active SET version='release1' WHERE id=1");
        assertEquals("Staged release",recommendations.activities(1).data().get(0).reason());
        db.update("UPDATE recommendation_release SET expires_at=NOW()-INTERVAL 1 DAY");
        assertNotEquals("Staged release",recommendations.activities(1).data().get(0).reason());
    }
    MessageService messages() {
        var activities=new ActivityRepository(db);
        return new MessageService(db,activities,new TeamRepository(db,activities),mock(ChatRealtimeService.class));
    }
    long user(String name) {
        db.update("INSERT INTO users(username,password_hash,nickname) VALUES (?,'test',?)",name,name);
        return db.queryForObject("SELECT id FROM users WHERE username=?",Long.class,name);
    }
    @Test void privateImageOwnershipMembershipAndReadCursorsAreEnforced() {
        long sender=user("sender"),recipient=user("recipient"),stranger=user("stranger");
        String file="a".repeat(32),url="/api/chat-media/"+file;
        db.update("INSERT INTO uploaded_file(id,user_id,kind,url,size_bytes) VALUES (?,?,'chat',?,1)",file,sender,url);
        var service=messages();AuthContext.set(new AuthContext.User(sender,"sender","USER"));
        long id=tx.execute(s->service.send(MessageService.Channel.DIRECT,recipient,"","image-id-123","IMAGE",url));
        assertEquals(id,tx.execute(s->service.send(MessageService.Channel.DIRECT,recipient,"","image-id-123","IMAGE",url)).longValue());
        AuthContext.set(new AuthContext.User(stranger,"stranger","USER"));
        assertThrows(Api.ApiException.class,()->tx.executeWithoutResult(s->service.requireMediaAccess(file)));
        assertThrows(Api.ApiException.class,()->tx.execute(s->service.send(MessageService.Channel.DIRECT,recipient,"","image-id-234","IMAGE",url)));
        AuthContext.set(new AuthContext.User(recipient,"recipient","USER"));
        tx.executeWithoutResult(s->service.requireMediaAccess(file));
        assertEquals(url,tx.execute(s->service.list(MessageService.Channel.DIRECT,sender,null,null,10)).get(0).get("image_url"));
        db.update("INSERT INTO teams(title,creator_id) VALUES ('Read receipts',?)",sender);
        long team=db.queryForObject("SELECT id FROM teams",Long.class);
        db.update("INSERT INTO team_member(team_id,user_id) VALUES (?,?),(?,?)",team,sender,team,recipient);
        AuthContext.set(new AuthContext.User(sender,"sender","USER"));
        long groupId=tx.execute(s->service.send(MessageService.Channel.TEAM,team,"hello","read-test-123"));
        AuthContext.set(new AuthContext.User(recipient,"recipient","USER"));
        tx.executeWithoutResult(s->service.markRead(MessageService.Channel.TEAM,team,Long.MAX_VALUE));
        tx.executeWithoutResult(s->service.markRead(MessageService.Channel.TEAM,team,0));
        assertEquals(groupId,db.queryForObject("SELECT last_read_id FROM group_read_state",Long.class));
        assertEquals(1,((Number)tx.execute(s->service.list(MessageService.Channel.TEAM,team,null,null,10)).get(0).get("read_count")).intValue());
    }
    @Test void directConversationCannotCommitLargerCursorBeforeEarlierMessage() throws Exception {
        long a=user("first"),b=user("second");var service=messages();
        var allocated=new CountDownLatch(1);var release=new CountDownLatch(1);var attempting=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            Future<Long> first=pool.submit(()-> {AuthContext.set(new AuthContext.User(a,"first","USER"));try{return tx.execute(s->{long id=service.send(MessageService.Channel.DIRECT,b,"first","cursor-first");allocated.countDown();try{assertTrue(release.await(10,TimeUnit.SECONDS));}catch(InterruptedException e){throw new RuntimeException(e);}return id;});}finally{AuthContext.clear();}});
            assertTrue(allocated.await(5,TimeUnit.SECONDS));
            Future<Long> second=pool.submit(()-> {AuthContext.set(new AuthContext.User(b,"second","USER"));attempting.countDown();try{return tx.execute(s->service.send(MessageService.Channel.DIRECT,a,"second","cursor-second"));}finally{AuthContext.clear();}});
            assertTrue(attempting.await(5,TimeUnit.SECONDS));
            assertThrows(TimeoutException.class,()->second.get(300,TimeUnit.MILLISECONDS));
            assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM dm_message",Integer.class));
            release.countDown();assertTrue(second.get(10,TimeUnit.SECONDS)>first.get(10,TimeUnit.SECONDS));
        } finally {release.countDown();pool.shutdownNow();}
    }

    @Test void preFlywayDatabaseIsAdoptedWithoutRewritingSavedRecords() {
        flyway.clean();
        var ds=new DriverManagerDataSource(mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword());
        new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(new org.springframework.core.io.ClassPathResource("db/migration/V1__baseline.sql")).execute(ds);
        db.update("ALTER TABLE users DROP COLUMN campus,DROP COLUMN major,DROP COLUMN education_level,DROP COLUMN bio,DROP COLUMN email_verified,DROP COLUMN phone_verified,DROP COLUMN failed_login_count,DROP COLUMN lock_until");
        db.update("ALTER TABLE activities DROP COLUMN chat_enabled,DROP COLUMN teaming_enabled,DROP COLUMN version,DROP COLUMN audit_status");
        db.update("INSERT INTO users(username,password_hash,nickname,role) VALUES('admin','saved-hash','Saved admin','ADMIN')");
        Flyway.configure().dataSource(ds).baselineOnMigrate(true).load().migrate();
        var bootstrap=new Bootstrap(db,new PasswordHasher());tx.executeWithoutResult(status->bootstrap.run());
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM users",Integer.class));
        assertEquals("saved-hash",db.queryForObject("SELECT password_hash FROM users",String.class));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM activities",Integer.class));
        assertEquals(1,db.queryForObject("SELECT completed FROM app_initialization WHERE name='demo-v1'",Integer.class));
    }
    @Test void expiredLegacyPromotionDoesNotLeakIntoCurrentRecommendations() {
        long owner=user("promoter");
        db.update("INSERT INTO activities(title,location,organizer_id,start_time,end_time,audit_status) VALUES('Expired boost','Campus',?,NOW()+INTERVAL 1 DAY,NOW()+INTERVAL 2 DAY,'APPROVED')",owner);
        long id=db.queryForObject("SELECT id FROM activities",Long.class);
        db.update("INSERT INTO ops_featured_activity(activity_id,weight,start_time,end_time) VALUES(?,100,NOW()-INTERVAL 2 DAY,NOW()-INTERVAL 1 DAY)",id);
        assertFalse(new RecommendService(db).activities(1).data().get(0).promoted());
    }

}
