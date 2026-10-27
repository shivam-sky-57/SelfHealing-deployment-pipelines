package com.example.healingagent;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

/**
 * Smoke test — verifies the full Spring application context loads successfully.
 *
 * <p>If any bean fails to initialize (bad @Value binding, missing required
 * dependency, circular reference, etc.) this test will fail with a clear error.
 *
 * <p>Uses RANDOM_PORT to avoid conflicts with other running processes.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class HealingAgentApplicationTest {

    @Test
    void contextLoads() {
        // Context loading is the assertion — no additional checks needed here.
    }
}
