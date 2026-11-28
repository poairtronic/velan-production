# ==============================================================================
# Velan Production — Backend Dockerfile (Multi-Stage, Layered Spring Boot Jar)
# ==============================================================================

FROM eclipse-temurin:25-jdk-jammy AS builder
WORKDIR /build

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw
RUN ./mvnw dependency:go-offline -B

COPY src/ src/
RUN ./mvnw package -DskipTests -B \
    && java -Djarmode=tools -jar target/velan-production-backend-*.jar extract --layers --launcher --destination extracted

FROM eclipse-temurin:25-jre-jammy AS runtime
WORKDIR /app

RUN groupadd -r velan && useradd -r -g velan -d /app -s /sbin/nologin velan

COPY --from=builder --chown=velan:velan /build/extracted/dependencies/ ./
COPY --from=builder --chown=velan:velan /build/extracted/spring-boot-loader/ ./
COPY --from=builder --chown=velan:velan /build/extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=velan:velan /build/extracted/application/ ./

USER velan

EXPOSE 8080

ENV PORT=8080 \
    TZ=UTC \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
