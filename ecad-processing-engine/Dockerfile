# Dockerfile para ecad-processing-engine
FROM maven:3.9-eclipse-temurin-21 AS build
ARG MODULE_NAME=ecad-processing-engine
ARG JAR_VERSION=0.1.0-SNAPSHOT
WORKDIR /workspace
COPY pom.xml ./
COPY ecad-shared/pom.xml ecad-shared/
COPY ecad-control-center/pom.xml ecad-control-center/
COPY ecad-processing-engine/pom.xml ecad-processing-engine/
COPY ecad-document-scraper/pom.xml ecad-document-scraper/
COPY ecad-sga-status-sync/pom.xml ecad-sga-status-sync/
COPY ecad-deduplicator/pom.xml ecad-deduplicator/
COPY ecad-log-analyser/pom.xml ecad-log-analyser/
COPY ecad-integration-tests/pom.xml ecad-integration-tests/
RUN mvn -pl ${MODULE_NAME} -am -DskipTests install -Dspring-boot.repackage.skip=true
COPY . .
RUN mvn -pl ${MODULE_NAME} -am -Dmaven.test.skip=true package

FROM eclipse-temurin:21-jre-jammy
ARG MODULE_NAME=ecad-processing-engine
ARG JAR_VERSION=0.1.0-SNAPSHOT
WORKDIR /app
RUN groupadd -r appuser && useradd -r -g appuser -d /app -s /sbin/nologin appuser \
    && chown -R appuser:appuser /app
COPY --from=build /workspace/${MODULE_NAME}/target/${MODULE_NAME}-${JAR_VERSION}.jar /app/app.jar
USER appuser
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]