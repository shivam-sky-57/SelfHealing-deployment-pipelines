package com.example.healingagent.model;

public record GitHubIssueResult(boolean success, String url, int number, String errorMessage) {

    public static GitHubIssueResult ok(String url, int number) {
        return new GitHubIssueResult(true, url, number, null);
    }

    public static GitHubIssueResult failed(String errorMessage) {
        return new GitHubIssueResult(false, null, 0, errorMessage);
    }
}
