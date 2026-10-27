package com.example.healingagent.client;

import com.example.healingagent.model.GitCommit;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

/**
 * Integration test placeholder for {@link GitHubClientService}.
 *
 * <p><strong>These tests are @Disabled and will not run in CI.</strong>
 *
 * <h2>How to enable these tests</h2>
 * <ol>
 *   <li>Set the required environment variables:
 *       <pre>
 *       $env:GITHUB_TOKEN = "ghp_..."        # personal access token
 *       $env:GITHUB_OWNER = "your-username"
 *       $env:GITHUB_REPO  = "self-healing-deployment-pipeline"
 *       </pre></li>
 *   <li>Make sure the repository exists on GitHub</li>
 *   <li>Remove the {@code @Disabled} annotation below</li>
 *   <li>Run: {@code mvn -pl healing-agent test -Dtest=GitHubClientIntegrationTest}</li>
 * </ol>
 *
 * <p><strong>Note:</strong> The createIssue test will create a REAL GitHub issue.
 * Close or delete it after testing.
 */
@Disabled("Requires GITHUB_TOKEN env var and a real repository — see class Javadoc")
@SpringBootTest
class GitHubClientIntegrationTest {

    @Autowired
    private GitHubClientService githubClient;

    @Test
    void getRecentCommits_returnsNonEmptyList() {
        List<GitCommit> commits = githubClient.getRecentCommits(5);

        // Uncomment when running for real:
        // assertThat(commits).isNotEmpty();
        // assertThat(commits.get(0).sha()).isNotBlank();
        System.out.println("Commits found: " + commits.size());
        commits.forEach(c -> System.out.println("  " + c.toLogLine()));
    }

    @Test
    void createIssue_createsRealIssue() {
        // NOTE: This WILL create a real GitHub issue if you run it.
        // The issue body will contain placeholder incident data.
        System.out.println("createIssue integration test — wire up with real IncidentReport to test");
        // When ready:
        // IncidentReport report = buildTestReport();
        // LlmAnalysis analysis = buildTestAnalysis();
        // GitHubIssueResult result = githubClient.createIssue(report, analysis, List.of());
        // assertThat(result.success()).isTrue();
        // assertThat(result.url()).startsWith("https://github.com/");
        // System.out.println("Issue created: " + result.url());
    }
}
