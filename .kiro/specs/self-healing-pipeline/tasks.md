# Tasks: Self-Healing Deployment Pipeline

Each task is small, ordered, and independently testable.
Phases match the implementation plan in the project brief.

---

## Phase 1 — Project Skeleton

- [x] **T-1.1** Create monorepo directory structure (demo-app, healing-agent, k8s, .github/workflows, docs)
- [x] **T-1.2** Create root `.gitignore` (Maven targets, .env, IDE files, Docker credentials)
- [x] **T-1.3** Create `.env.example` documenting all required environment variables
- [x] **T-1.4** Create `demo-app/pom.xml` with Spring Boot 3.x parent, Java 21, and required dependencies
- [x] **T-1.5** Create `healing-agent/pom.xml` with Spring Boot 3.x parent, Java 21, and required dependencies
- [x] **T-1.6** Create skeleton `DemoAppApplication.java` and `HealingAgentApplication.java` (entry points only)
- [x] **T-1.7** Create placeholder `application.yml` files for both services
- [x] **T-1.8** Create `README.md` skeleton with project description and phase roadmap
- [x] **T-1.9** Create `docs/architecture.md`, `docs/demo-script.md`, `docs/run-locally.md` as stubs
- [x] **T-1.10** Verify: `mvn -pl demo-app clean compile` and `mvn -pl healing-agent clean compile` both succeed

---

## Phase 2 — demo-app Implementation

- [ ] **T-2.1** Implement `DemoController` with `GET /` and `GET /fail`
- [ ] **T-2.2** Implement `FORCE_FAILURE` env var check in the success endpoint
- [ ] **T-2.3** Register `demo_requests_total` and `demo_errors_total` Micrometer counters
- [ ] **T-2.4** Configure `application.yml`: expose all Actuator endpoints, set Prometheus scrape path
- [ ] **T-2.5** Create `Dockerfile` for demo-app (multi-stage, non-root user)
- [ ] **T-2.6** Write `DemoControllerTest` unit test covering success and failure paths
- [ ] **T-2.7** Verify: `mvn -pl demo-app clean verify` passes; `docker build demo-app` succeeds

---

## Phase 3 — Local Kubernetes Deployment

- [ ] **T-3.1** Create `k8s/namespace.yaml`
- [ ] **T-3.2** Create `k8s/demo-app-deployment.yaml` (2 replicas, probes, revisionHistoryLimit, resource limits)
- [ ] **T-3.3** Create `k8s/demo-app-service.yaml` (ClusterIP on port 8080)
- [ ] **T-3.4** Document kind/minikube setup commands in `docs/run-locally.md`
- [ ] **T-3.5** Verify: pods running, health endpoints reachable via port-forward, Prometheus endpoint returns metrics

---

## Phase 4 — GitHub Actions CI/CD

- [ ] **T-4.1** Create `.github/workflows/deploy.yml` with trigger on push to main
- [ ] **T-4.2** Add Maven build and test step (Java 21 setup)
- [ ] **T-4.3** Add Docker build and push to GHCR using commit SHA as tag
- [ ] **T-4.4** Add kubectl apply and rollout status steps
- [ ] **T-4.5** Add automatic `kubectl rollout undo` on rollout failure
- [ ] **T-4.6** Document required GitHub Secrets in README
- [ ] **T-4.7** Verify: push to main triggers workflow; image appears in GHCR

---

## Phase 5 — Prometheus & Alertmanager

- [ ] **T-5.1** Create `k8s/prometheus-configmap.yaml` (scrape config targeting demo-app)
- [ ] **T-5.2** Create `k8s/prometheus-rules.yaml` (HighErrorRate and PodNotReady rules)
- [ ] **T-5.3** Create `k8s/alertmanager-configmap.yaml` (webhook route to healing-agent)
- [ ] **T-5.4** Create Prometheus Deployment and Service manifests
- [ ] **T-5.5** Create Alertmanager Deployment and Service manifests
- [ ] **T-5.6** Verify: Prometheus scrapes demo-app; alerts visible in Prometheus UI; test alert routes to healing-agent

---

## Phase 6 — healing-agent Webhook

- [ ] **T-6.1** Create `AlertPayload` and related model records
- [ ] **T-6.2** Implement `AlertController` with `POST /alerts` returning 202 Accepted
- [ ] **T-6.3** Implement `AlertPayloadParser` extracting namespace and deployment from labels
- [ ] **T-6.4** Create `KubernetesClientService` interface and `KubectlClient` implementation (evidence collection only, no mutations yet)
- [ ] **T-6.5** Create `EvidenceCollectionService` assembling all K8s evidence
- [ ] **T-6.6** Create `k8s/healing-agent-deployment.yaml` and `k8s/healing-agent-service.yaml`
- [ ] **T-6.7** Write `AlertPayloadParserTest` unit test
- [ ] **T-6.8** Verify: `POST /alerts` with a sample payload logs incident ID and collected evidence

---

## Phase 7 — Rollback & Verification

- [ ] **T-7.1** Add mutation methods to `KubernetesClientService` interface: `executeRollback`, `verifyRecovery`
- [ ] **T-7.2** Implement rollback and verification in `KubectlClient`
- [ ] **T-7.3** Implement `RemediationPolicyService` with all decision branches and cooldown map
- [ ] **T-7.4** Wire rollback into `IncidentResponseService`
- [ ] **T-7.5** Write `RemediationPolicyServiceTest` covering all 5 decision branches
- [ ] **T-7.6** Verify: triggering alert causes rollback; cooldown prevents second rollback within 5 min

---

## Phase 8 — LLM Root-Cause Analysis

- [ ] **T-8.1** Create `LlmAnalysis` record and `LlmAnalysisService` interface
- [ ] **T-8.2** Implement `GroqLlmClient` using Spring's `RestClient` with structured prompt
- [ ] **T-8.3** Implement JSON response parsing with fallback to UNKNOWN/NOTIFY_ONLY
- [ ] **T-8.4** Wire `LlmAnalysisService` into `IncidentResponseService`
- [ ] **T-8.5** Write `LlmAnalysisServiceTest` using a mocked HTTP client (valid JSON, invalid JSON, HTTP 500)
- [ ] **T-8.6** Verify: alert triggers LLM call; parsed analysis appears in logs; GitHub issue body includes analysis

---

## Phase 9 — GitHub Automation

- [ ] **T-9.1** Create `GitHubClientService` interface with `getRecentCommits`, `createIssue`, `createDraftPR`
- [ ] **T-9.2** Implement `GitHubRestClient` using `RestClient` with Bearer token auth
- [ ] **T-9.3** Implement issue creation with full incident report body (Markdown formatted)
- [ ] **T-9.4** Implement draft PR creation for CONFIG_ERROR category
- [ ] **T-9.5** Add `KubernetesClientIntegrationTest` and `GitHubClientIntegrationTest` placeholder classes with `@Disabled`
- [ ] **T-9.6** Write `GitHubClientTest` with mocked HTTP responses
- [ ] **T-9.7** Verify: incident creates a real GitHub issue; issue body contains all sections

---

## Phase 10 — Documentation & Demo

- [ ] **T-10.1** Complete `README.md` (prerequisites, architecture overview, quick start, demo, troubleshooting)
- [ ] **T-10.2** Complete `docs/architecture.md` with Mermaid diagram and component table
- [ ] **T-10.3** Complete `docs/demo-script.md` (step-by-step live demo with commands)
- [ ] **T-10.4** Complete `docs/run-locally.md` (kind and minikube options, port-forward commands, sample Prometheus queries)
- [ ] **T-10.5** Final check: no secrets in any committed file; `.gitignore` tested
- [ ] **T-10.6** Final check: `mvn clean verify` passes for both modules
