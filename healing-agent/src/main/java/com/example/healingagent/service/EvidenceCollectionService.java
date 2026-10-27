package com.example.healingagent.service;

import com.example.healingagent.client.KubernetesClientService;
import com.example.healingagent.config.HealingAgentProperties;
import com.example.healingagent.model.KubernetesEvidence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class EvidenceCollectionService {

    private static final Logger log = LoggerFactory.getLogger(EvidenceCollectionService.class);

    private final KubernetesClientService k8sClient;
    private final HealingAgentProperties properties;

    public EvidenceCollectionService(KubernetesClientService k8sClient, HealingAgentProperties properties) {
        this.k8sClient = k8sClient;
        this.properties = properties;
    }

    public KubernetesEvidence collect(String namespace, String deploymentName) {
        int logLines = properties.getKubernetes().logLines();
        log.info("[Evidence] Collecting for {}/{}", namespace, deploymentName);

        String deploymentStatus = safeCollect("deployment status",
                () -> k8sClient.getDeploymentStatus(namespace, deploymentName));
        String rolloutHistory = safeCollect("rollout history",
                () -> k8sClient.getRolloutHistory(namespace, deploymentName));
        List<String> podStatuses = safeCollectList("pod statuses",
                () -> k8sClient.getPodStatuses(namespace, deploymentName));
        List<String> recentEvents = safeCollectList("recent events",
                () -> k8sClient.getRecentEvents(namespace));
        List<String> containerLogs = safeCollectList("container logs",
                () -> k8sClient.getContainerLogs(namespace, deploymentName, logLines));

        log.info("[Evidence] Done — {} pods, {} events, {} log lines",
                podStatuses.size(), recentEvents.size(), containerLogs.size());

        return new KubernetesEvidence(namespace, deploymentName, deploymentStatus,
                rolloutHistory, podStatuses, recentEvents, containerLogs, Instant.now().toString());
    }

    @FunctionalInterface
    private interface Collector<T> { T collect() throws Exception; }

    private String safeCollect(String label, Collector<String> collector) {
        try { return collector.collect(); }
        catch (Exception e) {
            log.warn("[Evidence] Failed to collect {}: {}", label, e.getMessage());
            return "Error collecting " + label + ": " + e.getMessage();
        }
    }

    private List<String> safeCollectList(String label, Collector<List<String>> collector) {
        try { return collector.collect(); }
        catch (Exception e) {
            log.warn("[Evidence] Failed to collect {}: {}", label, e.getMessage());
            return List.of("Error collecting " + label + ": " + e.getMessage());
        }
    }
}
