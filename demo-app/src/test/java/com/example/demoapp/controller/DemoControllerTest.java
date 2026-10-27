package com.example.demoapp.controller;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for {@link DemoController} in the default (non-failure) mode.
 *
 * <p>Uses {@link WebMvcTest} to load only the web layer — no full Spring context.
 * A {@link SimpleMeterRegistry} is provided so Micrometer counters work without
 * the Prometheus registry needing to be configured.
 *
 * <p>Test cases covered here (FORCE_FAILURE=false):
 * <ul>
 *   <li>GET / returns 200 OK with the correct JSON body</li>
 *   <li>GET /fail always returns 500 with an error JSON body</li>
 *   <li>Error response body always has the expected fields</li>
 * </ul>
 *
 * @see DemoControllerForcedFailureTest for tests with FORCE_FAILURE=true
 */
@WebMvcTest(controllers = {DemoController.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "app.force-failure=false")
class DemoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * Provide a simple in-memory MeterRegistry.
     * WebMvcTest does not load the full auto-configuration, so we supply one explicitly.
     */
    @TestConfiguration
    static class TestMeterRegistryConfig {
        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    // -------------------------------------------------------------------------
    // GET / — happy path
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("GET / returns 200 OK with the expected JSON body")
    void getRoot_returnsOk() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.status", is("ok")))
                .andExpect(jsonPath("$.message", is("Hello from demo-app")))
                .andExpect(jsonPath("$.version", is("1.0.0")));
    }

    // -------------------------------------------------------------------------
    // GET /fail — always fails
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("GET /fail always returns 500 with an error body")
    void getFail_alwaysReturns500() throws Exception {
        mockMvc.perform(get("/fail"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.status", is("error")))
                .andExpect(jsonPath("$.message", notNullValue()));
    }

    // -------------------------------------------------------------------------
    // Error response structure
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Error response always contains status, message, and version fields")
    void errorResponse_hasAllExpectedFields() throws Exception {
        mockMvc.perform(get("/fail"))
                .andExpect(jsonPath("$.status").exists())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.version").exists());
    }
}
