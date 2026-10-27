package com.example.healingagent.service;

import com.example.healingagent.model.AlertPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

@Component
public class AlertPayloadParser {

    private static final Logger log = LoggerFactory.getLogger(AlertPayloadParser.class);

    public String extractAlertName(AlertPayload payload) {
        return findLabel(payload, "alertname").orElse("UNKNOWN");
    }

    public String extractNamespace(AlertPayload payload, String defaultNamespace) {
        return findLabel(payload, "namespace").orElseGet(() -> {
            log.warn("'namespace' label not found in alert, using default: {}", defaultNamespace);
            return defaultNamespace;
        });
    }

    public String extractDeploymentName(AlertPayload payload, String defaultDeploymentName) {
        return findLabel(payload, "deployment").orElseGet(() -> {
            log.warn("'deployment' label not found in alert, using default: {}", defaultDeploymentName);
            return defaultDeploymentName;
        });
    }

    public String extractSeverity(AlertPayload payload) {
        return findLabel(payload, "severity").orElse("unknown");
    }

    public String extractStartedAt(AlertPayload payload) {
        if (payload.alerts() == null || payload.alerts().isEmpty()) return "";
        return Optional.ofNullable(payload.alerts().get(0).startsAt()).orElse("");
    }

    public String extractSummary(AlertPayload payload) {
        Map<String, String> common = orEmpty(payload.commonAnnotations());
        if (common.containsKey("summary")) return common.get("summary");
        if (payload.alerts() != null && !payload.alerts().isEmpty()) {
            return orEmpty(payload.alerts().get(0).annotations()).getOrDefault("summary", "No summary provided");
        }
        return "No summary provided";
    }

    public boolean isFiring(AlertPayload payload) {
        return "firing".equalsIgnoreCase(payload.status());
    }

    private Optional<String> findLabel(AlertPayload payload, String key) {
        String v = orEmpty(payload.groupLabels()).get(key);
        if (v != null && !v.isBlank()) return Optional.of(v);
        v = orEmpty(payload.commonLabels()).get(key);
        if (v != null && !v.isBlank()) return Optional.of(v);
        if (payload.alerts() != null && !payload.alerts().isEmpty()) {
            v = orEmpty(payload.alerts().get(0).labels()).get(key);
            if (v != null && !v.isBlank()) return Optional.of(v);
        }
        return Optional.empty();
    }

    private Map<String, String> orEmpty(Map<String, String> map) {
        return map != null ? map : Collections.emptyMap();
    }
}
