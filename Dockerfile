# syntax=docker/dockerfile:1
# Imagem do serviço. Os testes completos (unitários + ITs com Testcontainers, que exigem
# Docker) rodam no CI antes da publicação; aqui só empacotamos o jar.
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace
COPY .mvn ./.mvn
COPY mvnw pom.xml ./
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -ntp package -DskipTests

FROM eclipse-temurin:21-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /workspace/target/app.jar app.jar
USER 10001
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
    CMD curl -fsS http://localhost:8080/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=65.0", "-jar", "app.jar"]
