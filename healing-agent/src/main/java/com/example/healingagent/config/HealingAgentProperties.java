package com.example.healingagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "")
public class HealingAgentProperties {

    private Kubernetes kubernetes = new Kubernetes();
    private Groq groq = new Groq();
    private Github github = new Github();
    private Remediation remediation = new Remediation();

    public record Kubernetes(String namespace, String deploymentName, int logLines) {
        public Kubernetes() { this("demo", "demo-app", 100); }
    }

    public record Groq(String apiUrl, String apiKey, String model, int maxTokens, double temperature) {
        public Groq() {
            this("https://api.groq.com/openai/v1/chat/completions", "", "llama3-8b-8192", 1024, 0.1);
        }
    }

    public record Github(String token, String owner, String repo, String apiUrl) {
        public Github() { this("", "", "", "https://api.github.com"); }
    }

    public record Remediation(int cooldownMinutes, int postDeployWindowMinutes) {
        public Remediation() { this(5, 10); }
    }

    public Kubernetes getKubernetes() { return kubernetes; }
    public void setKubernetes(Kubernetes kubernetes) { this.kubernetes = kubernetes; }
    public Groq getGroq() { return groq; }
    public void setGroq(Groq groq) { this.groq = groq; }
    public Github getGithub() { return github; }
    public void setGithub(Github github) { this.github = github; }
    public Remediation getRemediation() { return remediation; }
    public void setRemediation(Remediation remediation) { this.remediation = remediation; }
}
