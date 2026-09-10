package com.campuspulse.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import java.util.Locale;

@Component
@Profile("prod")
public class ProductionSafetyConfig {
    private final String tokenSecret;
    private final String emailSecret;
    private final boolean emailDevMode;

    public ProductionSafetyConfig(@Value("${app.security.token-secret}") String tokenSecret,
                                  @Value("${app.security.email-secret}") String emailSecret,
                                  @Value("${app.security.email-dev-mode:false}") boolean emailDevMode) {
        this.tokenSecret = tokenSecret;
        this.emailSecret = emailSecret;
        this.emailDevMode = emailDevMode;
    }

    @PostConstruct
    void validate() {
        requireStrongSecret("APP_SECURITY_TOKEN_SECRET", tokenSecret);
        requireStrongSecret("APP_SECURITY_EMAIL_SECRET", emailSecret);
        if (tokenSecret.equals(emailSecret)) throw new IllegalStateException("Signing and verification secrets must be distinct");
        if (emailDevMode) throw new IllegalStateException("Development verification codes must be disabled in prod");
    }

    static void requireStrongSecret(String name, String value) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT);
        if (value == null || value.length() < 32 || normalized.contains("change-me")
                || normalized.contains("development") || normalized.contains("replace-with")
                || normalized.contains("example") || normalized.contains("your-secret")
                || normalized.contains("changeme") || normalized.contains("demo")
                || value.chars().distinct().count() < 12) {
            throw new IllegalStateException(name + " must be a unique random secret, at least 32 characters; generate with openssl rand -hex 32");
        }
    }
}
