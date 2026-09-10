package com.campuspulse.support;

import com.campuspulse.common.Api;
import com.campuspulse.security.AuthContext;
import com.campuspulse.security.RequestRateLimiter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.interceptor.TransactionProxyFactoryBean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Testcontainers
class SupportConversationIT {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0").withDatabaseName("support_test");
    static JdbcTemplate db;
    static SupportConversationService conversations;
    @BeforeAll static void setup() {
        var ds = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        Flyway.configure().dataSource(ds).load().migrate(); db = new JdbcTemplate(ds);
        var proxy = new TransactionProxyFactoryBean();
        proxy.setTarget(new SupportConversationService(db, new ObjectMapper()));
        proxy.setTransactionManager(new DataSourceTransactionManager(ds));
        Properties attributes = new Properties(); attributes.setProperty("*", "PROPAGATION_REQUIRED");
        proxy.setTransactionAttributes(attributes); proxy.setProxyTargetClass(true); proxy.afterPropertiesSet();
        conversations = (SupportConversationService) proxy.getObject();
    }
    @AfterEach void clearLocale() { LocaleContextHolder.resetLocaleContext(); }
    long user() {
        String name = UUID.randomUUID().toString();
        db.update("INSERT INTO users(username,password_hash,nickname) VALUES (?,'test','Test')", name);
        return db.queryForObject("SELECT id FROM users WHERE username=?", Long.class, name);
    }
    SupportAnswer answer() { return LocalSupportKnowledge.answer("How do I join a team?", "en-US"); }

    @Test void conversationsKeepOrderedContextEnforceOwnershipAndDeleteTheirTurns() {
        long owner = user(), other = user();
        var first = conversations.begin(owner, null);
        assertTrue(first.history().isEmpty());
        conversations.complete(owner, first, "How do I join a team?", answer());
        assertEquals(first.id(), conversations.list(owner).get(0).get("id"));
        assertEquals("How do I join a team?", conversations.list(owner).get(0).get("preview"));
        assertTrue(conversations.list(other).isEmpty());
        var second = conversations.begin(owner, first.id());
        assertEquals(2, second.history().size());
        assertEquals("user", second.history().get(0).get("role"));
        conversations.release(second);
        assertEquals(404, assertThrows(Api.ApiException.class, () -> conversations.begin(other, first.id())).status().value());
        assertEquals(404, assertThrows(Api.ApiException.class, () -> conversations.detail(other, first.id())).status().value());
        assertEquals(404, assertThrows(Api.ApiException.class, () -> conversations.delete(other, first.id())).status().value());
        conversations.delete(owner, first.id());
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM support_chat_turn WHERE conversation_id=?", Integer.class, first.id()));
    }

    @Test void parallelRequestsCannotAcquireTheSameConversationLease() throws Exception {
        long owner = user(); var first = conversations.begin(owner, null); conversations.release(first);
        var pool = Executors.newFixedThreadPool(4); var ready = new CountDownLatch(4); var start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> jobs = new ArrayList<>();
            for (int i=0;i<4;i++) jobs.add(pool.submit(() -> {
                ready.countDown(); start.await();
                try { conversations.begin(owner, first.id()); return true; }
                catch (Api.ApiException conflict) { assertEquals(409, conflict.status().value()); return false; }
            }));
            assertTrue(ready.await(5, TimeUnit.SECONDS)); start.countDown();
            int acquired = 0; for (var job : jobs) if (job.get(15, TimeUnit.SECONDS)) acquired++;
            assertEquals(1, acquired);
        } finally { pool.shutdownNow(); }
    }

    @Test void expiredLeaseCannotOverwriteOrReleaseANewerTurn() {
        long owner = user(); var old = conversations.begin(owner, null);
        db.update("UPDATE support_conversation SET busy_until=NOW()-INTERVAL 1 SECOND WHERE id=?", old.id());
        var current = conversations.begin(owner, old.id());
        assertThrows(Api.ApiException.class, () -> conversations.complete(owner, old, "stale", answer()));
        conversations.release(old);
        assertEquals(current.token(), db.queryForObject("SELECT lease_token FROM support_conversation WHERE id=?", String.class, old.id()));
        conversations.complete(owner, current, "current", answer());
        assertEquals("current", db.queryForObject("SELECT message FROM support_chat_turn WHERE conversation_id=?", String.class, old.id()));
    }

    @Test void unknownQuestionsAndAgentFailureDoNotCreateTicketsOrLoseConversations() {
        var client = mock(SupportAgentClient.class); when(client.enabled()).thenReturn(true);
        when(client.answer(anyString(), anyString(), anyList(), anyBoolean())).thenThrow(new IllegalStateException("offline"));
        var chat = new SupportChatService(client, conversations, new RequestRateLimiter(), db, false, 0);
        long owner = user(); var user = new AuthContext.User(owner, "test", "USER");
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        var reply = chat.answer("Can you confirm my personal reimbursement?", null, user, "test-offline");
        assertTrue(reply.suggestEscalation()); assertFalse(reply.escalated()); assertNull(reply.ticketId());
        assertEquals("LOCAL_KNOWLEDGE", reply.source());
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM support_ticket WHERE user_id=?", Integer.class, owner));
        assertEquals(2, ((List<?>) conversations.detail(owner, reply.conversationId()).get("messages")).size());
        var next = conversations.begin(owner, reply.conversationId()); conversations.release(next);
    }

    @Test void authenticatedUsersBehindTheSameProxyHaveIndependentQuotas() {
        var client = mock(SupportAgentClient.class);
        var chat = new SupportChatService(client, conversations, new RequestRateLimiter(), db, false, 0);
        var first = new AuthContext.User(user(),"first","USER");
        var second = new AuthContext.User(user(),"second","USER");
        String firstId = null, secondId = null;
        for (int i=0;i<16;i++) {
            firstId = chat.answer("How do I join a team?",firstId,first,"same-proxy").conversationId();
            secondId = chat.answer("How do I join a team?",secondId,second,"same-proxy").conversationId();
        }
        assertEquals(32, ((List<?>)conversations.detail(first.id(),firstId).get("messages")).size());
        assertEquals(32, ((List<?>)conversations.detail(second.id(),secondId).get("messages")).size());
    }

    @Test void fullConversationQuotaCanBeRecoveredThroughListAndDelete() {
        long owner = user();
        for (int i=0;i<50;i++) conversations.release(conversations.begin(owner,null));
        assertEquals(429,assertThrows(Api.ApiException.class,()->conversations.begin(owner,null)).status().value());
        var list = conversations.list(owner);
        assertEquals(50,list.size());
        conversations.delete(owner,(String)list.get(0).get("id"));
        conversations.release(conversations.begin(owner,null));
        assertEquals(50,conversations.list(owner).size());
    }

    @Test void anonymousAndExhaustedBudgetsCannotRequestModelGeneration() {
        db.update("DELETE FROM support_ai_budget");
        var client = mock(SupportAgentClient.class); when(client.enabled()).thenReturn(true);
        when(client.answer(anyString(), anyString(), anyList(), anyBoolean())).thenReturn(
                new SupportAnswer("Read the team guide.", "LANGGRAPH_RETRIEVAL", List.of(), false));
        var chat = new SupportChatService(client, conversations, new RequestRateLimiter(), db, true, 1);
        chat.answer("How do I join a team?", null, null, "guest");
        verify(client).answer(anyString(), anyString(), eq(List.of()), eq(false));
        long owner = user(); var user = new AuthContext.User(owner, "test", "USER");
        var first = chat.answer("How do I join a team?", null, user, "member");
        chat.answer("Can I leave?", first.conversationId(), user, "member");
        verify(client, times(1)).answer(anyString(), anyString(), anyList(), eq(true));
        verify(client, times(2)).answer(anyString(), anyString(), anyList(), eq(false));
        assertEquals(1, db.queryForObject("SELECT request_count FROM support_ai_budget", Integer.class));
    }
}
