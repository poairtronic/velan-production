# ==============================================================================
# Velan Production — Backend Dockerfile (Local Development & Containerization)
# Language: Java 21 LTS | Framework: Spring Boot 3.4.x | Build: Maven 3.9.9
# ==============================================================================

# Stage 1: Build stage with OpenJDK 21 compiler
FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /build

# Copy Maven wrapper and POM to leverage Docker layer caching
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw

# Download dependencies offline where possible
RUN ./mvnw dependency:go-offline -B || true

# Copy source code and build executable jar (skipping live DB tests)
COPY src/ src/
RUN ./mvnw clean package -DskipTests -B

# Stage 2: Minimal JRE 21 runtime container
FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app

# Create unprivileged system group and user
RUN groupadd -r velan && useradd -r -g velan -d /app -s /sbin/nologin velan

# Copy application artifact from builder stage
COPY --from=builder /build/target/velan-production-backend-*.jar app.jar
RUN chown -R velan:velan /app

USER velan

EXPOSE 8080

ENV PORT=8080 \
    SPRING_PROFILES_ACTIVE=local \
    JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
