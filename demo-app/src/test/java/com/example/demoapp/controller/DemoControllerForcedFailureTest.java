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
 * Unit tests for {@link DemoController} when {@code FORCE_FAILURE=true}.
 *
 * <p>A separate test class is needed because {@code @TestPropertySource} is a
 * class-level annotation — it cannot be applied to individual test methods.
 *
 * <p>With FORCE_FAILURE enabled, GET / must behave exactly like GET /fail
 * (HTTP 500, error body) so that the Prometheus alert can be triggered
 * during a live demo without changing code or rebuilding the image.
 */
@WebMvcTest(controllers = {DemoController.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = "app.force-failure=true")
class DemoControllerForcedFailureTest {

    @Autowired
    private MockMvc mockMvc;

    @TestConfiguration
    static class TestMeterRegistryConfig {
        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    @Test
    @DisplayName("GET / returns 500 when FORCE_FAILURE=true")
    void getRoot_returns500_whenForceFailureEnabled() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.status", is("error")))
                .andExpect(jsonPath("$.message", containsString("FORCE_FAILURE")));
    }

    @Test
    @DisplayName("GET / error body contains all expected fields when FORCE_FAILURE=true")
    void getRoot_errorBody_hasAllFields_whenForceFailureEnabled() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(jsonPath("$.status").exists())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.version").exists());
    }
}
