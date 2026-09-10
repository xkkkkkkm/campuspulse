package com.campuspulse.config;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ProductionSafetyConfigTest {
    @Test void refusesEveryPublishedPlaceholder() {
        for (String secret : new String[]{"replace-with-a-random-secret-at-least-32-characters", "replace-with-a-random-email-secret", "replace-with-a-random-sms-secret", "development-only-token-secret-change-me", "a".repeat(64)})
            assertThrows(IllegalStateException.class, () -> ProductionSafetyConfig.requireStrongSecret("TEST", secret));
        assertDoesNotThrow(() -> ProductionSafetyConfig.requireStrongSecret("TEST", "Y9qS6cO3tP1xM7jA5vL0rK8dH4bG2nFz"));
    }
    @Test void rejectsReusedSecretsAndDevCodes() {
        String strong = "Y9qS6cO3tP1xM7jA5vL0rK8dH4bG2nFz";
        assertThrows(IllegalStateException.class, () -> new ProductionSafetyConfig(strong, strong, false).validate());
        assertThrows(IllegalStateException.class, () -> new ProductionSafetyConfig(strong, strong+"extra", true).validate());
    }
}
