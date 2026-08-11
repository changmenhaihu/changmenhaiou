package com.guardian.config;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GuardianConfigTest {

    @Test
    void readsApiKeyFromEnvironment() {
        GuardianConfig config = new GuardianConfig(Map.of("DEEPSEEK_API_KEY", "sk-test-123"));
        assertEquals("sk-test-123", config.getDeepSeekApiKey());
    }

    @Test
    void returnsNullWhenApiKeyMissing() {
        GuardianConfig config = new GuardianConfig(Map.of());
        assertNull(config.getDeepSeekApiKey());
    }

    @Test
    void modelDefaultsToDeepSeekChat() {
        GuardianConfig config = new GuardianConfig(Map.of());
        assertEquals("deepseek-chat", config.getDeepSeekModel());
    }

    @Test
    void modelCanBeOverriddenByEnvironment() {
        GuardianConfig config = new GuardianConfig(Map.of("DEEPSEEK_MODEL", "deepseek-v4-flash"));
        assertEquals("deepseek-v4-flash", config.getDeepSeekModel());
    }
}
