# Stage 1: Build Java 21 Application
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder
WORKDIR /build

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Run as non-privileged user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --from=builder /build/target/jira-mcp-server-*.jar app.jar

USER appuser:appgroup

ENV PORT=8080 \
    MCP_ENDPOINT=/mcp \
    JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Dfile.encoding=UTF-8"

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
  CMD wget -q -O - http://127.0.0.1:${PORT:-8080}/actuator/health || exit 0

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
