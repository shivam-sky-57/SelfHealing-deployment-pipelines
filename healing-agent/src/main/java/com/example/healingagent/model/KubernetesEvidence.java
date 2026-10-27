package com.example.healingagent.model;

import java.util.List;

public record KubernetesEvidence(
        String namespace,
        String deploymentName,
        String deploymentStatus,
        String rolloutHistory,
        List<String> podStatuses,
        List<String> recentEvents,
        List<String> containerLogs,
        String collectedAt
) {
    public String toMarkdown() {
        var sb = new StringBuilder();
        sb.append("### Kubernetes Evidence\n\n");
        sb.append("**Namespace:** `").append(namespace).append("`  \n");
        sb.append("**Deployment:** `").append(deploymentName).append("`  \n");
        sb.append("**Collected at:** ").append(collectedAt).append("\n\n");
        sb.append("**Deployment Status:**\n```\n").append(deploymentStatus).append("\n```\n\n");
        sb.append("**Rollout History:**\n```\n").append(rolloutHistory).append("\n```\n\n");
        sb.append("**Pod Statuses:**\n");
        podStatuses.forEach(p -> sb.append("- ").append(p).append("\n"));
        sb.append("\n**Recent Events:**\n```\n");
        recentEvents.forEach(e -> sb.append(e).append("\n"));
        sb.append("```\n\n**Container Logs:**\n```\n");
        containerLogs.forEach(l -> sb.append(l).append("\n"));
        sb.append("```\n");
        return sb.toString();
    }
}
