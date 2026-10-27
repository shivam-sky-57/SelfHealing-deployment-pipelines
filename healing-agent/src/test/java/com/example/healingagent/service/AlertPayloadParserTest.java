package com.example.healingagent.service;

import com.example.healingagent.model.AlertPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AlertPayloadParser}.
 *
 * <p>These tests run without Spring — plain JUnit 5 + AssertJ.
 * This keeps them fast and makes the business logic easy to verify
 * in isolation.
 *
 * <p>Test cases:
 * <ul>
 *   <li>Valid payload — labels in commonLabels</li>
 *   <li>Labels only in individual alert (not in commonLabels)</li>
 *   <li>Missing labels — falls back to provided defaults</li>
 *   <li>Resolved alert — isFiring() returns false</li>
 *   <li>Null / empty maps — no NullPointerException</li>
 *   <li>Summary extracted from commonAnnotations and alert annotations</li>
 * </ul>
 */
class AlertPayloadParserTest {

    private AlertPayloadParser parser;

    @BeforeEach
    void setUp() {
        parser = new AlertPayloadParser();
    }

    // -------------------------------------------------------------------------
    // Helper builders
    // -------------------------------------------------------------------------

    /** Build a fully populated firing payload with labels in commonLabels. */
    private AlertPayload buildFiringPayload(String alertName, String namespace, String deployment) {
        AlertPayload.AlertDetail detail = new AlertPayload.AlertDetail(
                "firing",
                Map.of("alertname", alertName, "severity", "critical"),
                Map.of("summary", "Test alert summary"),
                "2024-01-01T12:00:00Z",
                "0001-01-01T00:00:00Z",
                "http://prometheus:9090/graph"
        );
        return new AlertPayload(
                "4",
                "firing",
                "healing-agent",
                Map.of("alertname", alertName, "namespace", namespace, "deployment", deployment),
                Map.of("summary", "High error rate in " + deployment),
                Map.of("alertname", alertName),
                List.of(detail),
                "http://alertmanager:9093"
        );
    }

    /** Build a payload that has labels only in the individual alert, not in commonLabels. */
    private AlertPayload buildPayloadWithLabelsOnlyInAlert() {
        AlertPayload.AlertDetail detail = new AlertPayload.AlertDetail(
                "firing",
                Map.of("alertname", "HighErrorRate", "namespace", "demo", "deployment", "demo-app"),
                Map.of("summary", "Alert-level summary"),
                "2024-06-01T09:00:00Z",
                null,
                null
        );
        return new AlertPayload(
                "4", "firing", "healing-agent",
                Collections.emptyMap(),   // no commonLabels
                Collections.emptyMap(),   // no commonAnnotations
                Collections.emptyMap(),   // no groupLabels
                List.of(detail),
                null
        );
    }

    /** Build a payload with no labels at all (tests the fallback defaults). */
    private AlertPayload buildPayloadWithNoLabels() {
        AlertPayload.AlertDetail detail = new AlertPayload.AlertDetail(
                "firing",
                Collections.emptyMap(),
                Collections.emptyMap(),
                "2024-06-01T09:00:00Z",
                null, null
        );
        return new AlertPayload(
                "4", "firing", "healing-agent",
                null, null, null,
                List.of(detail),
                null
        );
    }

    /** Build a resolved payload (not firing). */
    private AlertPayload buildResolvedPayload() {
        return new AlertPayload(
                "4", "resolved", "healing-agent",
                Map.of("alertname", "HighErrorRate"),
                Map.of("summary", "Resolved"),
                Map.of("alertname", "HighErrorRate"),
                List.of(),
                null
        );
    }

    // -------------------------------------------------------------------------
    // extractAlertName
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("extractAlertName returns the alertname from commonLabels")
    void extractAlertName_fromCommonLabels() {
        AlertPayload payload = buildFiringPayload("HighErrorRate", "demo", "demo-app");
        assertThat(parser.extractAlertName(payload)).isEqualTo("HighErrorRate");
    }

    @Test
    @DisplayName("extractAlertName returns UNKNOWN when no alertname label is present")
    void extractAlertName_returnsUnknown_whenMissing() {
        AlertPayload payload = buildPayloadWithNoLabels();
        assertThat(parser.extractAlertName(payload)).isEqualTo("UNKNOWN");
    }

    @Test
    @DisplayName("extractAlertName falls through to individual alert labels")
    void extractAlertName_fromAlertLabels_whenCommonLabelsEmpty() {
        AlertPayload payload = buildPayloadWithLabelsOnlyInAlert();
        assertThat(parser.extractAlertName(payload)).isEqualTo("HighErrorRate");
    }

    // -------------------------------------------------------------------------
    // extractNamespace
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("extractNamespace returns namespace from commonLabels")
    void extractNamespace_fromCommonLabels() {
        AlertPayload payload = buildFiringPayload("HighErrorRate", "demo", "demo-app");
        assertThat(parser.extractNamespace(payload, "default")).isEqualTo("demo");
    }

    @Test
    @DisplayName("extractNamespace falls back to default when label is absent")
    void extractNamespace_usesDefault_whenLabelAbsent() {
        AlertPayload payload = buildPayloadWithNoLabels();
        assertThat(parser.extractNamespace(payload, "my-default-ns")).isEqualTo("my-default-ns");
    }

    @Test
    @DisplayName("extractNamespace reads from individual alert labels when commonLabels empty")
    void extractNamespace_fromAlertLabels() {
        AlertPayload payload = buildPayloadWithLabelsOnlyInAlert();
        assertThat(parser.extractNamespace(payload, "default")).isEqualTo("demo");
    }

    // -------------------------------------------------------------------------
    // extractDeploymentName
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("extractDeploymentName returns deployment from commonLabels")
    void extractDeploymentName_fromCommonLabels() {
        AlertPayload payload = buildFiringPayload("HighErrorRate", "demo", "demo-app");
        assertThat(parser.extractDeploymentName(payload, "default-app")).isEqualTo("demo-app");
    }

    @Test
    @DisplayName("extractDeploymentName falls back to default when label is absent")
    void extractDeploymentName_usesDefault_whenLabelAbsent() {
        AlertPayload payload = buildPayloadWithNoLabels();
        assertThat(parser.extractDeploymentName(payload, "fallback-app")).isEqualTo("fallback-app");
    }

    // -------------------------------------------------------------------------
    // isFiring
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("isFiring returns true for status=firing")
    void isFiring_returnsTrue_forFiringAlert() {
        AlertPayload payload = buildFiringPayload("HighErrorRate", "demo", "demo-app");
        assertThat(parser.isFiring(payload)).isTrue();
    }

    @Test
    @DisplayName("isFiring returns false for status=resolved")
    void isFiring_returnsFalse_forResolvedAlert() {
        AlertPayload payload = buildResolvedPayload();
        assertThat(parser.isFiring(payload)).isFalse();
    }

    @Test
    @DisplayName("isFiring is case-insensitive")
    void isFiring_isCaseInsensitive() {
        AlertPayload payload = new AlertPayload(
                "4", "FIRING", "healing-agent",
                Map.of(), Map.of(), Map.of(), List.of(), null);
        assertThat(parser.isFiring(payload)).isTrue();
    }

    // -------------------------------------------------------------------------
    // extractStartedAt
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("extractStartedAt returns the startsAt from the first alert")
    void extractStartedAt_returnsTimestamp() {
        AlertPayload payload = buildFiringPayload("HighErrorRate", "demo", "demo-app");
        assertThat(parser.extractStartedAt(payload)).isEqualTo("2024-01-01T12:00:00Z");
    }

    @Test
    @DisplayName("extractStartedAt returns empty string when no alerts")
    void extractStartedAt_returnsEmpty_whenNoAlerts() {
        AlertPayload payload = buildResolvedPayload(); // has empty alert list
        assertThat(parser.extractStartedAt(payload)).isEmpty();
    }

    // -------------------------------------------------------------------------
    // extractSummary
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("extractSummary returns summary from commonAnnotations")
    void extractSummary_fromCommonAnnotations() {
        AlertPayload payload = buildFiringPayload("HighErrorRate", "demo", "demo-app");
        assertThat(parser.extractSummary(payload)).isEqualTo("High error rate in demo-app");
    }

    @Test
    @DisplayName("extractSummary falls back to individual alert annotation")
    void extractSummary_fromAlertAnnotation_whenCommonEmpty() {
        AlertPayload payload = buildPayloadWithLabelsOnlyInAlert();
        assertThat(parser.extractSummary(payload)).isEqualTo("Alert-level summary");
    }

    @Test
    @DisplayName("extractSummary returns default when no annotations present")
    void extractSummary_returnsDefault_whenNoAnnotations() {
        AlertPayload payload = buildPayloadWithNoLabels();
        assertThat(parser.extractSummary(payload)).isEqualTo("No summary provided");
    }

    // -------------------------------------------------------------------------
    // Null safety — no NullPointerException on malformed payloads
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Parser handles null commonLabels without throwing NPE")
    void parser_handlesNullCommonLabels_gracefully() {
        AlertPayload payload = buildPayloadWithNoLabels();
        // Must not throw
        assertThat(parser.extractAlertName(payload)).isNotNull();
        assertThat(parser.extractNamespace(payload, "demo")).isNotNull();
        assertThat(parser.extractDeploymentName(payload, "demo-app")).isNotNull();
        assertThat(parser.extractSeverity(payload)).isNotNull();
    }
}
