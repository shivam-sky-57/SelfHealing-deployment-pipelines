package com.example.demoapp.controller;

import com.example.demoapp.model.ApiResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DemoController {

    private static final Logger log = LoggerFactory.getLogger(DemoController.class);

    @Value("${app.force-failure:false}")
    private boolean forceFailure;

    private final Counter requestsCounter;
    private final Counter errorsCounter;

    public DemoController(MeterRegistry registry) {
        this.requestsCounter = Counter.builder("demo_requests_total")
                .description("Total requests to GET /")
                .register(registry);
        this.errorsCounter = Counter.builder("demo_errors_total")
                .description("Total errors thrown by demo-app")
                .register(registry);
    }

    @GetMapping("/")
    public ResponseEntity<ApiResponse> hello() {
        requestsCounter.increment();
        if (forceFailure) {
            log.warn("FORCE_FAILURE enabled — simulating failure on GET /");
            errorsCounter.increment();
            throw new RuntimeException("Simulated failure: FORCE_FAILURE=true");
        }
        log.info("GET / — ok");
        return ResponseEntity.ok(new ApiResponse("ok", "Hello from demo-app", "1.0.0"));
    }

    @GetMapping("/fail")
    public ResponseEntity<ApiResponse> fail() {
        requestsCounter.increment();
        errorsCounter.increment();
        log.error("GET /fail — throwing simulated exception");
        throw new RuntimeException("Simulated failure: GET /fail was called");
    }
}
