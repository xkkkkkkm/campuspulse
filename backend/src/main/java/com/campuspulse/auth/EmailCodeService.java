package com.campuspulse.auth;

import com.campuspulse.common.Api;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Supplier;

/** Serializes each email/purpose challenge. Success and its business action commit together.
 * Invalid attempts return an outcome so their counters commit before the HTTP error is thrown. */
@Service
public class EmailCodeService {
    private final JdbcTemplate jdbc;
    private final JavaMailSender mail;
    private final TransactionTemplate transaction;
    private final String from, secret;
    private final boolean devMode;
    private final long ttlSeconds, cooldownSeconds;
    private final SecureRandom random = new SecureRandom();

    public EmailCodeService(JdbcTemplate jdbc, JavaMailSender mail, PlatformTransactionManager manager,
                            @Value("${app.security.email-from}") String from,
                            @Value("${app.security.email-secret}") String secret,
                            @Value("${app.security.email-dev-mode:true}") boolean devMode,
                            @Value("${app.security.email-code-ttl-seconds:300}") long ttlSeconds,
                            @Value("${app.security.email-cooldown-seconds:60}") long cooldownSeconds) {
        this.jdbc = jdbc; this.mail = mail; this.from = from; this.secret = secret;
        this.devMode = devMode; this.ttlSeconds = ttlSeconds; this.cooldownSeconds = cooldownSeconds;
        transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.setTimeout(30);
    }

    public Map<String, Object> sendCode(String email, String purpose) {
        return transaction.execute(status -> {
            lock(email, purpose);
            LocalDateTime last = jdbc.queryForObject("SELECT last_sent_at FROM email_code_guard WHERE email = ? AND purpose = ?", (rs, row) -> {
                Timestamp value = rs.getTimestamp(1); return value == null ? null : value.toLocalDateTime();
            }, email, purpose);
            LocalDateTime now = LocalDateTime.now();
            if (last != null && last.plusSeconds(cooldownSeconds).isAfter(now))
                throw error(HttpStatus.TOO_MANY_REQUESTS, "操作过于频繁，请稍后再试");
            String code = String.format("%06d", random.nextInt(1_000_000));
            jdbc.update("UPDATE email_code SET consumed_at = CURRENT_TIMESTAMP WHERE email = ? AND purpose = ? AND consumed_at IS NULL", email, purpose);
            jdbc.update("INSERT INTO email_code (email, purpose, code_hash, expires_at) VALUES (?, ?, ?, ?)", email, purpose, hash(email, purpose, code), Timestamp.valueOf(now.plusSeconds(ttlSeconds)));
            jdbc.update("UPDATE email_code_guard SET last_sent_at = ? WHERE email = ? AND purpose = ?", Timestamp.valueOf(now), email, purpose);
            if (devMode) return Map.of("devCode", code, "expiresIn", ttlSeconds);
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from); message.setTo(email);
            message.setSubject(Api.localize("CampusPulse 邮箱验证码", "CampusPulse verification code"));
            message.setText(Api.localize("您的 CampusPulse 验证码为：", "Your CampusPulse verification code is: ") + code
                    + Api.localize("\n请勿泄露给他人。有效期（分钟）：", "\nDo not share this code. Valid for minutes: ") + Math.max(1, ttlSeconds / 60));
            try { mail.send(message); }
            catch (Exception ex) { throw error(HttpStatus.SERVICE_UNAVAILABLE, "邮件发送失败，请稍后重试"); }
            return Map.of("expiresIn", ttlSeconds);
        });
    }

    public record Challenge(String email, String purpose, String code) {}
    private record Outcome<T>(T value, Api.ApiException error) {}

    public <T> T consume(String email, String purpose, String code, Supplier<T> action) {
        return consume(List.of(new Challenge(email, purpose, code)), action);
    }

    public <T> T consume(List<Challenge> challenges, Supplier<T> action) {
        List<Challenge> sorted = challenges.stream().sorted(Comparator.comparing(c -> c.email() + ":" + c.purpose())).toList();
        Outcome<T> outcome = transaction.execute(status -> {
            // Acquire guards in deterministic order when changing both email addresses.
            for (Challenge challenge : sorted) lock(challenge.email(), challenge.purpose());
            List<Long> ids = new ArrayList<>();
            for (Challenge c : sorted) {
                var rows = jdbc.queryForList("SELECT id, code_hash, expires_at, consumed_at, attempt_count FROM email_code WHERE email = ? AND purpose = ? ORDER BY id DESC LIMIT 1 FOR UPDATE", c.email(), c.purpose());
                if (rows.isEmpty()) return new Outcome<>(null, error(HttpStatus.BAD_REQUEST, "验证码已过期或不存在"));
                var row = rows.get(0);
                long id = ((Number) row.get("id")).longValue();
                if (row.get("consumed_at") != null || !time(row.get("expires_at")).isAfter(LocalDateTime.now()))
                    return new Outcome<>(null, error(HttpStatus.BAD_REQUEST, "验证码已使用或过期，请重新获取"));
                if (((Number) row.get("attempt_count")).intValue() >= 5)
                    return new Outcome<>(null, error(HttpStatus.TOO_MANY_REQUESTS, "验证码错误次数过多，请重新获取"));
                if (c.code() == null || !c.code().trim().matches("[0-9]{6}") || !MessageDigest.isEqual(String.valueOf(row.get("code_hash")).getBytes(StandardCharsets.UTF_8), hash(c.email(), c.purpose(), c.code().trim()).getBytes(StandardCharsets.UTF_8))) {
                    jdbc.update("UPDATE email_code SET attempt_count = attempt_count + 1 WHERE id = ?", id);
                    return new Outcome<>(null, error(HttpStatus.BAD_REQUEST, "验证码错误"));
                }
                ids.add(id);
            }
            for (Long id : ids) jdbc.update("UPDATE email_code SET consumed_at = CURRENT_TIMESTAMP WHERE id = ? AND consumed_at IS NULL", id);
            return new Outcome<>(action.get(), null); // Any business failure rolls back successful consumption.
        });
        if (outcome.error() != null) throw outcome.error();
        return outcome.value();
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 3600000)
    public void pruneExpiredChallenges() {
        jdbc.update("DELETE FROM email_code WHERE expires_at < DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 1 DAY) LIMIT 1000");
        jdbc.update("DELETE FROM email_code_guard WHERE COALESCE(last_sent_at, created_at) < DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 2 DAY) LIMIT 1000");
        jdbc.update("DELETE FROM auth_audit WHERE created_at < DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 90 DAY) LIMIT 1000");
    }

    private void lock(String email, String purpose) {
        jdbc.update("INSERT INTO email_code_guard (email, purpose) VALUES (?, ?) ON DUPLICATE KEY UPDATE email = VALUES(email)", email, purpose);
        jdbc.queryForList("SELECT email FROM email_code_guard WHERE email = ? AND purpose = ? FOR UPDATE", email, purpose);
    }
    private String hash(String email, String purpose, String code) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((email + ":" + purpose + ":" + code + ":" + secret).getBytes(StandardCharsets.UTF_8))); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }
    private static LocalDateTime time(Object value) { return value instanceof Timestamp t ? t.toLocalDateTime() : (LocalDateTime) value; }
    private static Api.ApiException error(HttpStatus status, String message) { return new Api.ApiException(status, message); }
}
