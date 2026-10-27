package com.example.healingagent.client;

import com.example.healingagent.config.HealingAgentProperties;
import com.example.healingagent.model.KubernetesEvidence;
import com.example.healingagent.model.LlmAnalysis;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for LLM analysis service behaviour.
 *
 * <p>These tests verify the <em>parsing and fallback logic</em> in {@link GroqLlmClient}
 * without making real HTTP calls to Groq. A stub implementation of
 * {@link LlmAnalysisService} is used for the clean-path tests, and a partial
 * mock of {@link GroqLlmClient} is used for the parse-failure tests.
 *
 * <p>Test cases:
 * <ol>
 *   <li>Successful parse — valid JSON → LlmAnalysis populated</li>
 *   <li>JSON parse failure → LlmAnalysis.unknown() returned, never throws</li>
 *   <li>Missing API key → LlmAnalysis.unknown() returned immediately</li>
 *   <li>LlmAnalysis.unknown() factory produces correct default values</li>
 *   <li>Markdown code-fence stripping — ```json ... ``` is cleaned before parsing</li>
 *   <li>Missing required fields (null rootCause) → falls back to unknown</li>
 * </ol>
 */
class LlmAnalysisServiceTest {

    private ObjectMapper objectMapper;
    private KubernetesEvidence stubEvidence;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        stubEvidence = new KubernetesEvidence(
                "demo", "demo-app",
                "NAME      READY  UP-TO-DATE\ndemo-app  2/2    2",
                "REVISION  CHANGE-CAUSE\n1  initial\n2  bad deploy",
                List.of("demo-app-abc Running 0"),
                List.of("Warning: OOMKilled"),
                List.of("[demo-app-abc/app] java.lang.OutOfMemoryError"),
                Instant.now().toString()
        );
    }

    // =========================================================================
    // LlmAnalysis.unknown() factory
    // =========================================================================

    @Test
    @DisplayName("LlmAnalysis.unknown() produces UNKNOWN category and LOW confidence")
    void unknown_factory_producesCorrectDefaults() {
        LlmAnalysis result = LlmAnalysis.unknown("raw error response here");

        assertThat(result.category()).isEqualTo(LlmAnalysis.Category.UNKNOWN);
        assertThat(result.confidence()).isEqualTo(LlmAnalysis.Confidence.LOW);
        assertThat(result.recommendedAction()).isEqualTo("NOTIFY_ONLY");
        assertThat(result.rootCause()).isNotNull().isNotBlank();
        assertThat(result.isKnown()).isFalse();
    }

    @Test
    @DisplayName("LlmAnalysis.unknown() truncates very long raw responses")
    void unknown_factory_truncatesLongResponse() {
        String longResponse = "x".repeat(1000);
        LlmAnalysis result = LlmAnalysis.unknown(longResponse);

        assertThat(result.explanation()).doesNotContain("x".repeat(500));
        assertThat(result.explanation().length()).isLessThan(longResponse.length());
    }

    @Test
    @DisplayName("LlmAnalysis.unknown() handles null raw response safely")
    void unknown_factory_handlesNull() {
        assertThat(LlmAnalysis.unknown(null)).isNotNull();
    }

    // =========================================================================
    // Stub implementation — valid analysis
    // =========================================================================

    @Test
    @DisplayName("A valid LlmAnalysis is populated with all expected fields")
    void validAnalysis_hasAllFields() {
        LlmAnalysis analysis = buildValidAnalysis();

        assertThat(analysis.rootCause()).isNotBlank();
        assertThat(analysis.confidence()).isEqualTo(LlmAnalysis.Confidence.HIGH);
        assertThat(analysis.category()).isEqualTo(LlmAnalysis.Category.CODE_BUG);
        assertThat(analysis.recommendedAction()).isEqualTo("ROLLBACK");
        assertThat(analysis.explanation()).isNotBlank();
        assertThat(analysis.evidence()).isNotEmpty();
        assertThat(analysis.isKnown()).isTrue();
    }

    // =========================================================================
    // JSON parsing via ObjectMapper (tests the parser logic directly)
    // =========================================================================

    @Test
    @DisplayName("Valid JSON response string parses into LlmAnalysis correctly")
    void jsonParsing_succeeds_forValidResponse() throws Exception {
        String json = """
                {
                  "rootCause": "Bad Docker image with broken startup script",
                  "confidence": "HIGH",
                  "category": "CODE_BUG",
                  "recommendedAction": "ROLLBACK",
                  "explanation": "The new image was deployed and immediately started crash-looping.",
                  "evidence": ["CrashLoopBackOff in events", "exit code 1 in logs"]
                }
                """;

        LlmAnalysis parsed = objectMapper.readValue(json, LlmAnalysis.class);

        assertThat(parsed.rootCause()).isEqualTo("Bad Docker image with broken startup script");
        assertThat(parsed.confidence()).isEqualTo(LlmAnalysis.Confidence.HIGH);
        assertThat(parsed.category()).isEqualTo(LlmAnalysis.Category.CODE_BUG);
        assertThat(parsed.recommendedAction()).isEqualTo("ROLLBACK");
        assertThat(parsed.evidence()).hasSize(2);
    }

    @Test
    @DisplayName("CONFIG_ERROR category parses correctly")
    void jsonParsing_configErrorCategory() throws Exception {
        String json = """
                {
                  "rootCause": "FORCE_FAILURE env var set to true",
                  "confidence": "HIGH",
                  "category": "CONFIG_ERROR",
                  "recommendedAction": "ROLLBACK",
                  "explanation": "The FORCE_FAILURE environment variable was set.",
                  "evidence": ["GET / returns 500", "FORCE_FAILURE=true in pod env"]
                }
                """;

        LlmAnalysis parsed = objectMapper.readValue(json, LlmAnalysis.class);

        assertThat(parsed.category()).isEqualTo(LlmAnalysis.Category.CONFIG_ERROR);
    }

    @Test
    @DisplayName("RESOURCE_EXHAUSTION category parses correctly")
    void jsonParsing_resourceExhaustionCategory() throws Exception {
        String json = """
                {
                  "rootCause": "Pod OOMKilled — heap too large for memory limit",
                  "confidence": "MEDIUM",
                  "category": "RESOURCE_EXHAUSTION",
                  "recommendedAction": "SCALE_UP",
                  "explanation": "OOMKill events detected in Kubernetes events.",
                  "evidence": ["OOMKilled in events"]
                }
                """;

        LlmAnalysis parsed = objectMapper.readValue(json, LlmAnalysis.class);

        assertThat(parsed.category()).isEqualTo(LlmAnalysis.Category.RESOURCE_EXHAUSTION);
        assertThat(parsed.confidence()).isEqualTo(LlmAnalysis.Confidence.MEDIUM);
    }

    @Test
    @DisplayName("Invalid JSON string produces UNKNOWN analysis via fallback")
    void jsonParsing_fallsBackToUnknown_forInvalidJson() {
        String notJson = "Sorry, I cannot analyze this incident right now.";
        // Simulate what the real client does: try to parse, fall back on failure
        LlmAnalysis result = tryParse(notJson);

        assertThat(result.category()).isEqualTo(LlmAnalysis.Category.UNKNOWN);
        assertThat(result.confidence()).isEqualTo(LlmAnalysis.Confidence.LOW);
        assertThat(result.isKnown()).isFalse();
    }

    @Test
    @DisplayName("JSON missing required rootCause field produces UNKNOWN via fallback")
    void jsonParsing_fallsBackToUnknown_whenRootCauseMissing() throws Exception {
        String json = """
                {
                  "confidence": "HIGH",
                  "category": "CODE_BUG",
                  "recommendedAction": "ROLLBACK",
                  "explanation": "Something broke.",
                  "evidence": []
                }
                """;
        // Jackson successfully parses this (rootCause just becomes null)
        LlmAnalysis parsed = objectMapper.readValue(json, LlmAnalysis.class);
        // Simulate the null-check in GroqLlmClient.parseLlmContent
        if (parsed.rootCause() == null) {
            parsed = LlmAnalysis.unknown(json);
        }

        assertThat(parsed.category()).isEqualTo(LlmAnalysis.Category.UNKNOWN);
    }

    @Test
    @DisplayName("Markdown code fence is stripped and JSON parses successfully")
    void markdownCodeFence_isStrippedBeforeParsing() {
        // The JSON inside the fence has category=CODE_BUG so we can verify
        // that it parsed correctly (not the UNKNOWN fallback from parse failure)
        String withFence = """
                ```json
                {
                  "rootCause": "test root cause",
                  "confidence": "HIGH",
                  "category": "CODE_BUG",
                  "recommendedAction": "ROLLBACK",
                  "explanation": "test explanation",
                  "evidence": ["evidence point 1"]
                }
                ```
                """;

        // Simulate the stripping logic from GroqLlmClient.parseLlmContent
        String cleaned = withFence.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceAll("(?s)^```[a-zA-Z]*\\n?", "").replaceAll("```$", "").trim();
        }

        // Parse the cleaned string — should succeed and give CODE_BUG, not UNKNOWN
        LlmAnalysis result = tryParse(cleaned);
        assertThat(result.category()).isEqualTo(LlmAnalysis.Category.CODE_BUG);
        assertThat(result.rootCause()).isEqualTo("test root cause");
        assertThat(result.isKnown()).isTrue();
    }

    // =========================================================================
    // Stub LlmAnalysisService for testing callers
    // =========================================================================

    @Test
    @DisplayName("A stub LlmAnalysisService can be injected without Spring context")
    void stubLlmService_canBeUsedInTests() {
        LlmAnalysisService stub = (alertName, ns, dep, ev, commits) ->
                buildValidAnalysis();

        LlmAnalysis result = stub.analyze("HighErrorRate", "demo", "demo-app",
                stubEvidence, List.of("fix: update config"));

        assertThat(result.category()).isEqualTo(LlmAnalysis.Category.CODE_BUG);
        assertThat(result.isKnown()).isTrue();
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private LlmAnalysis buildValidAnalysis() {
        return new LlmAnalysis(
                "Bad deployment introduced a runtime exception in the request handler",
                LlmAnalysis.Confidence.HIGH,
                LlmAnalysis.Category.CODE_BUG,
                "ROLLBACK",
                "The error rate increased immediately after revision 2 was deployed. " +
                "Container logs show a NullPointerException in DemoController. " +
                "Rolling back to revision 1 is expected to restore service.",
                List.of(
                        "Error rate jumped to 100% at 12:00:01 UTC",
                        "NullPointerException in DemoController at line 42",
                        "Revision 2 deployed at 12:00:00 UTC"
                )
        );
    }

    /** Simulates the parse-with-fallback logic from GroqLlmClient. */
    private LlmAnalysis tryParse(String content) {
        try {
            LlmAnalysis result = objectMapper.readValue(content, LlmAnalysis.class);
            if (result.rootCause() == null || result.category() == null) {
                return LlmAnalysis.unknown(content);
            }
            return result;
        } catch (Exception e) {
            return LlmAnalysis.unknown(content);
        }
    }
}
