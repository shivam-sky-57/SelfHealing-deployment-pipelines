# Architecture

## System Diagram

```mermaid
graph TB
    DEV[Developer<br/>git push main] -->|triggers| GHA[GitHub Actions]
    GHA -->|mvn clean verify| BUILD[Maven Build & Test]
    BUILD -->|docker build push| GHCR[GitHub Container Registry]
    GHCR -->|kubectl apply| K8S

    subgraph K8S[Kubernetes — namespace: demo]
        DA[demo-app :8080<br/>2 replicas]
        HA[healing-agent :8081<br/>1 replica]
        PROM[Prometheus :9090]
        AM[Alertmanager :9093]

        PROM -->|scrape every 15s| DA
        PROM -->|alert rules| AM
        AM -->|POST /alerts| HA
        HA -->|kubectl rollout undo| DA
    end

    HA -->|structured prompt| GROQ[Groq LLM API]
    HA -->|fetch commits| GHAPI[GitHub REST API]
    HA -->|create issue / PR| GHAPI
    GROQ -->|JSON analysis| HA
```

## Incident Response Sequence

```mermaid
sequenceDiagram
    participant AM as Alertmanager
    participant AC as AlertController
    participant IRS as IncidentResponseService
    participant ECS as EvidenceCollectionService
    participant KC as KubectlClient
    participant GH as GitHubRestClient
    participant LLM as GroqLlmClient
    participant RPS as RemediationPolicyService

    AM->>AC: POST /alerts
    AC->>IRS: handleAlert()
    IRS-->>AM: 202 Accepted (immediate)
    IRS->>ECS: collect(namespace, deployment)
    ECS->>KC: getDeploymentStatus, getRolloutHistory,<br/>getPodStatuses, getRecentEvents, getContainerLogs
    ECS-->>IRS: KubernetesEvidence
    IRS->>GH: getRecentCommits(10)
    GH-->>IRS: List[GitCommit]
    IRS->>LLM: analyze(alert, evidence, commits)
    LLM-->>IRS: LlmAnalysis{category, confidence, rootCause}
    IRS->>RPS: decide(RemediationContext)
    RPS-->>IRS: RemediationDecision (ROLLBACK / NOTIFY_ONLY)

    alt ROLLBACK
        IRS->>KC: executeRollback()
        IRS->>KC: verifyRecovery()
    end

    IRS->>GH: createIssue(report, analysis, commits)
    GH-->>IRS: issue URL

    opt CONFIG_ERROR
        IRS->>GH: createDraftPullRequest()
    end
```

## Components

| Component | Responsibility |
|---|---|
| `demo-app` | Monitored REST service; exposes Micrometer metrics at `/actuator/prometheus` |
| `healing-agent` | Incident responder; orchestrates evidence collection, LLM analysis, remediation |
| `AlertController` | Accepts Alertmanager webhook, returns 202 immediately |
| `IncidentResponseService` | Async pipeline: evidence → commits → LLM → policy → rollback → GitHub |
| `EvidenceCollectionService` | Runs kubectl commands to collect deployment state |
| `RemediationPolicyService` | Pure Java 5-guard safety policy; makes all rollback decisions |
| `GroqLlmClient` | Sends structured prompt to Groq; parses JSON response with fallback |
| `GitHubRestClient` | Fetches commits; creates issues and draft PRs |
| `KubectlClient` | Runs kubectl as subprocess; all args as arrays (no shell injection) |
| Prometheus | Scrapes metrics every 15s; evaluates alert rules |
| Alertmanager | Routes fired alerts to healing-agent webhook |

## Safety Properties

- **LLM is advisory only** — Java policy has final authority over all kubectl commands
- **5 rollback guards** — cooldown, revision history, post-deploy window, plus defaults
- **Shell injection prevention** — all kubectl args passed as `String[]` to `ProcessBuilder`
- **Secrets in K8s Secrets** — never in source code or ConfigMaps
- **Least-privilege RBAC** — healing-agent ServiceAccount scoped to `demo` namespace only
- **Non-root containers** — both Docker images run as UID 1000
