# Deployment Report: `doable1` — Spring Boot Course Notification Service

## 1. Project Overview

| Property | Value |
|---|---|
| Framework | Spring Boot 4.0.6 |
| Java Version | 21 |
| Packaging | JAR (via `spring-boot-maven-plugin`) |
| Exposed Port | 8080 (Spring default) |
| Key Dependencies | Spring Web, Spring Mail, Apache POI, Lombok, Gemini REST API |
| Entry Point | `com.epam.doable1.Doable1Application` |

### API Endpoints
| Method | Path | Description |
|---|---|---|
| POST | `/api/course/notify` | Upload Excel files + SMTP config → send course completion emails |
| GET | `/api/demo/active-employees` | Download demo active-employees `.xlsx` |
| GET | `/api/demo/course-enrollments` | Download demo course-enrollments `.xlsx` |

---

## 2. 🚨 Security Issues (Fix Before Deploying)

### 2.1 Exposed Secret in `.env`
- **Problem**: `.env` file contains a **real Gemini API key** (`GEMINI_API_KEY=AIzaSy...`) but `.env` is **not in `.gitignore`**.
  - If pushed to GitHub, this key is publicly exposed.
- **Fix**:
  1. Add `.env` to `.gitignore` immediately.
  2. **Rotate/revoke** the existing Gemini API key in [Google AI Studio](https://aistudio.google.com/app/apikey).
  3. Store the new key as a GitHub Actions secret (`GEMINI_API_KEY`) — see Section 4.

### 2.2 SMTP Credentials
- SMTP credentials are passed **at runtime per request** (good design), but they are never encrypted in transit internally.
- Ensure the app is always served over **HTTPS** (TLS termination via Ingress or load balancer).

---

## 3. What's Missing / Needs to Be Created

| Artifact | Status | Needed For |
|---|---|---|
| `Dockerfile` | ❌ Missing | Docker image build |
| `.github/workflows/ci-cd.yml` | ❌ Missing | GitHub Actions CI/CD |
| `k8s/` manifests (Deployment, Service, Secret, Ingress) | ❌ Missing | Kubernetes deployment |
| `.env` in `.gitignore` | ❌ Missing | Security |
| Spring Boot Actuator (health endpoint) | ❌ Not added | Kubernetes liveness/readiness probes |
| Application tests | ❌ Minimal | CI test gate |

---

## 4. Things to Add — Step by Step

---

### 4.1 Fix `.gitignore`

Add the following line to `.gitignore`:

```gitignore
# Environment secrets
.env
```

---

### 4.2 Dockerfile (Multi-Stage Build)

Create `Dockerfile` at project root. Uses a multi-stage build to keep the final image lean.

```dockerfile
# ── Stage 1: Build ──────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
# Download dependencies first (layer caching)
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn package -DskipTests -q

# ── Stage 2: Runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
# Non-root user for security
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring
COPY --from=builder /app/target/doable1-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Why multi-stage?** The final image only contains the JRE + JAR (~200MB vs ~700MB with full JDK/Maven).

---

### 4.3 GitHub Actions CI/CD Pipeline

Create `.github/workflows/ci-cd.yml`:

```yaml
name: CI/CD Pipeline

on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main]

env:
  REGISTRY: ghcr.io
  IMAGE_NAME: ${{ github.repository }}   # e.g., org/doable1

jobs:
  # ── Job 1: Build & Test ──────────────────────────────────────────────────
  build-test:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Set up Java 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: maven

      - name: Build and test
        run: mvn verify -q
        env:
          GEMINI_API_KEY: ${{ secrets.GEMINI_API_KEY }}   # optional, for integration tests

  # ── Job 2: Build & Push Docker Image ────────────────────────────────────
  docker-build-push:
    needs: build-test
    runs-on: ubuntu-latest
    if: github.ref == 'refs/heads/main' && github.event_name == 'push'
    permissions:
      contents: read
      packages: write

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Log in to GitHub Container Registry
        uses: docker/login-action@v3
        with:
          registry: ${{ env.REGISTRY }}
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      - name: Extract Docker metadata (tags, labels)
        id: meta
        uses: docker/metadata-action@v5
        with:
          images: ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}
          tags: |
            type=sha,prefix=sha-
            type=raw,value=latest,enable={{is_default_branch}}

      - name: Build and push Docker image
        uses: docker/build-push-action@v5
        with:
          context: .
          push: true
          tags: ${{ steps.meta.outputs.tags }}
          labels: ${{ steps.meta.outputs.labels }}
          cache-from: type=gha
          cache-to: type=gha,mode=max

  # ── Job 3: Deploy to Kubernetes (optional) ──────────────────────────────
  deploy:
    needs: docker-build-push
    runs-on: ubuntu-latest
    if: github.ref == 'refs/heads/main' && github.event_name == 'push'

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Set up kubectl
        uses: azure/setup-kubectl@v3

      - name: Configure kubeconfig
        run: echo "${{ secrets.KUBECONFIG }}" | base64 -d > kubeconfig.yaml

      - name: Deploy to Kubernetes
        env:
          KUBECONFIG: kubeconfig.yaml
          IMAGE_TAG: sha-${{ github.sha }}
        run: |
          kubectl set image deployment/doable1 \
            doable1=${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}:$IMAGE_TAG \
            -n doable1
          kubectl rollout status deployment/doable1 -n doable1 --timeout=120s
```

#### GitHub Secrets Required

Go to **GitHub repo → Settings → Secrets and variables → Actions** and add:

| Secret Name | Value |
|---|---|
| `GEMINI_API_KEY` | Your (rotated) Gemini API key |
| `KUBECONFIG` | Base64-encoded kubeconfig for your Kubernetes cluster (only needed if deploying to K8s) |

> `GITHUB_TOKEN` is provided automatically by GitHub Actions — no setup needed.

---

### 4.4 Add Spring Boot Actuator (for Kubernetes health probes)

Add to `pom.xml` `<dependencies>`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

Add to `application.properties`:

```properties
# Expose only health endpoint (don't expose /actuator/env etc.)
management.endpoints.web.exposure.include=health
management.endpoint.health.show-details=never
```

This enables `GET /actuator/health` which Kubernetes uses to determine if the pod is alive/ready.

---

### 4.5 Kubernetes Manifests

Create a `k8s/` directory with the following files:

#### `k8s/namespace.yaml`
```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: doable1
```

#### `k8s/secret.yaml` *(do NOT commit actual values — inject via CI)*
```yaml
apiVersion: v1
kind: Secret
metadata:
  name: doable1-secrets
  namespace: doable1
type: Opaque
stringData:
  GEMINI_API_KEY: ""   # Injected by CI pipeline via: kubectl create secret generic ...
```

#### `k8s/configmap.yaml`
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: doable1-config
  namespace: doable1
data:
  SPRING_APPLICATION_NAME: doable1
  SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE: 10MB
  SPRING_SERVLET_MULTIPART_MAX_REQUEST_SIZE: 20MB
```

#### `k8s/deployment.yaml`
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: doable1
  namespace: doable1
  labels:
    app: doable1
spec:
  replicas: 2
  selector:
    matchLabels:
      app: doable1
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxUnavailable: 1
      maxSurge: 1
  template:
    metadata:
      labels:
        app: doable1
    spec:
      containers:
        - name: doable1
          image: ghcr.io/<YOUR_ORG>/doable1:latest   # replaced by CI with exact SHA tag
          ports:
            - containerPort: 8080
          envFrom:
            - configMapRef:
                name: doable1-config
          env:
            - name: GEMINI_API_KEY
              valueFrom:
                secretKeyRef:
                  name: doable1-secrets
                  key: GEMINI_API_KEY
          resources:
            requests:
              memory: "256Mi"
              cpu: "250m"
            limits:
              memory: "512Mi"
              cpu: "500m"
          livenessProbe:
            httpGet:
              path: /actuator/health
              port: 8080
            initialDelaySeconds: 30
            periodSeconds: 15
            failureThreshold: 3
          readinessProbe:
            httpGet:
              path: /actuator/health
              port: 8080
            initialDelaySeconds: 20
            periodSeconds: 10
            failureThreshold: 3
```

#### `k8s/service.yaml`
```yaml
apiVersion: v1
kind: Service
metadata:
  name: doable1-svc
  namespace: doable1
spec:
  selector:
    app: doable1
  ports:
    - protocol: TCP
      port: 80
      targetPort: 8080
  type: ClusterIP
```

#### `k8s/ingress.yaml` *(requires an Ingress controller, e.g., nginx-ingress)*
```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: doable1-ingress
  namespace: doable1
  annotations:
    nginx.ingress.kubernetes.io/proxy-body-size: "25m"   # allow large Excel uploads
    nginx.ingress.kubernetes.io/client-max-body-size: "25m"
spec:
  ingressClassName: nginx
  rules:
    - host: doable1.yourdomain.com
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: doable1-svc
                port:
                  number: 80
  tls:
    - hosts:
        - doable1.yourdomain.com
      secretName: doable1-tls   # created by cert-manager or manually
```

---

## 5. Complete Checklist

### Security (Do First)
- [ ] Add `.env` to `.gitignore`
- [ ] **Rotate the Gemini API key** (it was in `.env` which is currently unignored)
- [ ] Add `GEMINI_API_KEY` as a GitHub Actions secret

### Docker
- [ ] Create `Dockerfile` (multi-stage, non-root user)
- [ ] Test locally: `docker build -t doable1 . && docker run -p 8080:8080 -e GEMINI_API_KEY=... doable1`

### GitHub Actions
- [ ] Create `.github/workflows/ci-cd.yml`
- [ ] Add required GitHub secrets (`GEMINI_API_KEY`, `KUBECONFIG` if using K8s)
- [ ] Verify pipeline runs on push to `main`

### Spring Boot Changes
- [ ] Add `spring-boot-starter-actuator` dependency to `pom.xml`
- [ ] Configure actuator in `application.properties` (expose only `health`)

### Kubernetes (Optional but Recommended for Production)
- [ ] Create `k8s/namespace.yaml`
- [ ] Create `k8s/secret.yaml` (values injected by CI, not stored in Git)
- [ ] Create `k8s/configmap.yaml`
- [ ] Create `k8s/deployment.yaml`
- [ ] Create `k8s/service.yaml`
- [ ] Create `k8s/ingress.yaml` (configure with your domain + TLS)
- [ ] Replace `<YOUR_ORG>` placeholder in image references

---

## 6. Architecture Diagram (Text)

```
GitHub Push (main)
       │
       ▼
┌──────────────────────────────────────────────────┐
│           GitHub Actions Pipeline                │
│  1. mvn verify (build + test)                    │
│  2. docker build + push → ghcr.io/org/doable1   │
│  3. kubectl set image → rolling update           │
└──────────────────────────────────────────────────┘
                          │
                          ▼
              ┌───────────────────────┐
              │   Kubernetes Cluster  │
              │  Namespace: doable1   │
              │                       │
              │  [Pod 1] [Pod 2]  ←── replicas: 2
              │   doable1:sha-xxx     │
              │                       │
              │  ConfigMap (env vars) │
              │  Secret (GEMINI key)  │
              └────────┬──────────────┘
                       │ ClusterIP :80
                       ▼
              ┌─────────────────┐
              │  Ingress (nginx) │
              │  TLS termination │
              │  doable1.domain  │
              └─────────────────┘
                       │
                  Internet (HTTPS)
```

---

## 7. Notes on This Application's Specific Needs

1. **Multipart Upload Size**: The app accepts Excel file uploads up to 10MB/20MB. The Ingress annotation `proxy-body-size: 25m` is critical — without it, nginx will reject large uploads with HTTP 413.

2. **No Database**: This app is stateless (no DB, no persistent storage). Kubernetes deployment is straightforward — no PersistentVolumeClaims needed.

3. **SMTP at Runtime**: SMTP credentials are passed per-request by the caller — they are never stored in the app. This is a good design for a multi-tenant tool, but ensure the `/api/course/notify` endpoint is protected (authentication/rate-limiting) in production.

4. **Gemini API Key is Optional**: The app has a graceful fallback if `GEMINI_API_KEY` is absent. K8s Secret can be left empty if LLM personalisation is not needed.

5. **Horizontal Scaling**: Since the app is stateless, it scales horizontally. `replicas: 2` in the Deployment gives basic HA. Add an HPA (HorizontalPodAutoscaler) based on CPU if traffic is variable.
