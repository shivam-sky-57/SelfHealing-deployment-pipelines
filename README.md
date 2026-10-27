# Self-Healing Deployment Pipeline

A portfolio DevOps project that monitors a Kubernetes deployment, detects failures, uses an LLM to analyze the root cause, automatically rolls back bad deployments, and opens a GitHub issue with a full incident report — all running locally on kind or minikube.

---

## How It Works

```
git push main
  → GitHub Actions: build → test → docker push → kubectl apply
    → demo-app running in Kubernetes (namespace: demo)
      → Prometheus scrapes /actuator/prometheus every 15s
        → HighErrorRate alert fires after 1 minute of >50% errors
          → Alertmanager sends webhook to healing-agent
            → healing-agent collects K8s evidence
              → Groq LLM analyzes root cause
                → Java policy decides: rollback or notify-only
                  → kubectl rollout undo (if safe)
                    → GitHub issue created with full report
```

---

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│  Kubernetes — namespace: demo                           │
│                                                         │
│  demo-app (2 replicas)      healing-agent (1 replica)  │
│  :8080                      :8081                       │
│  GET /                      POST /alerts                │
│  GET /fail                       │                      │
│  /actuator/prometheus        kubectl + Groq + GitHub    │
│       │                          │                      │
│  Prometheus ──── alert ──► Alertmanager                 │
│  :9090                      :9093                       │
└─────────────────────────────────────────────────────────┘
                                   │
                              External APIs
                         Groq LLM · GitHub REST
```

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.2 |
| Build | Maven |
| Containerisation | Docker |
| Orchestration | Kubernetes (kind / minikube) |
| CI/CD | GitHub Actions |
| Container Registry | GitHub Container Registry (GHCR) |
| Metrics | Micrometer + Prometheus |
| Alerting | Alertmanager |
| LLM | Groq (llama3-8b-8192) |
| GitHub Automation | GitHub REST API v3 |

---

## Project Structure

```
self-healing-deployment-pipeline/
├── demo-app/           Spring Boot REST app (the monitored service)
├── healing-agent/      Spring Boot incident-response agent
├── k8s/                Kubernetes manifests (13 files)
├── .github/workflows/  GitHub Actions CI/CD
├── docs/               Architecture, demo script, local setup guide
├── .env.example        Environment variable template
└── README.md
```

---

## Quick Start

See [docs/run-locally.md](docs/run-locally.md) for the full guide.

```bash
# 1. Clone
git clone https://github.com/YOUR_USERNAME/self-healing-deployment-pipeline.git
cd self-healing-deployment-pipeline

# 2. Copy env template and fill in values
cp .env.example .env

# 3. Build both services
mvn clean verify -pl demo-app
mvn clean verify -pl healing-agent

# 4. Create local cluster
kind create cluster --name self-healing

# 5. Build + load Docker image
docker build -t demo-app:local demo-app/
kind load docker-image demo-app:local --name self-healing

# 6. Deploy
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/

# 7. Port-forward and test
kubectl port-forward svc/demo-app -n demo 8080:8080
curl http://localhost:8080/
```

---

## Running Tests

```bash
mvn clean verify -pl demo-app      # 6 tests
mvn clean verify -pl healing-agent # 54 tests (7 @Disabled integration tests)
```

---

## Triggering the Self-Healing Demo

```bash
# Break demo-app
kubectl set env deployment/demo-app -n demo FORCE_FAILURE=true

# Watch healing-agent respond
kubectl logs -n demo -l app=healing-agent -f

# Metrics → alerts → webhook → LLM → rollback → GitHub issue
```

See [docs/demo-script.md](docs/demo-script.md) for the full walkthrough.

---

## Required Secrets

### GitHub Actions

| Secret | Description |
|---|---|
| `KUBECONFIG_B64` | Base64-encoded kubeconfig for your cluster |

`GITHUB_TOKEN` is injected automatically — no setup needed.

**Generate KUBECONFIG_B64:**
```powershell
[System.Convert]::ToBase64String([IO.File]::ReadAllBytes("$env:USERPROFILE\.kube\config"))
```

### Kubernetes Secret (for healing-agent)

```bash
kubectl create secret generic healing-agent-secrets -n demo \
  --from-literal=GROQ_API_KEY=gsk_... \
  --from-literal=GITHUB_TOKEN=ghp_...
```

### Environment Variables

See [`.env.example`](.env.example) for the full list.

---

## Alert Rules

| Alert | Condition | Fires After |
|---|---|---|
| `HighErrorRate` | error rate > 50% | 1 minute |
| `PodNotReady` | pod not ready | 2 minutes |
| `DemoAppDown` | Prometheus can't scrape | 1 minute |

---

## Remediation Policy

Rollback is only executed when **all** conditions are met:

1. No rollback issued for this deployment in the last 5 minutes (cooldown)
2. At least 2 revisions exist in rollout history
3. The alert started within 10 minutes of the last deployment

If any check fails, the action is `NOTIFY_ONLY` — a GitHub issue is still created.

---

## Troubleshooting

See [docs/run-locally.md](docs/run-locally.md#troubleshooting).

---

## Docs

- [Architecture](docs/architecture.md)
- [Live Demo Script](docs/demo-script.md)
- [Local Setup Guide](docs/run-locally.md)
