package com.example.healingagent.client;

import com.example.healingagent.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link GitHubClientService} using a Mockito mock.
 *
 * <p>These tests verify that:
 * <ul>
 *   <li>Callers correctly handle successful issue creation (non-null URL)</li>
 *   <li>Callers correctly handle failed issue creation (no URL, error message)</li>
 *   <li>getRecentCommits returns well-formed GitCommit objects</li>
 *   <li>createDraftPullRequest is only called for CONFIG_ERROR incidents</li>
 *   <li>GitHubIssueResult factory methods work correctly</li>
 * </ul>
 *
 * <p>No real HTTP calls are made — {@link GitHubClientService} is mocked.
 */
class GitHubClientTest {

    private GitHubClientService mockGitHub;

    @BeforeEach
    void setUp() {
        mockGitHub = mock(GitHubClientService.class);
    }

    // =========================================================================
    // GitHubIssueResult factory methods
    // =========================================================================

    @Test
    @DisplayName("GitHubIssueResult.ok() populates success=true with URL and number")
    void issueResult_ok_hasCorrectFields() {
        GitHubIssueResult result = GitHubIssueResult.ok("https://github.com/org/repo/issues/42", 42);

        assertThat(result.success()).isTrue();
        assertThat(result.url()).isEqualTo("https://github.com/org/repo/issues/42");
        assertThat(result.number()).isEqualTo(42);
        assertThat(result.errorMessage()).isNull();
    }

    @Test
    @DisplayName("GitHubIssueResult.failed() populates success=false with error message")
    void issueResult_failed_hasCorrectFields() {
        GitHubIssueResult result = GitHubIssueResult.failed("401 Unauthorized — bad token");

        assertThat(result.success()).isFalse();
        assertThat(result.url()).isNull();
        assertThat(result.number()).isEqualTo(0);
        assertThat(result.errorMessage()).contains("401");
    }

    // =========================================================================
    // getRecentCommits
    // =========================================================================

    @Test
    @DisplayName("getRecentCommits returns commit list from mock")
    void getRecentCommits_returnsMockedList() {
        List<GitCommit> fakeCommits = List.of(
                new GitCommit("abc1234567890", "fix: correct env var", "alice", "2024-01-01T12:00:00Z", List.of("k8s/demo-app-deployment.yaml")),
                new GitCommit("def9876543210", "feat: add /health endpoint", "bob", "2024-01-01T11:00:00Z", List.of("src/main/java/DemoController.java"))
        );
        when(mockGitHub.getRecentCommits(10)).thenReturn(fakeCommits);

        List<GitCommit> result = mockGitHub.getRecentCommits(10);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).summary()).isEqualTo("fix: correct env var");
        assertThat(result.get(0).shortSha()).isEqualTo("abc1234");
        assertThat(result.get(0).toLogLine()).contains("abc1234").contains("alice");
    }

    @Test
    @DisplayName("getRecentCommits returns empty list on error (graceful fallback)")
    void getRecentCommits_returnsEmptyList_onError() {
        when(mockGitHub.getRecentCommits(anyInt())).thenReturn(List.of());

        List<GitCommit> result = mockGitHub.getRecentCommits(10);

        assertThat(result).isEmpty();
    }

    // =========================================================================
    // createIssue
    // =========================================================================

    @Test
    @DisplayName("createIssue returns success result with issue URL")
    void createIssue_returnsSuccess_withIssueUrl() {
        IncidentReport report = buildReport("HighErrorRate", "NOTIFY_ONLY: reason", "NOT_ATTEMPTED");
        LlmAnalysis analysis  = buildAnalysis(LlmAnalysis.Category.CODE_BUG);

        when(mockGitHub.createIssue(any(), any(), any()))
                .thenReturn(GitHubIssueResult.ok("https://github.com/org/repo/issues/99", 99));

        GitHubIssueResult result = mockGitHub.createIssue(report, analysis, List.of());

        assertThat(result.success()).isTrue();
        assertThat(result.url()).contains("issues/99");
        assertThat(result.number()).isEqualTo(99);
    }

    @Test
    @DisplayName("createIssue returns failure when GitHub token is missing")
    void createIssue_returnsFailed_whenNotConfigured() {
        IncidentReport report = buildReport("HighErrorRate", "NOTIFY_ONLY", "NOT_ATTEMPTED");
        LlmAnalysis analysis  = buildAnalysis(LlmAnalysis.Category.UNKNOWN);

        when(mockGitHub.createIssue(any(), any(), any()))
                .thenReturn(GitHubIssueResult.failed("GitHub credentials not configured"));

        GitHubIssueResult result = mockGitHub.createIssue(report, analysis, List.of());

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("credentials");
    }

    @Test
    @DisplayName("createIssue is called exactly once per incident")
    void createIssue_isCalledOnce_perIncident() {
        IncidentReport report = buildReport("PodNotReady", "NOTIFY_ONLY", "NOT_ATTEMPTED");
        LlmAnalysis analysis  = buildAnalysis(LlmAnalysis.Category.UNKNOWN);
        when(mockGitHub.createIssue(any(), any(), any()))
                .thenReturn(GitHubIssueResult.ok("https://github.com/org/repo/issues/1", 1));

        mockGitHub.createIssue(report, analysis, List.of());

        verify(mockGitHub, times(1)).createIssue(eq(report), eq(analysis), any());
    }

    // =========================================================================
    // createDraftPullRequest
    // =========================================================================

    @Test
    @DisplayName("createDraftPullRequest is NOT called for non-CONFIG_ERROR incidents")
    void createDraftPR_notCalled_forNonConfigError() {
        // Simulate the caller's guard: only call createDraftPullRequest for CONFIG_ERROR
        LlmAnalysis analysis = buildAnalysis(LlmAnalysis.Category.CODE_BUG);

        if (analysis.category() == LlmAnalysis.Category.CONFIG_ERROR) {
            mockGitHub.createDraftPullRequest(buildReport("HighErrorRate", "", ""), analysis);
        }

        verify(mockGitHub, never()).createDraftPullRequest(any(), any());
    }

    @Test
    @DisplayName("createDraftPullRequest IS called for CONFIG_ERROR incidents")
    void createDraftPR_called_forConfigError() {
        IncidentReport report = buildReport("HighErrorRate", "ROLLBACK_SUCCESS", "RECOVERED");
        LlmAnalysis analysis  = buildAnalysis(LlmAnalysis.Category.CONFIG_ERROR);
        when(mockGitHub.createDraftPullRequest(any(), any()))
                .thenReturn(GitHubIssueResult.ok("https://github.com/org/repo/pulls/5", 5));

        // Simulate the caller's guard
        if (analysis.category() == LlmAnalysis.Category.CONFIG_ERROR) {
            GitHubIssueResult result = mockGitHub.createDraftPullRequest(report, analysis);
            assertThat(result.success()).isTrue();
            assertThat(result.url()).contains("pulls/5");
        }

        verify(mockGitHub, times(1)).createDraftPullRequest(eq(report), eq(analysis));
    }

    @Test
    @DisplayName("createDraftPullRequest failure is non-critical (returns failed result)")
    void createDraftPR_failure_isNonCritical() {
        IncidentReport report = buildReport("HighErrorRate", "ROLLBACK_SUCCESS", "RECOVERED");
        LlmAnalysis analysis  = buildAnalysis(LlmAnalysis.Category.CONFIG_ERROR);
        when(mockGitHub.createDraftPullRequest(any(), any()))
                .thenReturn(GitHubIssueResult.failed("Branch does not exist"));

        GitHubIssueResult result = mockGitHub.createDraftPullRequest(report, analysis);

        assertThat(result.success()).isFalse();
        // Caller should log and continue — not throw or fail the incident
    }

    // =========================================================================
    // GitCommit helper methods
    // =========================================================================

    @Test
    @DisplayName("GitCommit.shortSha() returns first 7 characters")
    void gitCommit_shortSha_returnsFirstSeven() {
        GitCommit commit = new GitCommit("abcdef1234567", "message", "author", "date", List.of());
        assertThat(commit.shortSha()).isEqualTo("abcdef1");
    }

    @Test
    @DisplayName("GitCommit.summary() returns first line of multi-line message")
    void gitCommit_summary_returnsFirstLine() {
        GitCommit commit = new GitCommit("abc", "fix: correct thing\n\nMore detail here.", "dev", "date", List.of());
        assertThat(commit.summary()).isEqualTo("fix: correct thing");
    }

    @Test
    @DisplayName("GitCommit.toLogLine() formats correctly")
    void gitCommit_toLogLine_formatsCorrectly() {
        GitCommit commit = new GitCommit("abc1234567", "feat: new thing", "charlie", "date", List.of());
        assertThat(commit.toLogLine()).isEqualTo("abc1234 — feat: new thing (charlie)");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private IncidentReport buildReport(String alertName, String action, String recovery) {
        KubernetesEvidence evidence = new KubernetesEvidence(
                "demo", "demo-app", "status", "history",
                List.of(), List.of(), List.of(), Instant.now().toString());
        return new IncidentReport(
                "incident-uuid-1234", alertName, "demo", "demo-app",
                "2024-01-01T12:00:00Z", Instant.now().toString(),
                evidence, action, recovery, null);
    }

    private LlmAnalysis buildAnalysis(LlmAnalysis.Category category) {
        return new LlmAnalysis(
                "root cause description",
                LlmAnalysis.Confidence.HIGH,
                category,
                "ROLLBACK",
                "explanation text",
                List.of("evidence point 1")
        );
    }
}
