# =============================================================
# demo-app Dockerfile
# Multi-stage build:
#   Stage 1 (builder) — compiles the app with Maven
#   Stage 2 (runtime) — runs the JAR on a minimal JRE image
#
# The final image runs as a non-root user (UID 1000) for security.
# =============================================================

# ---- Stage 1: Build ----
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

# Copy Maven wrapper and POM first — Docker layer caching means
# dependencies are only re-downloaded when pom.xml changes.
COPY pom.xml .

# Copy source code
COPY src ./src

# Download the Spring Boot parent POM and all dependencies,
# then compile and package (skip tests — tests run in CI before Docker build).
RUN apk add --no-cache maven && \
    mvn -B clean package -DskipTests

# ---- Stage 2: Runtime ----
FROM eclipse-temurin:21-jre-alpine AS runtime

# Add a non-root user and group for security.
# Running as root in a container is a security risk.
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# Copy only the fat JAR from the build stage
COPY --from=builder /build/target/demo-app-*.jar app.jar

# Change ownership to the non-root user
RUN chown appuser:appgroup app.jar

# Switch to non-root user
USER appuser

# Expose the application port
EXPOSE 8080

# JVM tuning for containers:
#   -XX:+UseContainerSupport   — respect cgroup CPU/memory limits
#   -XX:MaxRAMPercentage=75.0  — use up to 75% of container memory for heap
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
