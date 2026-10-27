package com.example.healingagent.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record GitCommit(String sha, String message, String author, String date, List<String> files) {

    public String shortSha() {
        return sha != null && sha.length() >= 7 ? sha.substring(0, 7) : sha;
    }

    public String summary() {
        if (message == null) return "(no message)";
        int newline = message.indexOf('\n');
        return newline > 0 ? message.substring(0, newline) : message;
    }

    public String toLogLine() {
        return shortSha() + " — " + summary() + " (" + author + ")";
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CommitListItem(
            @JsonProperty("sha") String sha,
            @JsonProperty("commit") CommitDetail commit,
            @JsonProperty("files") List<FileChange> files
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CommitDetail(
            @JsonProperty("message") String message,
            @JsonProperty("author") CommitAuthor author
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CommitAuthor(
            @JsonProperty("name") String name,
            @JsonProperty("date") String date
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FileChange(
            @JsonProperty("filename") String filename,
            @JsonProperty("status") String status
    ) {}
}
