# Live Demo Script

This walks through breaking `demo-app` and watching the system heal itself.

## Prerequisites

- kind cluster running: `kind create cluster --name self-healing`
- All manifests applied: `kubectl apply -f k8s/`
- `demo-app:local` image loaded into kind
- healing-agent secret created with `GROQ_API_KEY` and `GITHUB_TOKEN`
- Four terminal windows open

---

## Step 1 — Confirm healthy state

```bash
kubectl get pods -n demo
```

Expected:
```
NAME                             READY   STATUS    RESTARTS
demo-app-xxxxx                   1/1     Running   0
demo-app-yyyyy                   1/1     Running   0
healing-agent-zzzzz              1/1     Running   0
prometheus-aaaaa                 1/1     Running   0
alertmanager-bbbbb               1/1     Running   0
```

```bash
kubectl port-forward svc/demo-app -n demo 8080:8080
curl http://localhost:8080/
# → {"status":"ok","message":"Hello from demo-app","version":"1.0.0"}
```

---

## Step 2 — Open monitoring windows

**Terminal 2 — Prometheus UI:**
```bash
kubectl port-forward svc/prometheus -n demo 9090:9090
# Open: http://localhost:9090
# Go to: Status → Targets → confirm demo-app is UP
```

**Terminal 3 — Alertmanager UI:**
```bash
kubectl port-forward svc/alertmanager -n demo 9093:9093
# Open: http://localhost:9093
```

**Terminal 4 — healing-agent logs:**
```bash
kubectl logs -n demo -l app=healing-agent -f
```

---

## Step 3 — Break the deployment

```bash
kubectl set env deployment/demo-app -n demo FORCE_FAILURE=true
```

This triggers a rolling update so Kubernetes creates a new revision. Watch it:

```bash
kubectl rollout status deployment/demo-app -n demo
kubectl rollout history deployment/demo-app -n demo
# Should show REVISION 1 and REVISION 2
```

---

## Step 4 — Drive up the error rate

In Terminal 1, hammer the endpoint:

```bash
# PowerShell
for ($i = 0; $i -lt 200; $i++) {
    Invoke-WebRequest http://localhost:8080/ -UseBasicParsing -ErrorAction SilentlyContinue | Out-Null
    Start-Sleep -Milliseconds 200
}
```

```bash
# Linux/macOS
for i in $(seq 1 200); do curl -s http://localhost:8080/ > /dev/null; sleep 0.2; done
```

---

## Step 5 — Watch the alert fire

In the Prometheus UI at `http://localhost:9090`:

1. Go to **Alerts** tab
2. After ~60 seconds, `HighErrorRate` transitions from **inactive** → **pending** → **firing**

Query to watch in real time:
```promql
rate(demo_errors_total_total[1m]) / (rate(demo_requests_total_total[1m]) + 0.001) * 100
```

---

## Step 6 — Watch healing-agent respond

In Terminal 4 (healing-agent logs), you'll see:

```
[Incident abc-123] ===== INCIDENT PROCESSING STARTED =====
[Incident abc-123] alert=HighErrorRate severity=critical namespace=demo deployment=demo-app
[Incident abc-123] Collecting Kubernetes evidence...
[Incident abc-123] Evidence: 2 pods, 3 events, 47 log lines
[Incident abc-123] Sending evidence to Groq LLM...
[Incident abc-123] LLM: category=CONFIG_ERROR confidence=HIGH action=ROLLBACK
[Incident abc-123] LLM rootCause: FORCE_FAILURE env var set to true causing all requests to fail
[Incident abc-123] Policy: ROLLBACK — All safety guards passed...
[Incident abc-123] *** EXECUTING ROLLBACK: demo/demo-app ***
[Incident abc-123] ✓ Recovery verified for demo/demo-app
[Incident abc-123] GitHub issue created: https://github.com/YOUR_ORG/repo/issues/42
[Incident abc-123] ===== INCIDENT COMPLETE =====
```

---

## Step 7 — Confirm recovery

```bash
kubectl get pods -n demo
kubectl rollout history deployment/demo-app -n demo
# Should show REVISION 1 is now the active one
curl http://localhost:8080/
# → {"status":"ok",...}
```

---

## Step 8 — Check the GitHub issue

Open the URL from the healing-agent logs. The issue contains:

- **Summary table** — incidentId, alert, action taken, recovery status
- **LLM analysis** — root cause, confidence, category, explanation
- **Recent commits** — what changed before the incident
- **Kubernetes evidence** — pod statuses, events, container logs

---

## Cooldown Demo

Try breaking it again immediately:

```bash
kubectl set env deployment/demo-app -n demo FORCE_FAILURE=true
# Wait for HighErrorRate to fire again
```

healing-agent logs will show:
```
[Policy] DECISION: NOTIFY_ONLY (Cooldown active for demo/demo-app: last rollback was 0 minute(s) ago...)
```

No rollback is issued. A new issue is still created.

---

## Reset

```bash
kubectl set env deployment/demo-app -n demo FORCE_FAILURE=false
# Or roll back manually:
kubectl rollout undo deployment/demo-app -n demo
```
