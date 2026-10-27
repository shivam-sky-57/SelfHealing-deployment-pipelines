package com.example.healingagent.service;

import com.example.healingagent.config.HealingAgentProperties;
import com.example.healingagent.model.KubernetesEvidence;
import com.example.healingagent.model.RemediationContext;
import com.example.healingagent.model.RemediationDecision;
import com.example.healingagent.model.RemediationDecision.Action;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link RemediationPolicyService}.
 *
 * <p>Runs entirely without Spring — plain JUnit 5 + AssertJ.
 * Tests are structured around the five decision branches documented in the class.
 *
 * <p>Decision branches tested:
 * <ol>
 *   <li>COOLDOWN active → NOTIFY_ONLY</li>
 *   <li>COOLDOWN expired → allow rollback evaluation to continue</li>
 *   <li>NO_HISTORY (revisionCount &lt; 2) → NOTIFY_ONLY</li>
 *   <li>NOT_POST_DEPLOY (alert too old) → NOTIFY_ONLY</li>
 *   <li>ALL_GUARDS_PASSED → ROLLBACK</li>
 * </ol>
 */
class RemediationPolicyServiceTest {

    private RemediationPolicyService policy;

    // Use a real HealingAgentProperties with default values (5m cooldown, 10m window)
    private HealingAgentProperties properties;

    @BeforeEach
    void setUp() {
        properties = new HealingAgentProperties();
        policy = new RemediationPolicyService(properties);
    }

    // =========================================================================
    // Helper builders
    // =========================================================================

    private KubernetesEvidence stubEvidence(String namespace, String deployment) {
        return new KubernetesEvidence(
                namespace, deployment,
                "NAME       READY   UP-TO-DATE   AVAILABLE\n" + deployment + " 2/2     2            2",
                "REVISION  CHANGE-CAUSE\n1         initial\n2         bad deploy",
                List.of(deployment + "-abc Running 0 restarts"),
                List.of("No Warning events"),
                List.of("app started ok"),
                Instant.now().toString()
        );
    }

    /**
     * Builds a context where the alert is 2 minutes old (within the 10-minute window).
     */
    private RemediationContext recentContext(int revisionCount) {
        return new RemediationContext(
                "demo", "demo-app",
                Instant.now().minusSeconds(120),   // 2 minutes ago — recent
                stubEvidence("demo", "demo-app"),
                revisionCount
        );
    }

    /**
     * Builds a context where the alert is 20 minutes old (beyond the 10-minute window).
     */
    private RemediationContext oldAlertContext() {
        return new RemediationContext(
                "demo", "demo-app",
                Instant.now().minusSeconds(1200),  // 20 minutes ago — too old
                stubEvidence("demo", "demo-app"),
                5  // plenty of revisions
        );
    }

    // =========================================================================
    // Branch 1: COOLDOWN active
    // =========================================================================

    @Test
    @DisplayName("Branch 1: NOTIFY_ONLY when cooldown is active")
    void decide_returnsNotifyOnly_whenCooldownActive() {
        // Record a rollback just now to activate the cooldown
        policy.recordRollback("demo", "demo-app");

        RemediationDecision decision = policy.decide(recentContext(5));

        assertThat(decision.action()).isEqualTo(Action.NOTIFY_ONLY);
        assertThat(decision.reason()).contains("Cooldown active");
        assertThat(decision.rollbackSafe()).isFalse();
    }

    @Test
    @DisplayName("Branch 1: reason message includes time remaining in cooldown")
    void decide_cooldownReason_includesTimeRemaining() {
        policy.recordRollback("demo", "demo-app");

        RemediationDecision decision = policy.decide(recentContext(5));

        // Should mention "minute(s)" in the reason
        assertThat(decision.reason()).containsIgnoringCase("minute");
    }

    // =========================================================================
    // Branch 1b: COOLDOWN expired — policy continues to next guards
    // =========================================================================

    @Test
    @DisplayName("Branch 1b: cooldown cleared → decision proceeds to other guards")
    void decide_allowsRollback_afterCooldownCleared() {
        // Simulate a previous rollback, then clear it
        policy.recordRollback("demo", "demo-app");
        policy.clearCooldown("demo", "demo-app");

        RemediationDecision decision = policy.decide(recentContext(5));

        // With cooldown gone, recent alert, and 5 revisions → ROLLBACK
        assertThat(decision.action()).isEqualTo(Action.ROLLBACK);
    }

    // =========================================================================
    // Branch 2: NO_HISTORY
    // =========================================================================

    @Test
    @DisplayName("Branch 2: NOTIFY_ONLY when revision count is 0")
    void decide_returnsNotifyOnly_whenNoRevisionHistory() {
        RemediationDecision decision = policy.decide(recentContext(0));

        assertThat(decision.action()).isEqualTo(Action.NOTIFY_ONLY);
        assertThat(decision.reason()).containsIgnoringCase("revision");
    }

    @Test
    @DisplayName("Branch 2: NOTIFY_ONLY when revision count is exactly 1 (no previous to roll back to)")
    void decide_returnsNotifyOnly_whenOnlyOneRevision() {
        RemediationDecision decision = policy.decide(recentContext(1));

        assertThat(decision.action()).isEqualTo(Action.NOTIFY_ONLY);
        assertThat(decision.reason()).containsIgnoringCase("revision");
    }

    // =========================================================================
    // Branch 3: NOT_POST_DEPLOY (alert too old)
    // =========================================================================

    @Test
    @DisplayName("Branch 3: NOTIFY_ONLY when alert started more than 10 minutes ago")
    void decide_returnsNotifyOnly_whenAlertTooOld() {
        RemediationDecision decision = policy.decide(oldAlertContext());

        assertThat(decision.action()).isEqualTo(Action.NOTIFY_ONLY);
        assertThat(decision.reason()).containsIgnoringCase("post-deploy window");
    }

    @Test
    @DisplayName("Branch 3: null alertStartedAt is treated as recent (allows rollback)")
    void decide_treatsNullStartedAt_asRecent() {
        RemediationContext contextWithNullTimestamp = new RemediationContext(
                "demo", "demo-app",
                null,    // no timestamp — treated as recent
                stubEvidence("demo", "demo-app"),
                3
        );

        RemediationDecision decision = policy.decide(contextWithNullTimestamp);

        // No cooldown, 3 revisions, null → treated as recent → ROLLBACK
        assertThat(decision.action()).isEqualTo(Action.ROLLBACK);
    }

    // =========================================================================
    // Branch 4: ALL_GUARDS_PASSED → ROLLBACK
    // =========================================================================

    @Test
    @DisplayName("Branch 4: ROLLBACK when all guards pass (recent alert, ≥2 revisions, no cooldown)")
    void decide_returnsRollback_whenAllGuardsPass() {
        RemediationDecision decision = policy.decide(recentContext(3));

        assertThat(decision.action()).isEqualTo(Action.ROLLBACK);
        assertThat(decision.rollbackSafe()).isTrue();
        assertThat(decision.reason()).containsIgnoringCase("passed");
    }

    @Test
    @DisplayName("Branch 4: ROLLBACK works with exactly 2 revisions (minimum required)")
    void decide_returnsRollback_withExactlyTwoRevisions() {
        RemediationDecision decision = policy.decide(recentContext(2));

        assertThat(decision.action()).isEqualTo(Action.ROLLBACK);
    }

    // =========================================================================
    // Cooldown tracking helpers
    // =========================================================================

    @Test
    @DisplayName("cooldownMinutesRemaining returns 0 when no cooldown recorded")
    void cooldownMinutesRemaining_returnsZero_whenNoCooldown() {
        long remaining = policy.cooldownMinutesRemaining("demo", "demo-app");
        assertThat(remaining).isEqualTo(0);
    }

    @Test
    @DisplayName("cooldownMinutesRemaining returns positive value immediately after rollback")
    void cooldownMinutesRemaining_returnsPositive_afterRollback() {
        policy.recordRollback("demo", "demo-app");
        long remaining = policy.cooldownMinutesRemaining("demo", "demo-app");
        // Should be ~5 (the default cooldown) since we just recorded it
        assertThat(remaining).isGreaterThan(0).isLessThanOrEqualTo(5);
    }

    @Test
    @DisplayName("Cooldowns are independent per deployment key")
    void cooldowns_areIndependent_perDeployment() {
        policy.recordRollback("demo", "demo-app");
        // A different deployment should not be in cooldown
        RemediationDecision decision = policy.decide(new RemediationContext(
                "demo", "other-app",
                Instant.now().minusSeconds(60),
                stubEvidence("demo", "other-app"),
                3
        ));
        assertThat(decision.action()).isEqualTo(Action.ROLLBACK);
    }
}
