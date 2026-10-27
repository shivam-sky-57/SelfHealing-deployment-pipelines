package com.example.healingagent.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Runs kubectl as a subprocess for all Kubernetes operations.
 * Arguments are passed as arrays to prevent shell injection.
 */
@Component
public class KubectlClient implements KubernetesClientService {

    private static final Logger log = LoggerFactory.getLogger(KubectlClient.class);
    private static final int READ_TIMEOUT = 30;
    private static final int ROLLOUT_TIMEOUT = 130;

    @Override
    public String getDeploymentStatus(String namespace, String deploymentName) {
        String wide = runKubectl(READ_TIMEOUT, "get", "deployment", deploymentName, "-n", namespace, "-o", "wide");
        String describe = runKubectl(READ_TIMEOUT, "describe", "deployment", deploymentName, "-n", namespace);
        return wide + "\n\n--- describe ---\n" + describe;
    }

    @Override
    public String getRolloutHistory(String namespace, String deploymentName) {
        return runKubectl(READ_TIMEOUT, "rollout", "history", "deployment/" + deploymentName, "-n", namespace);
    }

    @Override
    public List<String> getPodStatuses(String namespace, String deploymentName) {
        String output = runKubectl(READ_TIMEOUT, "get", "pods", "-n", namespace,
                "-l", "app=" + deploymentName, "-o", "wide", "--no-headers");
        if (output.isBlank()) return List.of("No pods found for deployment: " + deploymentName);
        return output.lines().filter(l -> !l.isBlank()).collect(Collectors.toList());
    }

    @Override
    public List<String> getRecentEvents(String namespace) {
        String output = runKubectl(READ_TIMEOUT, "get", "events", "-n", namespace,
                "--field-selector", "type=Warning", "--sort-by=.lastTimestamp");
        List<String> lines = output.lines().filter(l -> !l.isBlank()).limit(20).collect(Collectors.toList());
        if (lines.isEmpty()) lines.add("No Warning events in namespace " + namespace);
        return lines;
    }

    @Override
    public List<String> getContainerLogs(String namespace, String deploymentName, int tailLines) {
        String podListOutput = runKubectl(READ_TIMEOUT, "get", "pods", "-n", namespace,
                "-l", "app=" + deploymentName, "-o", "jsonpath={.items[*].metadata.name}");
        if (podListOutput.isBlank()) return List.of("No pods found for deployment: " + deploymentName);

        List<String> allLogs = new ArrayList<>();
        for (String podName : podListOutput.trim().split("\\s+")) {
            if (podName.isBlank()) continue;
            String logs = runKubectl(READ_TIMEOUT, "logs", podName, "-n", namespace,
                    "--all-containers", "--tail=" + tailLines, "--prefix");
            allLogs.add("=== Logs: " + podName + " ===");
            logs.lines().filter(l -> !l.isBlank()).forEach(allLogs::add);
            allLogs.add("");
        }
        return allLogs;
    }

    @Override
    public int getRevisionCount(String namespace, String deploymentName) {
        String history = getRolloutHistory(namespace, deploymentName);
        return (int) history.lines()
                .filter(l -> !l.isBlank())
                .filter(l -> !l.startsWith("REVISION") && !l.startsWith("deployment.apps"))
                .count();
    }

    @Override
    public boolean executeRollback(String namespace, String deploymentName) {
        log.warn("EXECUTING ROLLBACK: kubectl rollout undo deployment/{} -n {}", deploymentName, namespace);
        ProcessResult result = runKubectlWithExitCode(READ_TIMEOUT,
                "rollout", "undo", "deployment/" + deploymentName, "-n", namespace);
        if (result.exitCode() == 0) {
            log.info("Rollback accepted for {}/{}: {}", namespace, deploymentName, result.output());
            return true;
        }
        log.error("Rollback failed (exit {}) for {}/{}: {}", result.exitCode(), namespace, deploymentName, result.output());
        return false;
    }

    @Override
    public boolean verifyRecovery(String namespace, String deploymentName) {
        log.info("Verifying recovery: rollout status deployment/{} -n {} --timeout=120s", deploymentName, namespace);
        ProcessResult result = runKubectlWithExitCode(ROLLOUT_TIMEOUT,
                "rollout", "status", "deployment/" + deploymentName, "-n", namespace, "--timeout=120s");
        if (result.exitCode() == 0) {
            log.info("Recovery verified for {}/{}", namespace, deploymentName);
            return true;
        }
        log.error("Recovery NOT verified for {}/{}: {}", namespace, deploymentName, result.output());
        return false;
    }

    private String runKubectl(int timeoutSeconds, String... args) {
        return runKubectlWithExitCode(timeoutSeconds, args).output();
    }

    private ProcessResult runKubectlWithExitCode(int timeoutSeconds, String... args) {
        String[] command = new String[args.length + 1];
        command[0] = "kubectl";
        System.arraycopy(args, 0, command, 1, args.length);

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            String output;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                output = reader.lines().collect(Collectors.joining("\n"));
            }

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new ProcessResult(-1, "kubectl timed out after " + timeoutSeconds + "s");
            }

            return new ProcessResult(process.exitValue(), output.isBlank() ? "(no output)" : output);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ProcessResult(-1, "kubectl interrupted: " + e.getMessage());
        } catch (Exception e) {
            return new ProcessResult(-1, "kubectl error: " + e.getMessage()
                    + "\n(Is kubectl installed and kubeconfig available?)");
        }
    }

    private record ProcessResult(int exitCode, String output) {}
}
