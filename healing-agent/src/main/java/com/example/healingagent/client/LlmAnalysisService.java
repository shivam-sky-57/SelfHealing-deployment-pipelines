package com.example.healingagent.client;

import com.example.healingagent.model.KubernetesEvidence;
import com.example.healingagent.model.LlmAnalysis;

import java.util.List;

/**
 * Sends incident evidence to an LLM and parses the structured response.
 * Implementations must never throw — return LlmAnalysis.unknown() on any failure.
 */
public interface LlmAnalysisService {

    LlmAnalysis analyze(
            String alertName,
            String namespace,
            String deploymentName,
            KubernetesEvidence evidence,
            List<String> recentCommits
    );
}
