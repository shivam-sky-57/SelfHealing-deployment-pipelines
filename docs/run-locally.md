# Local Setup Guide

## Prerequisites

| Tool | Version | Install |
|---|---|---|
| Java | 21 | [adoptium.net](https://adoptium.net/) |
| Maven | 3.9+ | [maven.apache.org](https://maven.apache.org/download.cgi) |
| Docker Desktop | latest | [docker.com](https://www.docker.com/products/docker-desktop/) |
| kind | 0.23+ | [kind.sigs.k8s.io](https://kind.sigs.k8s.io/) |
| kubectl | 1.29+ | [kubernetes.io/docs/tasks/tools](https://kubernetes.io/docs/tasks/tools/) |

```bash
java -version     # 21.x
mvn -version      # 3.9.x
docker version    # needs Docker Desktop running
kind version      # v0.23.x
kubectl version --client
```

---

## Option A — kind (recommended)

```bash
# Start Docker Desktop first, then:
kind create cluster --name self-healing
kubectl cluster-info --context kind-self-healing
```

### Build and load images

```bash
# Build
mvn clean package -DskipTests -pl demo-app
docker build -t demo-app:local demo-app/

# Load into kind (kind can't see local Docker images without this)
kind load docker-image demo-app:local --name self-healing
```

### Edit image tag

In `k8s/demo-app-deployment.yaml`, change:
```yaml
image: demo-app:latest
```
to:
```yaml
image: demo-app:local
```

---

## Option B — minikube

```bash
minikube start --memory=4096 --cpus=2 --driver=docker

# Point Docker CLI at minikube's daemon (run in every terminal you use for builds)
& minikube -p minikube docker-env --shell powershell | Invoke-Expression

# Build directly into minikube
docker build -t demo-app:local demo-app/
```

---

## Deploy to Kubernetes

```bash
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/healing-agent-rbac.yaml
kubectl apply -f k8s/

kubectl get pods -n demo -w
```

Wait until all pods are `1/1 Running`.

---

## Create the healing-agent secret

```bash
kubectl create secret generic healing-agent-secrets -n demo \
  --from-literal=GROQ_API_KEY=gsk_... \
  --from-literal=GITHUB_TOKEN=ghp_...
```

Set `GITHUB_OWNER` in `k8s/healing-agent-deployment.yaml`.

---

## Port-forward and verify

```bash
# demo-app
kubectl port-forward svc/demo-app -n demo 8080:8080
curl http://localhost:8080/                    # → {"status":"ok",...}
curl http://localhost:8080/actuator/health     # → {"status":"UP"}
curl http://localhost:8080/actuator/prometheus # → Prometheus metrics

# Prometheus UI
kubectl port-forward svc/prometheus -n demo 9090:9090
# Open: http://localhost:9090

# Alertmanager UI
kubectl port-forward svc/alertmanager -n demo 9093:9093
# Open: http://localhost:9093

# healing-agent
kubectl port-forward svc/healing-agent -n demo 8081:8081
curl http://localhost:8081/actuator/health     # → {"status":"UP"}
```

---

## Useful Commands

```bash
# All resources in demo namespace
kubectl get all -n demo

# Events (sort by time — great for debugging probe failures)
kubectl get events -n demo --sort-by='.lastTimestamp'

# Pod logs
kubectl logs -n demo -l app=demo-app --all-containers

# Rollout history
kubectl rollout history deployment/demo-app -n demo

# Clean slate
kubectl delete namespace demo
kubectl apply -f k8s/namespace.yaml && kubectl apply -f k8s/
```

---

## Prometheus Queries

```promql
# Error rate fraction
rate(demo_errors_total_total[1m]) / (rate(demo_requests_total_total[1m]) + 0.001)

# Error rate percentage
rate(demo_errors_total_total[1m]) / (rate(demo_requests_total_total[1m]) + 0.001) * 100

# Request throughput (req/sec)
rate(demo_requests_total_total[1m])

# Prometheus scraping demo-app?
up{job="demo-app"}
```

> **Note:** Micrometer appends `_total` to counter names; Prometheus 2.x also appends `_total` for counter type, resulting in `demo_requests_total_total`. Check the actual metric name under **Graph** in the Prometheus UI if queries return no data.

---

## Troubleshooting

**Pods stuck in Pending:**
```bash
kubectl describe pod -n demo <pod-name>
# Look at Events: section for resource or image pull issues
```

**ImagePullBackOff:**
- Verify `image: demo-app:local` and `imagePullPolicy: IfNotPresent`
- Re-run `kind load docker-image demo-app:local --name self-healing`

**Readiness probe failing:**
```bash
kubectl logs -n demo <pod-name>
# Check for Spring Boot startup errors or missing config
```

**Port-forward not working:**
```bash
# Kill existing port-forwards
Get-Process -Name kubectl | Stop-Process   # PowerShell
pkill kubectl                              # Linux/macOS
```

**Delete cluster and start fresh:**
```bash
kind delete cluster --name self-healing
minikube delete
```
