package com.example.healingagent.model;

public record IncidentReport(
        String incidentId,
        String alertName,
        String namespace,
        String deploymentName,
        String alertStartedAt,
        String receivedAt,
        KubernetesEvidence evidence,
        String actionTaken,
        String recoveryStatus,
        String githubIssueUrl
) {}
