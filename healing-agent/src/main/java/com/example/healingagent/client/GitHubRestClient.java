package com.example.healingagent.client;

import com.example.healingagent.config.HealingAgentProperties;
import com.example.healingagent.model.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class GitHubRestClient implements GitHubClientService {

    private static final Logger log = LoggerFactory.getLogger(GitHubRestClient.class);

    private final HealingAgentProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public GitHubRestClient(HealingAgentProperties properties,
                             RestClient.Builder restClientBuilder,
                             ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = restClientBuilder.baseUrl(properties.getGithub().apiUrl()).build();
    }

    @Override
    public List<GitCommit> getRecentCommits(int count) {
        if (!isConfigured()) {
            log.warn("[GitHub] Credentials not configured — skipping commit fetch");
            return Collections.emptyList();
        }
        try {
            String url = "/repos/%s/%s/commits?per_page=%d"
                    .formatted(properties.getGithub().owner(), properties.getGithub().repo(), count);
            String body = restClient.get()
                    .uri(url)
                    .header("Authorization", "Bearer " + properties.getGithub().token())
                    .header("Accept", "application/vnd.github.v3+json")
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, resp) ->
                        log.error("[GitHub] Error fetching commits: HTTP {}", resp.getStatusCode().value()))
                    .body(String.class);

            if (body == null) return Collections.emptyList();

            List<GitCommit.CommitListItem> items = objectMapper.readValue(body, new TypeReference<>() {});
            return items.stream().map(item -> new GitCommit(
                    item.sha(),
                    item.commit() != null ? item.commit().message() : "",
                    item.commit() != null && item.commit().author() != null ? item.commit().author().name() : "unknown",
                    item.commit() != null && item.commit().author() != null ? item.commit().author().date() : "",
                    item.files() != null
                            ? item.files().stream().map(GitCommit.FileChange::filename).collect(Collectors.toList())
                            : Collections.emptyList()
            )).collect(Collectors.toList());

        } catch (Exception e) {
            log.error("[GitHub] Failed to fetch commits: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public GitHubIssueResult createIssue(IncidentReport report, LlmAnalysis llmAnalysis, List<GitCommit> commits) {
        if (!isConfigured()) return GitHubIssueResult.failed("GitHub credentials not configured");

        try {
            String title = "[Incident %s] %s in %s/%s".formatted(
                    report.incidentId().substring(0, 8), report.alertName(),
                    report.namespace(), report.deploymentName());
            String body = buildIssueBody(report, llmAnalysis, commits);

            log.info("[GitHub] Creating issue: {}", title);

            String payload = objectMapper.writeValueAsString(Map.of(
                    "title", title,
                    "body", body,
                    "labels", List.of("incident", "self-healing", report.alertName().toLowerCase())
            ));

            String url = "/repos/%s/%s/issues".formatted(
                    properties.getGithub().owner(), properties.getGithub().repo());

            String responseBody = restClient.post()
                    .uri(url)
                    .header("Authorization", "Bearer " + properties.getGithub().token())
                    .header("Accept", "application/vnd.github.v3+json")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, resp) ->
                        log.error("[GitHub] Error creating issue: HTTP {}", resp.getStatusCode().value()))
                    .body(String.class);

            if (responseBody == null) return GitHubIssueResult.failed("GitHub API returned empty response");

            IssueResponse issue = objectMapper.readValue(responseBody, IssueResponse.class);
            log.info("[GitHub] Issue created: #{} — {}", issue.number(), issue.htmlUrl());
            return GitHubIssueResult.ok(issue.htmlUrl(), issue.number());

        } catch (Exception e) {
            log.error("[GitHub] Failed to create issue: {}", e.getMessage());
            return GitHubIssueResult.failed("Exception: " + e.getMessage());
        }
    }

    @Override
    public GitHubIssueResult createDraftPullRequest(IncidentReport report, LlmAnalysis llmAnalysis) {
        if (!isConfigured()) return GitHubIssueResult.failed("GitHub credentials not configured");

        try {
            String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                    .withZone(ZoneOffset.UTC).format(Instant.now());
            String branchName = "fix/incident-" + report.incidentId().substring(0, 8) + "-" + timestamp;
            String title = "[Draft] Fix configuration error — " + report.alertName() + " in " + report.deploymentName();

            String payload = objectMapper.writeValueAsString(Map.of(
                    "title", title,
                    "body", buildPRBody(report, llmAnalysis),
                    "head", branchName,
                    "base", "main",
                    "draft", true,
                    "maintainer_can_modify", true
            ));

            String url = "/repos/%s/%s/pulls".formatted(
                    properties.getGithub().owner(), properties.getGithub().repo());

            String responseBody = restClient.post()
                    .uri(url)
                    .header("Authorization", "Bearer " + properties.getGithub().token())
                    .header("Accept", "application/vnd.github.v3+json")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, resp) ->
                        log.warn("[GitHub] Could not create draft PR — HTTP {} (branch may not exist yet)",
                                resp.getStatusCode().value()))
                    .body(String.class);

            if (responseBody == null) return GitHubIssueResult.failed("GitHub API returned empty response for PR");

            IssueResponse pr = objectMapper.readValue(responseBody, IssueResponse.class);
            log.info("[GitHub] Draft PR created: #{} — {}", pr.number(), pr.htmlUrl());
            return GitHubIssueResult.ok(pr.htmlUrl(), pr.number());

        } catch (Exception e) {
            log.warn("[GitHub] Could not create draft PR (non-critical): {}", e.getMessage());
            return GitHubIssueResult.failed("Exception: " + e.getMessage());
        }
    }

    private String buildIssueBody(IncidentReport report, LlmAnalysis llmAnalysis, List<GitCommit> commits) {
        var sb = new StringBuilder();
        sb.append("# 🚨 Incident Report\n\n");
        sb.append("> Auto-generated by the Self-Healing Deployment Pipeline\n\n");
        sb.append("## Summary\n\n| Field | Value |\n|---|---|\n");
        sb.append("| Incident ID | `").append(report.incidentId()).append("` |\n");
        sb.append("| Alert | `").append(report.alertName()).append("` |\n");
        sb.append("| Namespace | `").append(report.namespace()).append("` |\n");
        sb.append("| Deployment | `").append(report.deploymentName()).append("` |\n");
        sb.append("| Alert Started | ").append(report.alertStartedAt()).append(" |\n");
        sb.append("| Received | ").append(report.receivedAt()).append(" |\n");
        sb.append("| Action Taken | `").append(report.actionTaken()).append("` |\n");
        sb.append("| Recovery | **").append(report.recoveryStatus()).append("** |\n\n");
        sb.append("## 🤖 LLM Root-Cause Analysis\n\n").append(llmAnalysis.toMarkdown()).append("\n");
        sb.append("## 📝 Recent Git Commits\n\n");
        if (commits.isEmpty()) {
            sb.append("_No commit data available_\n\n");
        } else {
            sb.append("```\n");
            commits.forEach(c -> sb.append(c.toLogLine()).append("\n"));
            sb.append("```\n\n");
        }
        if (report.evidence() != null) {
            sb.append("## ☸️ Kubernetes Evidence\n\n").append(report.evidence().toMarkdown()).append("\n");
        }
        sb.append("---\n_Created automatically by healing-agent. Close once root cause confirmed._\n");
        return sb.toString();
    }

    private String buildPRBody(IncidentReport report, LlmAnalysis llmAnalysis) {
        return """
                ## Draft Fix — Configuration Error
                
                Auto-created by healing-agent for incident `%s`.
                
                **Alert:** `%s` | **Deployment:** `%s/%s`
                
                **Root Cause:** %s
                
                **Explanation:** %s
                
                > This PR is a **draft** and requires human review before merging.
                """.formatted(report.incidentId(), report.alertName(),
                report.namespace(), report.deploymentName(),
                llmAnalysis.rootCause(), llmAnalysis.explanation());
    }

    private boolean isConfigured() {
        String token = properties.getGithub().token();
        String owner = properties.getGithub().owner();
        String repo  = properties.getGithub().repo();
        return token != null && !token.isBlank() && owner != null && !owner.isBlank()
                && repo != null && !repo.isBlank();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record IssueResponse(
            @JsonProperty("number") int number,
            @JsonProperty("html_url") String htmlUrl
    ) {}
}
