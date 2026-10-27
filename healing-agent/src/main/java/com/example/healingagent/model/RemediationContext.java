package com.example.healingagent.model;

import java.time.Instant;

public record RemediationContext(
        String namespace,
        String deploymentName,
        Instant alertStartedAt,
        KubernetesEvidence evidence,
        int revisionCount
) {}
