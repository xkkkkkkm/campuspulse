package com.campuspulse.bootstrap;

import com.campuspulse.security.PasswordHasher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Creates the first production administrator once, without installing demo accounts. */
@Component
@Profile("prod")
public class ProductionBootstrap implements CommandLineRunner {
    private final JdbcTemplate db;
    private final PasswordHasher passwords;
    private final String username, password, email;
    public ProductionBootstrap(JdbcTemplate db, PasswordHasher passwords,
            @Value("${app.bootstrap.admin-username:}") String username,
            @Value("${app.bootstrap.admin-password:}") String password,
            @Value("${app.bootstrap.admin-email:}") String email) {
        this.db=db; this.passwords=passwords; this.username=username; this.password=password; this.email=email;
    }
    @Override @Transactional
    public void run(String... args) {
        db.queryForObject("SELECT id FROM admin_governance_lock WHERE id=1 FOR UPDATE", Integer.class);
        db.queryForObject("SELECT completed FROM app_initialization WHERE name='production-admin' FOR UPDATE", Integer.class);
        if (db.queryForObject("SELECT COUNT(*) FROM users WHERE role='ADMIN' AND status=1", Long.class)>0) return;
        if (!username.matches("[A-Za-z0-9_]{3,64}") || password.length()<16 || password.length()>128
                || password.chars().distinct().count()<8 || !email.matches("[^@\\s]{1,64}@[^@\\s]+\\.[^@\\s]+") || email.length()>128) {
            throw new IllegalStateException("A fresh prod database requires APP_ADMIN_USERNAME (3–64 letters/digits/_), APP_ADMIN_PASSWORD (16–128 characters), and APP_ADMIN_EMAIL");
        }
        // A collision fails rather than promoting or changing an existing account.
        db.update("INSERT INTO users(username,password_hash,nickname,email,email_verified,role,status) VALUES(?,?,?,?,1,'ADMIN',1)",
                username, passwords.hash(password), username, email.trim().toLowerCase(java.util.Locale.ROOT));
        db.update("UPDATE app_initialization SET completed=1,completed_at=NOW() WHERE name='production-admin'");
    }
}
