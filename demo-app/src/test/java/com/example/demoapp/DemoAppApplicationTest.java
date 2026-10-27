package com.example.demoapp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

/**
 * Smoke test — verifies the full Spring application context loads successfully.
 *
 * <p>Uses a random port so there is no conflict if the test runs alongside
 * another process. WebEnvironment.RANDOM_PORT also starts the embedded Tomcat,
 * confirming that all beans and auto-configurations wire together correctly.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class DemoAppApplicationTest {

    @Test
    void contextLoads() {
        // If any bean fails to initialize, Spring throws an exception
        // and this test fails — which is exactly what we want.
    }
}
