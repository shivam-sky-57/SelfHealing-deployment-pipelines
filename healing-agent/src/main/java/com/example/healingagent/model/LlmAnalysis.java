package com.example.healingagent.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LlmAnalysis(
        @JsonProperty("rootCause") String rootCause,
        @JsonProperty("confidence") Confidence confidence,
        @JsonProperty("category") Category category,
        @JsonProperty("recommendedAction") String recommendedAction,
        @JsonProperty("explanation") String explanation,
        @JsonProperty("evidence") List<String> evidence
) {
    public enum Confidence { HIGH, MEDIUM, LOW }

    public enum Category { CODE_BUG, CONFIG_ERROR, RESOURCE_EXHAUSTION, DEPENDENCY_FAILURE, UNKNOWN }

    public static LlmAnalysis unknown(String rawResponse) {
        String truncated = rawResponse != null && rawResponse.length() > 300
                ? rawResponse.substring(0, 300) + "..." : rawResponse;
        return new LlmAnalysis(
                "Could not determine root cause — LLM response was not valid JSON",
                Confidence.LOW, Category.UNKNOWN, "NOTIFY_ONLY",
                "LLM returned an unparseable response. Raw: " + truncated,
                List.of("LLM parse failure")
        );
    }

    public boolean isKnown() { return category != Category.UNKNOWN; }

    public String toMarkdown() {
        var sb = new StringBuilder();
        sb.append("### LLM Root-Cause Analysis\n\n");
        sb.append("| Field | Value |\n|---|---|\n");
        sb.append("| Root Cause | ").append(rootCause).append(" |\n");
        sb.append("| Confidence | ").append(confidence).append(" |\n");
        sb.append("| Category | ").append(category).append(" |\n");
        sb.append("| Recommended Action | ").append(recommendedAction).append(" |\n\n");
        sb.append("**Explanation:** ").append(explanation).append("\n\n");
        if (evidence != null && !evidence.isEmpty()) {
            sb.append("**Key Evidence:**\n");
            evidence.forEach(e -> sb.append("- ").append(e).append("\n"));
        }
        return sb.toString();
    }
}
