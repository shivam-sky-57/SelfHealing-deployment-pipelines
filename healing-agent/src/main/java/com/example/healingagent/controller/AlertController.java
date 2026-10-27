package com.example.healingagent.controller;

import com.example.healingagent.model.AlertPayload;
import com.example.healingagent.model.AlertWebhookResponse;
import com.example.healingagent.service.IncidentResponseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AlertController {

    private static final Logger log = LoggerFactory.getLogger(AlertController.class);

    private final IncidentResponseService incidentResponseService;

    public AlertController(IncidentResponseService incidentResponseService) {
        this.incidentResponseService = incidentResponseService;
    }

    @PostMapping("/alerts")
    public ResponseEntity<AlertWebhookResponse> receiveAlert(@RequestBody AlertPayload payload) {
        log.info("POST /alerts — status={}, alerts={}",
                payload.status(),
                payload.alerts() != null ? payload.alerts().size() : 0);

        if (payload.alerts() == null || payload.alerts().isEmpty()) {
            log.warn("Received alert payload with no individual alerts — discarding");
            return ResponseEntity.badRequest()
                    .body(new AlertWebhookResponse("none", "rejected", "Payload contained no alerts"));
        }

        AlertWebhookResponse response = incidentResponseService.handleAlert(payload);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
}
