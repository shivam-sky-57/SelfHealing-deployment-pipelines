package com.example.healingagent.service;

import com.example.healingagent.config.HealingAgentProperties;
import com.example.healingagent.model.RemediationContext;
import com.example.healingagent.model.RemediationDecision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Decides whether to roll back a deployment.
 *
 * Guards (evaluated in order):
 *   1. Cooldown — refuse if a rollback was issued recently
 *   2. No history — refuse if no previous revision exists
 *   3. Post-deploy window — refuse if alert is older than N minutes
 *   4. All guards passed → ROLLBACK
 */
@Service
public class RemediationPolicyService {

    private static final Logger log = LoggerFactory.getLogger(RemediationPolicyService.class);

    private final HealingAgentProperties properties;
    private final Map<String, Instant> lastRollbackTime = new ConcurrentHashMap<>();

    public RemediationPolicyService(HealingAgentProperties properties) {
        this.properties = properties;
    }

    public RemediationDecision decide(RemediationContext context) {
        String key = context.namespace() + "/" + context.deploymentName();
        log.info("[Policy] Evaluating remediation for: {}", key);

        RemediationDecision cooldownDecision = checkCooldown(key);
        if (cooldownDecision != null) {
            log.warn("[Policy] {} — DECISION: {}", key, cooldownDecision);
            return cooldownDecision;
        }

        if (context.revisionCount() < 2) {
            String reason = String.format(
                "Cannot roll back %s: only %d revision(s) in history (need ≥2).", key, context.revisionCount());
            log.warn("[Policy] {} — DECISION: NOTIFY_ONLY ({})", key, reason);
            return RemediationDecision.notifyOnly(reason);
        }

        RemediationDecision postDeployDecision = checkPostDeployWindow(key, context);
        if (postDeployDecision != null) {
            log.warn("[Policy] {} — DECISION: {}", key, postDeployDecision);
            return postDeployDecision;
        }

        String reason = String.format(
            "All safety guards passed for %s: revisions=%d, alert is recent, no active cooldown. Executing rollback.",
            key, context.revisionCount());
        log.info("[Policy] {} — DECISION: ROLLBACK ({})", key, reason);
        return RemediationDecision.rollback(reason);
    }

    public void recordRollback(String namespace, String deploymentName) {
        String key = namespace + "/" + deploymentName;
        lastRollbackTime.put(key, Instant.now());
        log.info("[Policy] Rollback recorded for {} — cooldown active for {} minutes",
                key, properties.getRemediation().cooldownMinutes());
    }

    public void clearCooldown(String namespace, String deploymentName) {
        lastRollbackTime.remove(namespace + "/" + deploymentName);
    }

    public long cooldownMinutesRemaining(String namespace, String deploymentName) {
        Instant last = lastRollbackTime.get(namespace + "/" + deploymentName);
        if (last == null) return 0;
        long cooldown = properties.getRemediation().cooldownMinutes();
        long elapsed = Duration.between(last, Instant.now()).toMinutes();
        return Math.max(0, cooldown - elapsed);
    }

    private RemediationDecision checkCooldown(String key) {
        Instant lastRollback = lastRollbackTime.get(key);
        if (lastRollback == null) return null;

        int cooldownMinutes = properties.getRemediation().cooldownMinutes();
        Duration elapsed = Duration.between(lastRollback, Instant.now());

        if (elapsed.toMinutes() < cooldownMinutes) {
            long remaining = cooldownMinutes - elapsed.toMinutes();
            return RemediationDecision.notifyOnly(
                String.format("Cooldown active for %s: last rollback was %d minute(s) ago. Next allowed in %d minute(s). This prevents rollback thrashing.",
                        key, elapsed.toMinutes(), remaining));
        }

        lastRollbackTime.remove(key);
        return null;
    }

    private RemediationDecision checkPostDeployWindow(String key, RemediationContext context) {
        if (context.alertStartedAt() == null) return null;

        int windowMinutes = properties.getRemediation().postDeployWindowMinutes();
        Duration alertAge = Duration.between(context.alertStartedAt(), Instant.now());

        if (alertAge.toMinutes() > windowMinutes) {
            return RemediationDecision.notifyOnly(
                String.format("Alert for %s started %d minute(s) ago, beyond the post-deploy window of %d minutes. Manual investigation recommended.",
                        key, alertAge.toMinutes(), windowMinutes));
        }
        return null;
    }
}
