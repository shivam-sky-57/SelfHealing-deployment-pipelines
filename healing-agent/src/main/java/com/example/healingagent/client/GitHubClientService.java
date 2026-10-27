package com.example.healingagent.client;

import com.example.healingagent.model.GitCommit;
import com.example.healingagent.model.GitHubIssueResult;
import com.example.healingagent.model.IncidentReport;
import com.example.healingagent.model.LlmAnalysis;

import java.util.List;

/**
 * GitHub REST API operations. Implementations must never throw —
 * return empty lists or GitHubIssueResult.failed() on any error.
 */
public interface GitHubClientService {

    List<GitCommit> getRecentCommits(int count);

    GitHubIssueResult createIssue(IncidentReport report, LlmAnalysis llmAnalysis, List<GitCommit> commits);

    GitHubIssueResult createDraftPullRequest(IncidentReport report, LlmAnalysis llmAnalysis);
}
