package com.example.healingagent.client;

import com.example.healingagent.config.HealingAgentProperties;
import com.example.healingagent.model.KubernetesEvidence;
import com.example.healingagent.model.LlmAnalysis;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public class GroqLlmClient implements LlmAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(GroqLlmClient.class);

    private static final String SYSTEM_PROMPT = """
            You are an expert Kubernetes SRE assistant performing automated incident analysis.
            
            Analyze the provided incident evidence and return ONLY a valid JSON object.
            Do NOT include any markdown formatting, code blocks, or text outside the JSON.
            Return exactly one JSON object matching this schema:
            
            {
              "rootCause": "one concise sentence describing what broke and why",
              "confidence": "HIGH" or "MEDIUM" or "LOW",
              "category": "CODE_BUG" or "CONFIG_ERROR" or "RESOURCE_EXHAUSTION" or "DEPENDENCY_FAILURE" or "UNKNOWN",
              "recommendedAction": "ROLLBACK" or "NOTIFY_ONLY" or "SCALE_UP" or "RESTART_PODS",
              "explanation": "3 to 5 sentences explaining the analysis for a developer",
              "evidence": ["key evidence point 1", "key evidence point 2", "key evidence point 3"]
            }
            
            Use ROLLBACK only if evidence strongly suggests the failure started immediately after a new deployment.
            Use CONFIG_ERROR if environment variables, ConfigMaps, or resource limits appear misconfigured.
            Use RESOURCE_EXHAUSTION if OOM kills or CPU throttling are evident.
            Use DEPENDENCY_FAILURE if external service calls are failing in logs.
            Use UNKNOWN if you cannot determine the root cause from the evidence.
            """;

    private final HealingAgentProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public GroqLlmClient(HealingAgentProperties properties,
                         RestClient.Builder restClientBuilder,
                         ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = restClientBuilder.baseUrl(properties.getGroq().apiUrl()).build();
    }

    @Override
    public LlmAnalysis analyze(String alertName, String namespace, String deploymentName,
                                KubernetesEvidence evidence, List<String> recentCommits) {
        String apiKey = properties.getGroq().apiKey();
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("[LLM] GROQ_API_KEY not set — returning UNKNOWN fallback");
            return LlmAnalysis.unknown("GROQ_API_KEY not configured");
        }

        log.info("[LLM] Analyzing alert={} namespace={} deployment={}", alertName, namespace, deploymentName);
        String userPrompt = buildUserPrompt(alertName, namespace, deploymentName, evidence, recentCommits);
        String requestBody = buildRequestBody(userPrompt);

        try {
            String rawResponse = restClient.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + apiKey)
                    .body(requestBody)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, resp) ->
                        log.error("[LLM] Client error HTTP {}", resp.getStatusCode().value()))
                    .onStatus(HttpStatusCode::is5xxServerError, (req, resp) ->
                        log.error("[LLM] Server error HTTP {}", resp.getStatusCode().value()))
                    .body(String.class);

            if (rawResponse == null || rawResponse.isBlank()) {
                return LlmAnalysis.unknown("Groq returned empty response");
            }

            return parseGroqResponse(rawResponse);

        } catch (Exception e) {
            log.error("[LLM] HTTP call failed: {}", e.getMessage());
            return LlmAnalysis.unknown("HTTP error: " + e.getMessage());
        }
    }

    private String buildUserPrompt(String alertName, String namespace, String deploymentName,
                                   KubernetesEvidence evidence, List<String> recentCommits) {
        return """
                INCIDENT DETAILS
                Alert: %s | Namespace: %s | Deployment: %s | Collected: %s
                
                DEPLOYMENT STATUS
                %s
                
                ROLLOUT HISTORY
                %s
                
                POD STATUSES
                %s
                
                RECENT KUBERNETES EVENTS
                %s
                
                CONTAINER LOGS
                %s
                
                RECENT GIT COMMITS
                %s
                """.formatted(
                alertName, namespace, deploymentName, evidence.collectedAt(),
                truncate(evidence.deploymentStatus(), 1000),
                truncate(evidence.rolloutHistory(), 500),
                String.join("\n", evidence.podStatuses()),
                truncate(String.join("\n", evidence.recentEvents()), 1000),
                truncate(String.join("\n", evidence.containerLogs()), 3000),
                truncate(String.join("\n", recentCommits), 1000)
        );
    }

    private String buildRequestBody(String userPrompt) {
        HealingAgentProperties.Groq cfg = properties.getGroq();
        return """
                {
                  "model": "%s",
                  "temperature": %s,
                  "max_tokens": %d,
                  "messages": [
                    {"role": "system", "content": "%s"},
                    {"role": "user",   "content": "%s"}
                  ]
                }
                """.formatted(cfg.model(), cfg.temperature(), cfg.maxTokens(),
                escapeJson(SYSTEM_PROMPT), escapeJson(userPrompt));
    }

    private LlmAnalysis parseGroqResponse(String rawResponse) {
        try {
            GroqChatResponse envelope = objectMapper.readValue(rawResponse, GroqChatResponse.class);
            if (envelope.choices() == null || envelope.choices().isEmpty()) {
                return LlmAnalysis.unknown("Groq response had no choices");
            }
            return parseLlmContent(envelope.choices().get(0).message().content());
        } catch (Exception e) {
            log.error("[LLM] Failed to parse Groq envelope: {}", e.getMessage());
            return LlmAnalysis.unknown("Parse error: " + e.getMessage());
        }
    }

    private LlmAnalysis parseLlmContent(String content) {
        if (content == null || content.isBlank()) return LlmAnalysis.unknown("Empty content");

        String cleaned = content.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceAll("(?s)^```[a-zA-Z]*\\n?", "").replaceAll("```$", "").trim();
        }

        try {
            LlmAnalysis analysis = objectMapper.readValue(cleaned, LlmAnalysis.class);
            if (analysis.rootCause() == null || analysis.category() == null) {
                return LlmAnalysis.unknown("JSON missing required fields");
            }
            log.info("[LLM] Parsed: category={} confidence={} action={}",
                    analysis.category(), analysis.confidence(), analysis.recommendedAction());
            return analysis;
        } catch (Exception e) {
            log.warn("[LLM] Could not parse content as LlmAnalysis: {}", e.getMessage());
            return LlmAnalysis.unknown(cleaned);
        }
    }

    private String truncate(String text, int maxChars) {
        if (text == null) return "(null)";
        return text.length() <= maxChars ? text : text.substring(0, maxChars) + "\n... [truncated]";
    }

    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\").replace("\"", "\\\"")
                   .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GroqChatResponse(
            @JsonProperty("choices") List<Choice> choices,
            @JsonProperty("usage") Map<String, Object> usage
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Choice(
            @JsonProperty("message") Message message,
            @JsonProperty("finish_reason") String finishReason
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Message(
            @JsonProperty("role") String role,
            @JsonProperty("content") String content
    ) {}
}
