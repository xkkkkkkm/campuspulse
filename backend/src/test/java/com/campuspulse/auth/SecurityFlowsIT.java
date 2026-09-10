package com.campuspulse.auth;

import com.campuspulse.common.Api;
import com.campuspulse.security.PasswordHasher;
import com.campuspulse.security.AuthContext;
import com.campuspulse.support.SupportTicketService;
import com.campuspulse.upload.UploadService;
import org.springframework.transaction.interceptor.TransactionProxyFactoryBean;
import org.springframework.mock.web.MockMultipartFile;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import com.campuspulse.security.TokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mail.javamail.JavaMailSender;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Runs only against a new disposable MySQL database, never the configured application DB. */
@Testcontainers
class SecurityFlowsIT {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0").withDatabaseName("security_test");
    static JdbcTemplate jdbc;
    static EmailCodeService codes;
    static AuthController auth;
    static TokenService tokens;
    static JavaMailSender mail;
    static DataSourceTransactionManager manager;
    @TempDir Path files;
    static final PasswordHasher passwords = new PasswordHasher();

    @BeforeAll static void setup() {
        var dataSource = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        Flyway.configure().dataSource(dataSource).load().migrate();
        jdbc = new JdbcTemplate(dataSource);
        manager = new DataSourceTransactionManager(dataSource);
        mail = mock(JavaMailSender.class);
        codes = new EmailCodeService(jdbc, mail, manager, "test@example.invalid", "code-test-secret-not-for-deployment", true,300,60);
        tokens = new TokenService(new ObjectMapper(), jdbc, "token-test-secret-not-for-deployment",300);
        auth = new AuthController(jdbc,passwords,tokens,codes,manager,5,15);
    }
    static String email() { return UUID.randomUUID()+"@example.invalid"; }
    static List<Boolean> race(int threads, Supplier<Boolean> action) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads), start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> jobs = new ArrayList<>();
            for(int i=0;i<threads;i++) jobs.add(pool.submit(() -> { ready.countDown(); start.await(); return action.get(); }));
            assertTrue(ready.await(5,TimeUnit.SECONDS)); start.countDown();
            List<Boolean> result = new ArrayList<>();
            for(var job:jobs) result.add(job.get(30,TimeUnit.SECONDS));
            return result;
        } finally { pool.shutdownNow(); }
    }
    @Test void concurrentSendEnforcesOneCooldownReservation() throws Exception {
        String email = email();
        var results = race(6, () -> { try { codes.sendCode(email,"REGISTER"); return true; } catch(Api.ApiException ex) { assertEquals(429,ex.status().value()); return false; } });
        assertEquals(1,results.stream().filter(Boolean::booleanValue).count());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM email_code WHERE email = ?",Integer.class,email));
        verifyNoInteractions(mail);
    }
    @Test void exactlyOneConcurrentConsumerAndFailureRollsBackConsumption() throws Exception {
        String email = email(); String code = (String)codes.sendCode(email,"REGISTER").get("devCode");
        assertThrows(IllegalStateException.class,() -> codes.consume(email,"REGISTER",code,() -> { throw new IllegalStateException("business rollback"); }));
        assertNull(jdbc.queryForMap("SELECT consumed_at FROM email_code WHERE email = ?",email).get("consumed_at"));
        var results = race(8, () -> { try { return codes.consume(email,"REGISTER",code,() -> true); } catch(Api.ApiException ex) { return false; } });
        assertEquals(1,results.stream().filter(Boolean::booleanValue).count());
    }
    @Test void failedAttemptsPersistAndTwoCodeChangeIsAtomic() throws Exception {
        String first=email(), second=email();
        String firstCode=(String)codes.sendCode(first,"BIND").get("devCode");
        String secondCode=(String)codes.sendCode(second,"BIND").get("devCode");
        String invalid = secondCode.equals("000000") ? "999999" : "000000";
        assertThrows(Api.ApiException.class,() -> codes.consume(List.of(new EmailCodeService.Challenge(first,"BIND",firstCode),new EmailCodeService.Challenge(second,"BIND",invalid)),() -> true));
        assertNull(jdbc.queryForMap("SELECT consumed_at FROM email_code WHERE email = ?",first).get("consumed_at"));
        race(8, () -> { try { return codes.consume(second,"BIND",invalid,() -> true); } catch(Api.ApiException ex) { return false; } });
        assertEquals(5,jdbc.queryForObject("SELECT attempt_count FROM email_code WHERE email = ?",Integer.class,second));
        assertThrows(Api.ApiException.class,() -> codes.consume(second,"BIND",secondCode,() -> true));
        assertTrue(codes.consume(first,"BIND",firstCode,() -> true));
    }
    @Test void resetRevokesPreviousTokenAndLoginFailuresDoNotLoseIncrements() throws Exception {
        String username="user-"+UUID.randomUUID(), email=email();
        jdbc.update("INSERT INTO users (username,password_hash,nickname,email,email_verified) VALUES (?,?,'Test',?,1)",username,passwords.hash("Old-Password1!"),email);
        long id=jdbc.queryForObject("SELECT id FROM users WHERE username = ?",Long.class,username);
        String before=tokens.issueToken(id,username,"USER");
        race(8,() -> { try { auth.login(new AuthController.LoginRequest(username,"wrong")); return true; } catch(Api.ApiException ex) { return false; } });
        assertEquals(5,jdbc.queryForObject("SELECT failed_login_count FROM users WHERE id = ?",Integer.class,id));
        assertNotNull(jdbc.queryForMap("SELECT lock_until FROM users WHERE id = ?",id).get("lock_until"));
        String code=(String)codes.sendCode(email,"RESET").get("devCode");
        auth.resetPassword(new AuthController.ResetPasswordRequest(email,code,"New-Password2!"));
        assertTrue(tokens.parse(before).isEmpty());
        String after=auth.login(new AuthController.LoginRequest(username,"New-Password2!")).data().token();
        assertTrue(tokens.parse(after).isPresent());
    }
    @Test void registrationFailureKeepsCodeAndLegacyEndpointNeverReservesEmail() {
        String email=email(), username="user-"+UUID.randomUUID();
        assertEquals(410,assertThrows(Api.ApiException.class,() -> auth.register(new AuthController.RegisterRequest(username,"New-Password2!","Test","test",null,email,null))).status().value());
        String code=(String)codes.sendCode(email,"REGISTER").get("devCode");
        String duplicate="duplicate-"+UUID.randomUUID();
        jdbc.update("INSERT INTO users (username,password_hash,nickname) VALUES (?,'hash','Test')",duplicate);
        assertThrows(Api.ApiException.class,() -> auth.registerEmail(new AuthController.RegisterEmailRequest(duplicate,"New-Password2!","Test",UUID.randomUUID().toString().substring(0,30),null,email,code,null)));
        assertNull(jdbc.queryForMap("SELECT consumed_at FROM email_code WHERE email = ?",email).get("consumed_at"));
        String longPassword="Aa1!"+"b".repeat(124);
        var created=auth.registerEmail(new AuthController.RegisterEmailRequest(username,longPassword,"Test",UUID.randomUUID().toString().substring(0,30),null,email,code,"javascript:bad"));
        assertTrue(tokens.parse(created.data().token()).isPresent());
        assertTrue(auth.login(new AuthController.LoginRequest(username,longPassword)).success());
        assertNull(jdbc.queryForMap("SELECT avatar_url FROM users WHERE username = ?",username).get("avatar_url"));
    }
    @Test void concurrentCrossColumnIdentifiersAllowOneAccountAndKeepLosingCodeReusable() throws Exception {
        String firstAlias="cross-a-"+UUID.randomUUID().toString().substring(0,20);
        String secondAlias="cross-b-"+UUID.randomUUID().toString().substring(0,20);
        String firstEmail=email(), secondEmail=email();
        String firstCode=(String)codes.sendCode(firstEmail,"REGISTER").get("devCode");
        String secondCode=(String)codes.sendCode(secondEmail,"REGISTER").get("devCode");
        var requests=List.of(
                new AuthController.RegisterEmailRequest(firstAlias,"Cross-Password1!","First",secondAlias,null,firstEmail,firstCode,null),
                new AuthController.RegisterEmailRequest(secondAlias,"Cross-Password2!","Second",firstAlias,null,secondEmail,secondCode,null));
        AtomicInteger nextRequest=new AtomicInteger();
        var results=race(2,() -> {
            var request=requests.get(nextRequest.getAndIncrement());
            try { return auth.registerEmail(request).success(); }
            catch(Api.ApiException ex) { assertEquals(409,ex.status().value()); return false; }
        });
        assertEquals(1,results.stream().filter(Boolean::booleanValue).count());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE username IN (?,?) OR student_no IN (?,?)",Integer.class,firstAlias,secondAlias,firstAlias,secondAlias));
        for(var request:requests) {
            boolean registered=jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email = ?",Integer.class,request.email())==1;
            var challenge=jdbc.queryForMap("SELECT consumed_at, attempt_count FROM email_code WHERE email = ? AND purpose = 'REGISTER' ORDER BY id DESC LIMIT 1",request.email());
            if (registered) assertNotNull(challenge.get("consumed_at"));
            else {
                assertNull(challenge.get("consumed_at"));
                assertEquals(0,((Number)challenge.get("attempt_count")).intValue());
                String suffix=UUID.randomUUID().toString().substring(0,20);
                var retry=auth.registerEmail(new AuthController.RegisterEmailRequest("retry-"+suffix,request.password(),request.nickname(),"student-"+suffix,null,request.email(),request.code(),null));
                assertTrue(retry.success());
                assertTrue(tokens.parse(retry.data().token()).isPresent());
            }
        }
    }

    @Test void legacyCrossColumnConflictsResolveToEachUsernameIncludingLongPassword() {
        String firstAlias="legacy-a-"+UUID.randomUUID().toString().substring(0,20);
        String secondAlias="legacy-b-"+UUID.randomUUID().toString().substring(0,20);
        String firstPassword="Aa1!"+"b".repeat(124), secondPassword="Legacy-Password2!";
        assertEquals(128,firstPassword.length());
        // Reproduce an existing course database that predates cross-column uniqueness.
        jdbc.update("INSERT INTO users (username,student_no,password_hash,nickname) VALUES (?,?,?,'Legacy first')",firstAlias,secondAlias,passwords.hash(firstPassword));
        jdbc.update("INSERT INTO users (username,student_no,password_hash,nickname) VALUES (?,?,?,'Legacy second')",secondAlias,firstAlias,passwords.hash(secondPassword));
        long firstId=jdbc.queryForObject("SELECT id FROM users WHERE username = ?",Long.class,firstAlias);
        long secondId=jdbc.queryForObject("SELECT id FROM users WHERE username = ?",Long.class,secondAlias);
        assertTrue(firstId<secondId);
        var firstLogin=auth.login(new AuthController.LoginRequest(firstAlias,firstPassword));
        var secondLogin=auth.login(new AuthController.LoginRequest(secondAlias,secondPassword));
        assertEquals(firstId,firstLogin.data().user().id());
        assertEquals(secondId,secondLogin.data().user().id());
        assertEquals(firstId,tokens.parse(firstLogin.data().token()).orElseThrow().id());
        assertEquals(secondId,tokens.parse(secondLogin.data().token()).orElseThrow().id());
        assertEquals(401,assertThrows(Api.ApiException.class,() -> auth.login(new AuthController.LoginRequest(secondAlias,firstPassword))).status().value());
        assertEquals(0,jdbc.queryForObject("SELECT failed_login_count FROM users WHERE id = ?",Integer.class,firstId));
        assertEquals(1,jdbc.queryForObject("SELECT failed_login_count FROM users WHERE id = ?",Integer.class,secondId));
    }

    static AuthContext.User newUser(String role) {
        String username="u-"+UUID.randomUUID();
        jdbc.update("INSERT INTO users (username,password_hash,nickname,role) VALUES (?,'test-hash','Test',?)",username,role);
        return new AuthContext.User(jdbc.queryForObject("SELECT id FROM users WHERE username = ?",Long.class,username),username,role);
    }
    @Test void supportPersistsRepliesEnforcesOwnershipAndRollsBackOnNotificationFailure() {
        TransactionProxyFactoryBean factory = new TransactionProxyFactoryBean();
        factory.setTarget(new SupportTicketService(jdbc)); factory.setTransactionManager(manager); factory.setProxyTargetClass(true);
        Properties rules=new Properties(); rules.setProperty("*","PROPAGATION_REQUIRED"); factory.setTransactionAttributes(rules); factory.afterPropertiesSet();
        SupportTicketService tickets=(SupportTicketService)factory.getObject();
        AuthContext.User owner=newUser("USER"), admin=newUser("ADMIN"), stranger=newUser("USER");
        jdbc.execute("ALTER TABLE notifications ADD CONSTRAINT test_reject_support CHECK (type <> 'SUPPORT_ESCALATION')");
        try {
            assertThrows(RuntimeException.class,() -> tickets.create(owner,"Rollback this test ticket"));
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM support_ticket WHERE user_id = ?",Integer.class,owner.id()));
        } finally { jdbc.execute("ALTER TABLE notifications DROP CHECK test_reject_support"); }
        long id=tickets.create(owner,"Please help with my account");
        assertEquals(id,tickets.create(owner,"Please help with my account"));
        assertThrows(Api.ApiException.class,() -> tickets.detail(stranger,id,0));
        tickets.reply(admin,id,"A detailed reply "+"x".repeat(1200));
        assertEquals(1,((List<?>)tickets.detail(owner,id,0).get("replies")).size());
        tickets.status(admin,id,"CLOSED");
        assertThrows(Api.ApiException.class,() -> tickets.reply(owner,id,"reply after close"));
        assertEquals("CLOSED",jdbc.queryForObject("SELECT status FROM support_ticket WHERE id = ?",String.class,id));
    }
    @Test void uploadSanitizesFilesAndEnforcesConcurrentQuota() throws Exception {
        AuthContext.User user=newUser("USER");
        BufferedImage original=new BufferedImage(8,8,BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bytes=new ByteArrayOutputStream(); ImageIO.write(original,"png",bytes); original.flush();
        byte[] image=bytes.toByteArray();
        UploadService uploads=new UploadService(jdbc,manager,files.toString(),image.length+10,1000000);
        var results=race(4,() -> {
            try { uploads.save(user.id(),new MockMultipartFile("file","photo.png","image/png",image),"avatars",1024); return true; }
            catch(Api.ApiException ex) { return false; }
            catch(Exception ex) { throw new RuntimeException(ex); }
        });
        assertEquals(1,results.stream().filter(Boolean::booleanValue).count());
        var saved=uploads.list(user.id()); assertEquals(1,saved.size());
        String url=String.valueOf(saved.get(0).get("url"));
        assertDoesNotThrow(() -> uploads.requireOwned(user.id(),url,"avatars"));
        assertThrows(Api.ApiException.class,() -> uploads.requireOwned(newUser("USER").id(),url,"avatars"));
        assertNotNull(ImageIO.read(files.resolve(url.substring("/uploads/".length())).toFile()));
        uploads.deleteUnused(user.id(),String.valueOf(saved.get(0).get("id")));
        assertTrue(uploads.list(user.id()).isEmpty());
        assertFalse(Files.exists(files.resolve(url.substring("/uploads/".length()))));
    }

}
