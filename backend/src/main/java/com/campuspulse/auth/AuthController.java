package com.campuspulse.auth;

import com.campuspulse.common.Api;
import com.campuspulse.security.AuthContext;
import com.campuspulse.security.PasswordHasher;
import com.campuspulse.security.TokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final JdbcTemplate jdbcTemplate;
    private final PasswordHasher passwordHasher;
    private final TokenService tokenService;
    private final EmailCodeService emailCodeService;
    private final TransactionTemplate transaction;
    private final int loginMaxFailed;
    private final int loginLockMinutes;

    public AuthController(JdbcTemplate jdbcTemplate,
                          PasswordHasher passwordHasher,
                          TokenService tokenService,
                          EmailCodeService emailCodeService,
                          PlatformTransactionManager manager,
                          @Value("${app.security.login-max-failed:5}") int loginMaxFailed,
                          @Value("${app.security.login-lock-minutes:15}") int loginLockMinutes) {
        this.jdbcTemplate = jdbcTemplate;
        this.transaction = new TransactionTemplate(manager);
        this.passwordHasher = passwordHasher;
        this.tokenService = tokenService;
        this.emailCodeService = emailCodeService;
        this.loginMaxFailed = loginMaxFailed;
        this.loginLockMinutes = loginLockMinutes;
    }

    @PostMapping("/register")
    public Api.ApiResponse<AuthResponse> register(@RequestBody RegisterRequest req) {
        throw new Api.ApiException(HttpStatus.GONE, "请使用邮箱验证码注册接口 /api/auth/register-email");
    }

    @PostMapping({"/logout", "/logout-all"})
    public Api.ApiResponse<Void> logout() {
        long userId = AuthContext.requireUser().id();
        transaction.executeWithoutResult(tx -> { tokenService.revokeAll(userId); audit(userId, "LOGOUT_ALL", "SUCCESS"); });
        return Api.ok();
    }

    @PostMapping("/email/send")
    public Api.ApiResponse<Map<String, Object>> sendEmail(@RequestBody EmailSendRequest req) {
        if (req == null) throw new Api.ApiException(HttpStatus.BAD_REQUEST, "参数错误");
        String email = mustTrim(req.email(), 5, 128, "邮箱").toLowerCase();
        if (!isValidEmail(email)) throw new Api.ApiException(HttpStatus.BAD_REQUEST, "邮箱格式不正确");
        String purpose = mustTrim(req.purpose(), 1, 32, "用途").toUpperCase();
        if (!List.of("LOGIN", "REGISTER", "RESET", "BIND").contains(purpose)) {
            throw new Api.ApiException(HttpStatus.BAD_REQUEST, "用途不支持");
        }
        if ("BIND".equals(purpose)) AuthContext.requireUser();
        if ("LOGIN".equals(purpose) || "RESET".equals(purpose)) {
            Long bound = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE email = ? AND email_verified = 1", Long.class, email);
            if (bound == null || bound == 0) {
                throw new Api.ApiException(HttpStatus.NOT_FOUND, "该邮箱未绑定账号");
            }
        }
        if ("REGISTER".equals(purpose) || "BIND".equals(purpose)) {
            Long used = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE email = ? AND email_verified = 1", Long.class, email);
            if (used != null && used > 0) {
                throw new Api.ApiException(HttpStatus.CONFLICT, "该邮箱已被绑定");
            }
        }
        return Api.ok(emailCodeService.sendCode(email, purpose));
    }

    @PostMapping("/me/password/email/send")
    public Api.ApiResponse<Map<String, Object>> sendChangePasswordEmail() {
        AuthContext.User p = AuthContext.requireUser();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT email, email_verified FROM users WHERE id = ? LIMIT 1", p.id());
        if (rows.isEmpty()) throw new Api.ApiException(HttpStatus.UNAUTHORIZED, "未登录或登录已过期");
        Map<String, Object> u = rows.get(0);
        String email = u.get("email") == null ? null : String.valueOf(u.get("email")).trim().toLowerCase();
        boolean verified = u.get("email_verified") != null && ((Number) u.get("email_verified")).intValue() == 1;
        if (email == null || email.isBlank() || !verified) {
            throw new Api.ApiException(HttpStatus.BAD_REQUEST, "请先绑定邮箱");
        }
        return Api.ok(emailCodeService.sendCode(email, "CHANGE"));
    }

    @PostMapping("/me/password/change-email")
    public Api.ApiResponse<Void> changePasswordByEmail(@RequestBody ChangePasswordEmailRequest req) {
        AuthContext.User p = AuthContext.requireUser();
        if (req == null) throw new Api.ApiException(HttpStatus.BAD_REQUEST, "参数错误");
        String newPwd = mustTrim(req.newPassword(), 8, 128, "新密码");
        ensureStrongPassword(newPwd);
        Map<String, Object> u = jdbcTemplate.queryForMap("SELECT email, email_verified FROM users WHERE id = ? LIMIT 1", p.id());
        String email = u.get("email") == null ? null : String.valueOf(u.get("email")).trim().toLowerCase();
        boolean verified = u.get("email_verified") != null && ((Number) u.get("email_verified")).intValue() == 1;
        if (email == null || email.isBlank() || !verified) {
            throw new Api.ApiException(HttpStatus.BAD_REQUEST, "请先绑定邮箱");
        }
        return emailCodeService.consume(email, "CHANGE", req.code(), () -> {
        requireCurrentAccount(p);
        if (jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE id = ? AND email = ? AND email_verified = 1", Long.class, p.id(), email) != 1) throw new Api.ApiException(HttpStatus.CONFLICT, "邮箱已变更，请重试");
        jdbcTemplate.update("UPDATE users SET password_hash = ?, token_version = token_version + 1 WHERE id = ?", passwordHasher.hash(newPwd), p.id());
        audit(p.id(), "PASSWORD_CHANGE", "SUCCESS");
        return Api.ok();
        });
    }

    @PostMapping("/login-email")
    public Api.ApiResponse<AuthResponse> loginEmail(@RequestBody EmailLoginRequest req) {
        if (req == null) throw new Api.ApiException(HttpStatus.BAD_REQUEST, "参数错误");
        String email = mustTrim(req.email(), 5, 128, "邮箱").toLowerCase();
        if (!isValidEmail(email)) throw new Api.ApiException(HttpStatus.BAD_REQUEST, "邮箱格式不正确");
        return emailCodeService.consume(email, "LOGIN", req.code(), () -> {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT id, username, nickname, role, status
                FROM users
                WHERE email = ? AND email_verified = 1
                LIMIT 1 FOR UPDATE
                """, email);
        if (rows.isEmpty()) throw new Api.ApiException(HttpStatus.NOT_FOUND, "该邮箱未绑定账号");
        Map<String, Object> u = rows.get(0);
        int status = ((Number) u.get("status")).intValue();
        if (status != 1) throw new Api.ApiException(HttpStatus.FORBIDDEN, "账号已被禁用");
        long uid = ((Number) u.get("id")).longValue();
        String username = String.valueOf(u.get("username"));
        String role = String.valueOf(u.get("role"));
        audit(uid, "EMAIL_LOGIN", "SUCCESS");
        String token = tokenService.issueToken(uid, username, role);
        return Api.ok(new AuthResponse(token, new UserBrief(uid, username, String.valueOf(u.get("nickname")), role)));
        });
    }

    @PostMapping("/register-email")
    public Api.ApiResponse<AuthResponse> registerEmail(@RequestBody RegisterEmailRequest req) {
        if (req == null) throw new Api.ApiException(HttpStatus.BAD_REQUEST, "参数错误");
        String username = mustTrim(req.username(), 3, 64, "用户名");
        String password = mustTrim(req.password(), 8, 128, "密码");
        ensureStrongPassword(password);
        String nickname = mustTrim(req.nickname(), 1, 64, "姓名");
        String studentNo = mustTrim(req.studentNo(), 1, 32, "学号");
        String email = mustTrim(req.email(), 5, 128, "邮箱").toLowerCase();
        if (!isValidEmail(email)) throw new Api.ApiException(HttpStatus.BAD_REQUEST, "邮箱格式不正确");
        return emailCodeService.consume(email, "REGISTER", req.code(), () -> {
        jdbcTemplate.queryForObject("SELECT completed FROM app_initialization WHERE name='account-identifiers' FOR UPDATE",Integer.class);
        if(!jdbcTemplate.queryForList("SELECT id FROM users WHERE username IN (?,?) OR student_no IN (?,?) FOR UPDATE",username,studentNo,username,studentNo).isEmpty())
            throw new Api.ApiException(HttpStatus.CONFLICT,"用户名或学号已被占用");
        Long exists = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE username = ?", Long.class, username);
        if (exists != null && exists > 0) throw new Api.ApiException(HttpStatus.CONFLICT, "用户名已存在");
        Long stuExists = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE student_no = ?", Long.class, studentNo);
        if (stuExists != null && stuExists > 0) throw new Api.ApiException(HttpStatus.CONFLICT, "学号已被占用");
        Long emailUsed = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE email = ?", Long.class, email);
        if (emailUsed != null && emailUsed > 0) throw new Api.ApiException(HttpStatus.CONFLICT, "邮箱已被绑定");

        jdbcTemplate.update("""
                INSERT INTO users (username, student_no, password_hash, nickname, college, email, email_verified, avatar_url, role, status)
                VALUES (?, ?, ?, ?, ?, ?, 1, ?, 'USER', 1)
                """,
                username,
                studentNo,
                passwordHasher.hash(password),
                nickname,
                emptyToNull(req.college()),
                email,
                null
        );
        Map<String, Object> user = jdbcTemplate.queryForMap("SELECT id, username, nickname, role FROM users WHERE username = ? LIMIT 1", username);
        long uid = ((Number) user.get("id")).longValue();
        audit(uid, "REGISTER", "SUCCESS");
        String token = tokenService.issueToken(uid, username, String.valueOf(user.get("role")));
        return Api.ok(new AuthResponse(token, new UserBrief(uid, username, String.valueOf(user.get("nickname")), String.valueOf(user.get("role")))));
        });
    }

    @PostMapping("/login")
    public Api.ApiResponse<AuthResponse> login(@RequestBody LoginRequest req) {
        if (req == null) throw new Api.ApiException(HttpStatus.BAD_REQUEST, "参数错误");
        String account = mustTrim(req.usernameOrStudentNo(), 1, 64, "账号");
        String password = mustTrim(req.password(), 1, 128, "密码");
        LoginOutcome outcome = transaction.execute(tx -> {
            var rows = jdbcTemplate.queryForList("SELECT id, username, password_hash, nickname, role, status, failed_login_count, lock_until FROM users WHERE username = ? OR student_no = ? ORDER BY (username = ?) DESC,id LIMIT 1 FOR UPDATE", account, account, account);
            if (rows.isEmpty()) return new LoginOutcome(null, new Api.ApiException(HttpStatus.UNAUTHORIZED, "账号或密码错误"));
            var user = rows.get(0);
            if (((Number) user.get("status")).intValue() != 1) return new LoginOutcome(null, new Api.ApiException(HttpStatus.FORBIDDEN, "账号已被禁用"));
            LocalDateTime lockUntil = user.get("lock_until") == null ? null : toLocalDateTime(user.get("lock_until"));
            if (lockUntil != null && lockUntil.isAfter(LocalDateTime.now())) return new LoginOutcome(null, new Api.ApiException(HttpStatus.TOO_MANY_REQUESTS, "账号或密码错误次数过多，请稍后再试"));
            long uid = ((Number) user.get("id")).longValue();
            if (!passwordHasher.matches(password, String.valueOf(user.get("password_hash")))) {
                int failed = lockUntil != null ? 1 : ((Number) user.get("failed_login_count")).intValue() + 1;
                jdbcTemplate.update("UPDATE users SET failed_login_count = ?, lock_until = ? WHERE id = ?", failed, failed >= loginMaxFailed ? java.sql.Timestamp.valueOf(LocalDateTime.now().plusMinutes(loginLockMinutes)) : null, uid);
                audit(uid, "PASSWORD_LOGIN", "FAILURE");
                return new LoginOutcome(null, new Api.ApiException(failed >= loginMaxFailed ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.UNAUTHORIZED, failed >= loginMaxFailed ? "账号或密码错误次数过多，请稍后再试" : "账号或密码错误"));
            }
            jdbcTemplate.update("UPDATE users SET failed_login_count = 0, lock_until = NULL WHERE id = ?", uid);
            String username = String.valueOf(user.get("username")), role = String.valueOf(user.get("role"));
            audit(uid, "PASSWORD_LOGIN", "SUCCESS");
            return new LoginOutcome(new AuthResponse(tokenService.issueToken(uid, username, role), new UserBrief(uid, username, String.valueOf(user.get("nickname")), role)), null);
        });
        if (outcome.error() != null) throw outcome.error();
        return Api.ok(outcome.response());
    }

    private record LoginOutcome(AuthResponse response, Api.ApiException error) {}

    @GetMapping("/me")
    public Api.ApiResponse<UserProfile> me() {
        AuthContext.User p = AuthContext.requireUser();
        Map<String, Object> u = jdbcTemplate.queryForMap("""
                SELECT id, username, student_no, nickname, avatar_url, college, campus, major, grade, education_level, bio,
                       email, email_verified, phone, phone_verified, role
                FROM users WHERE id = ? LIMIT 1
                """, p.id());
        List<String> interests = jdbcTemplate.queryForList("""
                SELECT t.name
                FROM user_interest ui
                JOIN tags t ON t.id = ui.tag_id
                WHERE ui.user_id = ?
                ORDER BY t.name
                """, String.class, p.id());
        return Api.ok(new UserProfile(
                ((Number) u.get("id")).longValue(),
                String.valueOf(u.get("username")),
                u.get("student_no") == null ? null : String.valueOf(u.get("student_no")),
                String.valueOf(u.get("nickname")),
                u.get("avatar_url") == null ? null : String.valueOf(u.get("avatar_url")),
                u.get("college") == null ? null : String.valueOf(u.get("college")),
                u.get("campus") == null ? null : String.valueOf(u.get("campus")),
                u.get("major") == null ? null : String.valueOf(u.get("major")),
                u.get("grade") == null ? null : String.valueOf(u.get("grade")),
                u.get("education_level") == null ? null : String.valueOf(u.get("education_level")),
                u.get("bio") == null ? null : String.valueOf(u.get("bio")),
                u.get("email") == null ? null : String.valueOf(u.get("email")),
                u.get("email_verified") != null && ((Number) u.get("email_verified")).intValue() == 1,
                u.get("phone") == null ? null : String.valueOf(u.get("phone")),
                u.get("phone_verified") != null && ((Number) u.get("phone_verified")).intValue() == 1,
                String.valueOf(u.get("role")),
                interests
        ));
    }

    @Transactional
    @PutMapping("/me/interests")
    public Api.ApiResponse<Void> updateInterests(@RequestBody UpdateInterestsRequest req) {
        AuthContext.User p = AuthContext.requireUser();
        if (req == null || req.tags() == null || req.tags().size() > 50) throw new Api.ApiException(HttpStatus.BAD_REQUEST, "参数错误");
        for (String tag : req.tags()) {
            if (tag == null || !jdbcTemplate.queryForList("SELECT id FROM tags WHERE name = ?", Long.class, tag.trim()).stream().findAny().isPresent()) throw new Api.ApiException(HttpStatus.BAD_REQUEST, "标签不存在");
        }
        jdbcTemplate.update("DELETE FROM user_interest WHERE user_id = ?", p.id());
        if (req.tags() != null) {
            for (String tagName : req.tags()) {
                if (tagName == null || tagName.isBlank()) continue;
                Long tagId = jdbcTemplate.queryForObject("SELECT id FROM tags WHERE name = ? LIMIT 1", Long.class, tagName.trim());
                if (tagId == null) continue;
                jdbcTemplate.update("INSERT IGNORE INTO user_interest (user_id, tag_id) VALUES (?, ?)", p.id(), tagId);
            }
        }
        return Api.ok();
    }

    @PostMapping("/password/change")
    public Api.ApiResponse<Void> changePassword(@RequestBody ChangePasswordRequest req) {
        AuthContext.User p = AuthContext.requireUser();
        throw new Api.ApiException(HttpStatus.BAD_REQUEST, "为保障安全，请使用验证码修改密码");
    }

    @PostMapping("/password/reset")
    public Api.ApiResponse<Void> resetPassword(@RequestBody ResetPasswordRequest req) {
        if (req == null) throw new Api.ApiException(HttpStatus.BAD_REQUEST, "参数错误");
        String email = mustTrim(req.email(), 5, 128, "邮箱").toLowerCase();
        if (!isValidEmail(email)) throw new Api.ApiException(HttpStatus.BAD_REQUEST, "邮箱格式不正确");
        String newPwd = mustTrim(req.newPassword(), 8, 128, "新密码");
        ensureStrongPassword(newPwd);
        return emailCodeService.consume(email, "RESET", req.code(), () -> {
        List<Long> rows = jdbcTemplate.queryForList("SELECT id FROM users WHERE email = ? AND email_verified = 1 LIMIT 1 FOR UPDATE", Long.class, email);
        if (rows.isEmpty()) throw new Api.ApiException(HttpStatus.NOT_FOUND, "该邮箱未绑定账号");
        jdbcTemplate.update("UPDATE users SET password_hash = ?, token_version = token_version + 1, failed_login_count = 0, lock_until = NULL WHERE id = ?", passwordHasher.hash(newPwd), rows.get(0));
        audit(rows.get(0), "PASSWORD_RESET", "SUCCESS");
        return Api.ok();
        });
    }

    @PutMapping("/me/email/bind")
    public Api.ApiResponse<Void> bindEmail(@RequestBody BindEmailRequest req) {
        AuthContext.User p = AuthContext.requireUser();
        if (req == null) throw new Api.ApiException(HttpStatus.BAD_REQUEST, "参数错误");
        Map<String, Object> cur = jdbcTemplate.queryForMap("SELECT email_verified FROM users WHERE id = ? LIMIT 1", p.id());
        boolean verified = cur.get("email_verified") != null && ((Number) cur.get("email_verified")).intValue() == 1;
        if (verified) {
            throw new Api.ApiException(HttpStatus.CONFLICT, "该账号已绑定邮箱，可使用换绑功能");
        }
        String email = mustTrim(req.email(), 5, 128, "邮箱").toLowerCase();
        if (!isValidEmail(email)) throw new Api.ApiException(HttpStatus.BAD_REQUEST, "邮箱格式不正确");
        return emailCodeService.consume(email, "BIND", req.code(), () -> {
        requireCurrentAccount(p);
        if (jdbcTemplate.queryForObject("SELECT email_verified FROM users WHERE id = ?", Integer.class, p.id()) != 0) throw new Api.ApiException(HttpStatus.CONFLICT, "该账号已绑定邮箱，可使用换绑功能");
        Long used = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE email = ? AND email_verified = 1 AND id <> ?", Long.class, email, p.id());
        if (used != null && used > 0) throw new Api.ApiException(HttpStatus.CONFLICT, "邮箱已被绑定");
        jdbcTemplate.update("UPDATE users SET email = ?, email_verified = 1 WHERE id = ?", email, p.id());
        return Api.ok();
        });
    }

    @PostMapping("/me/email/change/send-old")
    public Api.ApiResponse<Map<String, Object>> sendChangeEmailOld() {
        AuthContext.User p = AuthContext.requireUser();
        Map<String, Object> u = jdbcTemplate.queryForMap("SELECT email, email_verified FROM users WHERE id = ? LIMIT 1", p.id());
        String email = u.get("email") == null ? null : String.valueOf(u.get("email")).trim().toLowerCase();
        boolean verified = u.get("email_verified") != null && ((Number) u.get("email_verified")).intValue() == 1;
        if (email == null || email.isBlank() || !verified) {
            throw new Api.ApiException(HttpStatus.BAD_REQUEST, "当前账号未绑定邮箱");
        }
        return Api.ok(emailCodeService.sendCode(email, "EMAIL_CHANGE_OLD"));
    }

    @PostMapping("/me/email/change/send-new")
    public Api.ApiResponse<Map<String, Object>> sendChangeEmailNew(@RequestBody Map<String, String> body) {
        AuthContext.User p = AuthContext.requireUser();
        String email = body == null ? null : emptyToNull(body.get("email"));
        if (email == null) throw new Api.ApiException(HttpStatus.BAD_REQUEST, "邮箱不能为空");
        email = email.toLowerCase();
        if (!isValidEmail(email)) throw new Api.ApiException(HttpStatus.BAD_REQUEST, "邮箱格式不正确");
        Long used = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE email = ? AND email_verified = 1 AND id <> ?", Long.class, email, p.id());
        if (used != null && used > 0) throw new Api.ApiException(HttpStatus.CONFLICT, "邮箱已被绑定");
        return Api.ok(emailCodeService.sendCode(email, "EMAIL_CHANGE_NEW"));
    }

    @PutMapping("/me/email/change")
    public Api.ApiResponse<Void> changeEmail(@RequestBody ChangeEmailRequest req) {
        AuthContext.User p = AuthContext.requireUser();
        if (req == null) throw new Api.ApiException(HttpStatus.BAD_REQUEST, "参数错误");
        String oldCode = mustTrim(req.oldCode(), 1, 32, "原邮箱验证码");
        String newEmail = mustTrim(req.newEmail(), 5, 128, "新邮箱").toLowerCase();
        String newCode = mustTrim(req.newCode(), 1, 32, "新邮箱验证码");
        if (!isValidEmail(newEmail)) throw new Api.ApiException(HttpStatus.BAD_REQUEST, "邮箱格式不正确");

        Map<String, Object> u = jdbcTemplate.queryForMap("SELECT email, email_verified FROM users WHERE id = ? LIMIT 1", p.id());
        String oldEmail = u.get("email") == null ? null : String.valueOf(u.get("email")).trim().toLowerCase();
        boolean verified = u.get("email_verified") != null && ((Number) u.get("email_verified")).intValue() == 1;
        if (oldEmail == null || oldEmail.isBlank() || !verified) {
            throw new Api.ApiException(HttpStatus.BAD_REQUEST, "当前账号未绑定邮箱");
        }
        return emailCodeService.consume(List.of(new EmailCodeService.Challenge(oldEmail, "EMAIL_CHANGE_OLD", oldCode), new EmailCodeService.Challenge(newEmail, "EMAIL_CHANGE_NEW", newCode)), () -> {
        requireCurrentAccount(p);
        if (jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE id = ? AND email = ? AND email_verified = 1", Long.class, p.id(), oldEmail) != 1) throw new Api.ApiException(HttpStatus.CONFLICT, "邮箱已变更，请重试");
        Long used = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE email = ? AND email_verified = 1 AND id <> ?", Long.class, newEmail, p.id());
        if (used != null && used > 0) throw new Api.ApiException(HttpStatus.CONFLICT, "邮箱已被绑定");
        jdbcTemplate.update("UPDATE users SET email = ?, email_verified = 1 WHERE id = ?", newEmail, p.id());
        return Api.ok();
        });
    }

    private void audit(long userId, String action, String result) {
        jdbcTemplate.update("INSERT INTO auth_audit (user_id, action, result) VALUES (?, ?, ?)", userId, action, result);
    }

    private void requireCurrentAccount(AuthContext.User user) {
        var rows = jdbcTemplate.queryForList("SELECT id FROM users WHERE id = ? AND token_version = ? AND status = 1 FOR UPDATE", user.id(), user.tokenVersion());
        if (rows.isEmpty()) throw new Api.ApiException(HttpStatus.UNAUTHORIZED, "未登录或登录已过期");
    }

    private static String emptyToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static boolean isValidEmail(String email) {
        return email.trim().matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    }

    private static LocalDateTime toLocalDateTime(Object value) {
        if (value == null) return null;
        if (value instanceof LocalDateTime ldt) return ldt;
        if (value instanceof java.sql.Timestamp ts) return ts.toLocalDateTime();
        if (value instanceof java.util.Date d) return new java.sql.Timestamp(d.getTime()).toLocalDateTime();
        return LocalDateTime.parse(String.valueOf(value).replace(" ", "T"));
    }

    private static void ensureStrongPassword(String password) {
        String p = password;
        boolean hasLower = p.chars().anyMatch(Character::isLowerCase);
        boolean hasUpper = p.chars().anyMatch(Character::isUpperCase);
        boolean hasDigit = p.chars().anyMatch(Character::isDigit);
        boolean hasSpecial = p.chars().anyMatch(ch -> !Character.isLetterOrDigit(ch));
        if (!(hasLower && hasUpper && hasDigit && hasSpecial)) {
            throw new Api.ApiException(HttpStatus.BAD_REQUEST, "密码需包含大小写字母、数字和特殊字符");
        }
    }

    private static String mustTrim(String s, int min, int max, String label) {
        if (s == null) throw new Api.ApiException(HttpStatus.BAD_REQUEST, label + "不能为空");
        String t = s.trim();
        if (t.isEmpty()) throw new Api.ApiException(HttpStatus.BAD_REQUEST, label + "不能为空");
        if (t.length() < min) throw new Api.ApiException(HttpStatus.BAD_REQUEST, label + "长度不能少于 " + min);
        if (t.length() > max) throw new Api.ApiException(HttpStatus.BAD_REQUEST, label + "长度不能超过 " + max);
        return t;
    }

    public record RegisterRequest(String username, String password, String nickname, String studentNo, String college, String email, String avatarUrl) {
    }

    public record LoginRequest(String usernameOrStudentNo, String password) {
    }

    public record EmailSendRequest(String email, String purpose) {
    }

    public record EmailLoginRequest(String email, String code) {
    }

    public record RegisterEmailRequest(String username, String password, String nickname, String studentNo, String college, String email, String code, String avatarUrl) {
    }

    public record ChangePasswordRequest(String oldPassword, String newPassword) {
    }

    public record ChangePasswordEmailRequest(String code, String newPassword) {
    }

    public record ResetPasswordRequest(String email, String code, String newPassword) {
    }

    public record BindEmailRequest(String email, String code) {
    }

    public record ChangeEmailRequest(String oldCode, String newEmail, String newCode) {
    }

    public record UpdateInterestsRequest(List<String> tags) {
    }

    public record AuthResponse(String token, UserBrief user) {
    }

    public record UserBrief(long id, String username, String nickname, String role) {
    }

    public record UserProfile(long id, String username, String studentNo, String nickname, String avatarUrl,
                              String college, String campus, String major, String grade, String educationLevel, String bio,
                              String email, boolean emailVerified, String phone, boolean phoneVerified,
                              String role, List<String> interests) {
    }
}
