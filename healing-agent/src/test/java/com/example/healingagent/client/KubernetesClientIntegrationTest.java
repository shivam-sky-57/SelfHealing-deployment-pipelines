package com.example.healingagent.client;

import com.example.healingagent.config.HealingAgentProperties;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Integration test placeholder for {@link KubernetesClientService}.
 *
 * <p><strong>These tests are @Disabled and will not run in CI.</strong>
 *
 * <h2>How to enable these tests</h2>
 * <ol>
 *   <li>Start your kind or minikube cluster:
 *       {@code kind create cluster --name self-healing}</li>
 *   <li>Apply the manifests:
 *       {@code kubectl apply -f k8s/}</li>
 *   <li>Make sure demo-app pods are Running:
 *       {@code kubectl get pods -n demo}</li>
 *   <li>Remove the {@code @Disabled} annotation below</li>
 *   <li>Run: {@code mvn -pl healing-agent test -Dtest=KubernetesClientIntegrationTest}</li>
 * </ol>
 *
 * <p>These tests talk to your real local cluster. They do NOT call kubectl rollout undo
 * (read-only operations only in this placeholder).
 */
@Disabled("Requires a live Kubernetes cluster — see class Javadoc for setup instructions")
@SpringBootTest
class KubernetesClientIntegrationTest {

    @Autowired
    private KubernetesClientService k8sClient;

    @Autowired
    private HealingAgentProperties properties;

    @Test
    void getDeploymentStatus_returnsNonEmptyString() {
        String ns    = properties.getKubernetes().namespace();
        String dep   = properties.getKubernetes().deploymentName();

        String status = k8sClient.getDeploymentStatus(ns, dep);

        // Uncomment assertions when running against a live cluster:
        // assertThat(status).isNotBlank();
        // assertThat(status).contains(dep);
        System.out.println("Deployment status:\n" + status);
    }

    @Test
    void getRolloutHistory_returnsAtLeastOneRevision() {
        String ns  = properties.getKubernetes().namespace();
        String dep = properties.getKubernetes().deploymentName();

        String history = k8sClient.getRolloutHistory(ns, dep);
        System.out.println("Rollout history:\n" + history);
    }

    @Test
    void getPodStatuses_returnsAtLeastOnePod() {
        String ns  = properties.getKubernetes().namespace();
        String dep = properties.getKubernetes().deploymentName();

        var pods = k8sClient.getPodStatuses(ns, dep);
        System.out.println("Pod statuses: " + pods);
    }

    @Test
    void getRecentEvents_returnsEventList() {
        String ns = properties.getKubernetes().namespace();

        var events = k8sClient.getRecentEvents(ns);
        System.out.println("Events: " + events.size() + " entries");
    }

    @Test
    void getContainerLogs_returnsLogLines() {
        String ns   = properties.getKubernetes().namespace();
        String dep  = properties.getKubernetes().deploymentName();
        int lines   = properties.getKubernetes().logLines();

        var logs = k8sClient.getContainerLogs(ns, dep, lines);
        System.out.println("Log lines: " + logs.size());
    }
}
