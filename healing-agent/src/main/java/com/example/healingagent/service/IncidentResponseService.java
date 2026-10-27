package com.example.healingagent.service;

import com.example.healingagent.client.GitHubClientService;
import com.example.healingagent.client.KubernetesClientService;
import com.example.healingagent.client.LlmAnalysisService;
import com.example.healingagent.config.HealingAgentProperties;
import com.example.healingagent.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

/**
 * Orchestrates the full incident-response lifecycle for a single alert.
 *
 * <h2>Complete lifecycle (Phases 6–9)</h2>
 * <ol>
 *   <li>Parse alert payload → extract namespace, deployment, alertName</li>
 *   <li>Collect Kubernetes evidence (kubectl)</li>
 *   <li>Fetch recent GitHub commits (GitHub REST API)</li>
 *   <li>Send evidence + commits to Groq LLM for root-cause analysis</li>
 *   <li>Apply Java remediation policy (5 safety guards)</li>
 *   <li>Execute rollback if safe, otherwise NOTIFY_ONLY</li>
 *   <li>Create GitHub issue with full incident report</li>
 *   <li>If category == CONFIG_ERROR: create draft pull request</li>
 * </ol>
 *
 * <p><strong>Architecture rules:</strong>
 * <ul>
 *   <li>The LLM is advisory — it never directly triggers kubectl commands</li>
 *   <li>The rollback policy has final authority over all remediation decisions</li>
 *   <li>No exception escapes this class — every failure is logged and handled gracefully</li>
 * </ul>
 */
@Service
public class IncidentResponseService {

    private static final Logger log = LoggerFactory.getLogger(IncidentResponseService.class);

    private static final int RECENT_COMMITS_COUNT = 10;

    private final AlertPayloadParser parser;
    private final EvidenceCollectionService evidenceService;
    private final RemediationPolicyService policyService;
    private final KubernetesClientService k8sClient;
    private final LlmAnalysisService llmService;
    private final GitHubClientService githubClient;
    private final HealingAgentProperties properties;

    public IncidentResponseService(AlertPayloadParser parser,
                                   EvidenceCollectionService evidenceService,
                                   RemediationPolicyService policyService,
                                   KubernetesClientService k8sClient,
                                   LlmAnalysisService llmService,
                                   GitHubClientService githubClient,
                                   HealingAgentProperties properties) {
        this.parser        = parser;
        this.evidenceService = evidenceService;
        this.policyService = policyService;
        this.k8sClient     = k8sClient;
        this.llmService    = llmService;
        this.githubClient  = githubClient;
        this.properties    = properties;
    }

    /**
     * Synchronous entry point — returns 202 immediately; all work is async.
     */
    public AlertWebhookResponse handleAlert(AlertPayload payload) {
        String incidentId = UUID.randomUUID().toString();
        log.info("[Incident {}] Alert received: status={}, receiver={}",
                incidentId, payload.status(), payload.receiver());

        if (!parser.isFiring(payload)) {
            log.info("[Incident {}] Status='{}' — ignoring", incidentId, payload.status());
            return new AlertWebhookResponse(incidentId, "ignored",
                    "Alert is not in 'firing' status — no action taken");
        }

        processIncident(incidentId, payload);
        return new AlertWebhookResponse(incidentId, "received",
                "Incident " + incidentId + " is being processed");
    }

    /**
     * Full incident-response pipeline, running asynchronously.
     * Never throws — all exceptions are caught and logged.
     */
    @Async
    public void processIncident(String incidentId, AlertPayload payload) {
        log.info("[Incident {}] ===== INCIDENT PROCESSING STARTED =====", incidentId);

        try {
            // ── Step 1: Parse alert context ───────────────────────────────────
            String alertName    = parser.extractAlertName(payload);
            String namespace    = parser.extractNamespace(payload, properties.getKubernetes().namespace());
            String deployment   = parser.extractDeploymentName(payload, properties.getKubernetes().deploymentName());
            String severity     = parser.extractSeverity(payload);
            String alertStarted = parser.extractStartedAt(payload);
            String receivedAt   = Instant.now().toString();

            log.info("[Incident {}] alert={} severity={} namespace={} deployment={}",
                    incidentId, alertName, severity, namespace, deployment);

            // ── Step 2: Collect Kubernetes evidence ───────────────────────────
            log.info("[Incident {}] Collecting Kubernetes evidence...", incidentId);
            KubernetesEvidence evidence = evidenceService.collect(namespace, deployment);
            log.info("[Incident {}] Evidence: {} pods, {} events, {} log lines",
                    incidentId, evidence.podStatuses().size(),
                    evidence.recentEvents().size(), evidence.containerLogs().size());

            // ── Step 3: Fetch recent GitHub commits ───────────────────────────
            log.info("[Incident {}] Fetching recent GitHub commits...", incidentId);
            List<GitCommit> commits = githubClient.getRecentCommits(RECENT_COMMITS_COUNT);
            log.info("[Incident {}] Fetched {} commits", incidentId, commits.size());

            List<String> commitLines = commits.stream()
                    .map(GitCommit::toLogLine)
                    .toList();

            // ── Step 4: LLM root-cause analysis ──────────────────────────────
            log.info("[Incident {}] Sending evidence to Groq LLM...", incidentId);
            LlmAnalysis llmAnalysis = llmService.analyze(
                    alertName, namespace, deployment, evidence, commitLines);

            log.info("[Incident {}] LLM: category={} confidence={} action={}",
                    incidentId, llmAnalysis.category(), llmAnalysis.confidence(),
                    llmAnalysis.recommendedAction());
            log.info("[Incident {}] LLM rootCause: {}", incidentId, llmAnalysis.rootCause());

            // ── Step 5: Remediation policy decision ───────────────────────────
            int revisionCount = k8sClient.getRevisionCount(namespace, deployment);
            RemediationContext context = new RemediationContext(
                    namespace, deployment, parseInstant(alertStarted), evidence, revisionCount);

            RemediationDecision decision = policyService.decide(context);
            log.info("[Incident {}] Policy: {} — {}", incidentId, decision.action(), decision.reason());

            if ("ROLLBACK".equals(llmAnalysis.recommendedAction())
                    && decision.action() != RemediationDecision.Action.ROLLBACK) {
                log.info("[Incident {}] NOTE: LLM recommended ROLLBACK but policy chose {} — policy wins",
                        incidentId, decision.action());
            }

            // ── Step 6: Execute remediation ───────────────────────────────────
            String actionTaken;
            String recoveryStatus;

            if (decision.action() == RemediationDecision.Action.ROLLBACK) {
                actionTaken    = executeRollback(incidentId, namespace, deployment);
                recoveryStatus = actionTaken.startsWith("ROLLBACK_SUCCESS") ? "RECOVERED" : "FAILED";
            } else {
                actionTaken    = "NOTIFY_ONLY: " + decision.reason();
                recoveryStatus = "NOT_ATTEMPTED";
            }

            // ── Step 7: Build final incident report ───────────────────────────
            IncidentReport report = new IncidentReport(
                    incidentId, alertName, namespace, deployment,
                    alertStarted, receivedAt, evidence,
                    actionTaken, recoveryStatus,
                    null    // filled in below after issue creation
            );

            // ── Step 8: Create GitHub issue ───────────────────────────────────
            log.info("[Incident {}] Creating GitHub issue...", incidentId);
            GitHubIssueResult issueResult = githubClient.createIssue(report, llmAnalysis, commits);

            if (issueResult.success()) {
                log.info("[Incident {}] GitHub issue created: {}", incidentId, issueResult.url());
            } else {
                log.warn("[Incident {}] GitHub issue creation failed: {}", incidentId, issueResult.errorMessage());
            }

            // Rebuild report with the issue URL now populated
            IncidentReport finalReport = new IncidentReport(
                    report.incidentId(), report.alertName(), report.namespace(),
                    report.deploymentName(), report.alertStartedAt(), report.receivedAt(),
                    report.evidence(), report.actionTaken(), report.recoveryStatus(),
                    issueResult.success() ? issueResult.url() : null
            );

            // ── Step 9: Draft PR for config errors ────────────────────────────
            if (llmAnalysis.category() == LlmAnalysis.Category.CONFIG_ERROR) {
                log.info("[Incident {}] LLM identified CONFIG_ERROR — attempting draft PR...", incidentId);
                GitHubIssueResult prResult = githubClient.createDraftPullRequest(finalReport, llmAnalysis);
                if (prResult.success()) {
                    log.info("[Incident {}] Draft PR created: {}", incidentId, prResult.url());
                } else {
                    log.info("[Incident {}] Draft PR not created (non-critical): {}",
                            incidentId, prResult.errorMessage());
                }
            }

            // ── Final summary ──────────────────────────────────────────────────
            log.info("[Incident {}] ===== INCIDENT COMPLETE =====", incidentId);
            log.info("[Incident {}] action={} recovery={} issue={}",
                    incidentId, actionTaken, recoveryStatus,
                    issueResult.success() ? issueResult.url() : "not-created");

        } catch (Exception e) {
            log.error("[Incident {}] Unexpected error: {}", incidentId, e.getMessage(), e);
        }
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private String executeRollback(String incidentId, String namespace, String deployment) {
        log.warn("[Incident {}] *** EXECUTING ROLLBACK: {}/{} ***", incidentId, namespace, deployment);

        boolean issued = k8sClient.executeRollback(namespace, deployment);
        if (!issued) return "ROLLBACK_FAILED: kubectl rollout undo returned non-zero exit code";

        policyService.recordRollback(namespace, deployment);
        log.info("[Incident {}] Cooldown recorded for {}/{}", incidentId, namespace, deployment);

        boolean recovered = k8sClient.verifyRecovery(namespace, deployment);
        if (recovered) {
            log.info("[Incident {}] ✓ Recovery verified for {}/{}", incidentId, namespace, deployment);
            return "ROLLBACK_SUCCESS: deployment recovered after rollback";
        } else {
            log.error("[Incident {}] ✗ Recovery NOT verified for {}/{}", incidentId, namespace, deployment);
            return "ROLLBACK_ISSUED_RECOVERY_UNVERIFIED: rollback executed but not confirmed healthy";
        }
    }

    private Instant parseInstant(String ts) {
        if (ts == null || ts.isBlank()) return null;
        try { return Instant.parse(ts); }
        catch (DateTimeParseException e) {
            log.warn("Could not parse timestamp '{}' — treating as recent", ts);
            return null;
        }
    }
}
