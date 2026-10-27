package com.example.healingagent.client;

import java.util.List;

public interface KubernetesClientService {

    String getDeploymentStatus(String namespace, String deploymentName);
    String getRolloutHistory(String namespace, String deploymentName);
    List<String> getPodStatuses(String namespace, String deploymentName);
    List<String> getRecentEvents(String namespace);
    List<String> getContainerLogs(String namespace, String deploymentName, int tailLines);
    int getRevisionCount(String namespace, String deploymentName);

    // Mutating operations — only called after policy approval
    boolean executeRollback(String namespace, String deploymentName);
    boolean verifyRecovery(String namespace, String deploymentName);
}
