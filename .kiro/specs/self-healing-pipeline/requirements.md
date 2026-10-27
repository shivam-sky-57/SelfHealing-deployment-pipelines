# Requirements: Self-Healing Deployment Pipeline

## Overview

A portfolio-grade DevOps automation system that monitors a Kubernetes-hosted CI/CD deployment,
detects failures or performance regressions, analyzes the likely root cause using Kubernetes
evidence and recent Git commits, automatically rolls back a bad deployment when safe, and creates
a GitHub issue or draft fix pull request with a clear explanation. Designed to run locally with
kind or minikube — safe for a learner.

---

## Actors

| Actor | Description |
|---|---|
| Developer | Pushes code to `main`; observes automated deployments |
| Healing Agent | Automated service that receives alerts and responds |
| Prometheus | Scrapes metrics and fires alerts |
| Alertmanager | Routes Prometheus alerts to the healing agent webhook |
| LLM (Groq) | Analyzes evidence and suggests a root cause (advisory only) |
| GitHub API | Source of recent commits; target for issues and PRs |
| Kubernetes | Executes workloads; source of evidence (events, logs, pod status) |

---

## User Stories

### US-1 — Automated Deployment on Push
**As a** developer,
**I want** every push to `main` to automatically build, test, containerise, and deploy `demo-app`,
**so that** I can validate changes quickly without manual steps.

### US-2 — Continuous Health Monitoring
**As a** developer,
**I want** Prometheus to continuously scrape `demo-app` metrics and fire alerts on high error rates or pod readiness failures,
**so that** problems are surfaced within minutes of occurring.

### US-3 — Automatic Incident Response
**As a** developer,
**I want** the healing agent to receive Alertmanager webhooks and immediately begin collecting evidence,
**so that** no manual triage is needed for common failure patterns.

### US-4 — Root-Cause Analysis
**As a** developer,
**I want** the healing agent to consult an LLM with Kubernetes evidence and recent commit history,
**so that** the likely root cause is explained in plain English alongside the automated action taken.

### US-5 — Safe Automatic Rollback
**As a** developer,
**I want** the healing agent to roll back `demo-app` only when it is safe (previous healthy revision exists and failure started after the last deploy),
**so that** the blast radius of bad code is minimised automatically without risking further breakage.

### US-6 — GitHub Issue Creation
**As a** developer,
**I want** a GitHub issue created for every incident containing the alert summary, Kubernetes evidence, LLM analysis, the action taken, and the recovery status,
**so that** there is a permanent audit trail.

### US-7 — Draft Fix Pull Request
**As a** developer,
**I want** the healing agent to optionally open a draft pull request containing a suggested configuration fix for simple config-related failures,
**so that** I have a starting point for the real fix.

### US-8 — Cooldown Protection
**As a** developer,
**I want** the healing agent to refuse repeated rollbacks for the same deployment within a short cooldown window (5 minutes),
**so that** it cannot thrash between revisions.

### US-9 — Local Demo
**As a** learner,
**I want** the entire system to run on my laptop using kind or minikube,
**so that** I can demonstrate self-healing behaviour during a portfolio walkthrough without cloud costs.

### US-10 — Configurable Failure Mode
**As a** learner,
**I want** to break `demo-app` by setting an environment variable rather than changing code,
**so that** I can trigger the self-healing demo repeatably and safely.

---

## EARS-Style Acceptance Criteria

### Feature 1 — CI/CD Pipeline (GitHub Actions)

**AC-1.1** WHEN a developer pushes a commit to `main`,
THE SYSTEM SHALL check out the repository, compile with Maven, run all tests, and fail the workflow if any test fails.

**AC-1.2** WHEN the Maven build passes,
THE SYSTEM SHALL build a Docker image tagged with the Git commit SHA and push it to GHCR.

**AC-1.3** WHEN the image is pushed,
THE SYSTEM SHALL apply Kubernetes manifests and update the `demo-app` Deployment image.

**AC-1.4** WHEN the Deployment update is applied,
THE SYSTEM SHALL run `kubectl rollout status` with a 5-minute timeout.

**AC-1.5** IF `kubectl rollout status` times out or fails,
THE SYSTEM SHALL automatically execute `kubectl rollout undo` as a basic safety net.

---

### Feature 2 — demo-app

**AC-2.1** WHEN `GET /` is called,
THE SYSTEM SHALL return HTTP 200 with a JSON body `{"status":"ok","message":"Hello from demo-app"}` and increment `demo_requests_total`.

**AC-2.2** WHEN `GET /fail` is called,
THE SYSTEM SHALL increment `demo_errors_total`, throw a `RuntimeException`, and return HTTP 500.

**AC-2.3** WHEN the environment variable `FORCE_FAILURE=true` is set,
THE SYSTEM SHALL make every `GET /` call behave as `GET /fail`, enabling demo scenarios without code changes.

**AC-2.4** THE SYSTEM SHALL expose `/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness`, `/actuator/metrics`, `/actuator/info`, and `/actuator/prometheus` endpoints.

**AC-2.5** THE SYSTEM SHALL be deployed with 2 replicas, `revisionHistoryLimit: 10`, and Kubernetes liveness/readiness probes pointing at Actuator endpoints.

---

### Feature 3 — Prometheus & Alertmanager

**AC-3.1** THE SYSTEM SHALL scrape `demo-app` at `/actuator/prometheus` every 15 seconds.

**AC-3.2** WHEN `demo_errors_total / demo_requests_total > 0.5` for 60 seconds,
THE SYSTEM SHALL fire the `HighErrorRate` alert.

**AC-3.3** WHEN any `demo-app` pod is not in `Ready` state for 120 seconds,
THE SYSTEM SHALL fire the `PodNotReady` alert.

**AC-3.4** WHEN either alert fires,
Alertmanager SHALL forward the webhook payload to `http://healing-agent.demo.svc.cluster.local:8081/alerts`.

---

### Feature 4 — healing-agent Webhook

**AC-4.1** THE SYSTEM SHALL expose `POST /alerts` accepting an Alertmanager webhook JSON payload.

**AC-4.2** WHEN a payload is received,
THE SYSTEM SHALL parse the namespace and deployment name from alert labels and log the incident with a generated `incidentId`.

**AC-4.3** THE SYSTEM SHALL collect: Deployment status, rollout history, pod statuses, restart counts, Kubernetes events, and the last 100 lines of logs from each pod container.

**AC-4.4** THE SYSTEM SHALL fetch the last 10 commits and changed files from the GitHub REST API for the configured repository.

---

### Feature 5 — LLM Root-Cause Analysis

**AC-5.1** THE SYSTEM SHALL send a structured prompt to the Groq LLM API containing all collected evidence.

**AC-5.2** THE SYSTEM SHALL instruct the LLM to return a JSON object with fields: `rootCause`, `confidence` (HIGH/MEDIUM/LOW), `category`, `recommendedAction`, and `explanation`.

**AC-5.3** IF the LLM response cannot be parsed as valid JSON,
THE SYSTEM SHALL classify the incident as `UNKNOWN` category and set `recommendedAction` to `NOTIFY_ONLY`.

**AC-5.4** THE SYSTEM SHALL log the full LLM response and the parsed result.

**AC-5.5** THE LLM SHALL only provide analysis; Java code SHALL make and execute all remediation decisions.

---

### Feature 6 — Remediation Policy

**AC-6.1** THE SYSTEM SHALL roll back a Deployment ONLY when ALL of the following are true:
  - The failure started within 10 minutes of the last Deployment rollout.
  - A previous revision exists in rollout history.
  - The cooldown window (5 minutes) has not been triggered by a previous rollback of the same deployment.

**AC-6.2** WHEN a rollback is executed,
THE SYSTEM SHALL run `kubectl rollout undo deployment/<name> -n <namespace>` then verify recovery with `kubectl rollout status --timeout=120s`.

**AC-6.3** WHEN a rollback is NOT safe,
THE SYSTEM SHALL log the reason and proceed with `NOTIFY_ONLY`.

**AC-6.4** THE SYSTEM SHALL record the timestamp of every rollback and refuse a second rollback for the same deployment within 5 minutes.

**AC-6.5** THE SYSTEM SHALL log every decision with the reasoning in a structured log entry.

---

### Feature 7 — GitHub Automation

**AC-7.1** WHEN an incident is handled,
THE SYSTEM SHALL create a GitHub issue containing: alert name, alert summary, collected Kubernetes evidence, recent commits, LLM analysis, action taken, and recovery status.

**AC-7.2** WHEN the LLM categorises the failure as a configuration error,
THE SYSTEM SHALL optionally create a draft pull request with a suggested fix.

**AC-7.3** THE SYSTEM SHALL read the GitHub token from the environment variable `GITHUB_TOKEN`.

**AC-7.4** THE SYSTEM SHALL never commit or log secrets.

---

### Feature 8 — Security

**AC-8.1** All credentials (Groq API key, GitHub token) SHALL be stored as Kubernetes Secrets or environment variables and never hard-coded in source code.

**AC-8.2** THE SYSTEM SHALL use `RBAC` — the healing-agent `ServiceAccount` SHALL have only the minimum permissions needed (get/list/update Deployments; get Pods, Events, Logs in the `demo` namespace).

**AC-8.3** Docker images SHALL not run as root.

---

### Feature 9 — Testing

**AC-9.1** Unit tests SHALL cover: `RemediationPolicyService` (all decision branches), `AlertPayloadParser` (valid and malformed payloads), and `LlmAnalysisService` (mocked client, including failed parse).

**AC-9.2** Integration-test placeholder classes SHALL exist for `KubernetesClient` and `GitHubClient` with `@Disabled` tests and a comment explaining what to wire up.

**AC-9.3** All tests SHALL pass with `mvn test`.
