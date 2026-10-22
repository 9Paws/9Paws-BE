# STAGE 1: BUILD
FROM maven:3.9-eclipse-temurin-25 AS builder
WORKDIR /app
COPY pom.xml ./
RUN mvn dependency:resolve -B
COPY src ./src
RUN mvn clean package -DskipTests -B

# STAGE 2: RUNTIME
FROM eclipse-temurin:25-jre AS runtime
RUN groupadd --system appgroup && \
    useradd --system --gid appgroup appuser
WORKDIR /app
COPY --from=builder /app/target/*.jar /app/app.jar
RUN chown -R appuser:appgroup /app
ENV JAVA_TOOL_OPTIONS="-XX:InitialRAMPercentage=25.0 -XX:MaxRAMPercentage=75.0 -Xmx450m -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError"
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
