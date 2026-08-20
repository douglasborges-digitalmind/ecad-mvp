FROM maven:3.9-eclipse-temurin-21 AS build
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
RUN mvn -pl ecad-document-scraper -am -DskipTests install -Dspring-boot.repackage.skip=true
COPY . .
RUN mvn -pl ecad-document-scraper -am -Dmaven.test.skip=true install
RUN PLAYWRIGHT_BROWSERS_PATH=/ms-playwright mvn -pl ecad-document-scraper -DskipTests org.codehaus.mojo:exec-maven-plugin:3.5.0:java -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install --with-deps chromium"

FROM eclipse-temurin:21-jre-jammy
ARG JAR_VERSION=0.1.0-SNAPSHOT
WORKDIR /app
ENV PLAYWRIGHT_BROWSERS_PATH=/ms-playwright
RUN apt-get update \
	&& apt-get install -y --no-install-recommends \
		ca-certificates \
		curl \
		fonts-liberation \
		libasound2 \
		libatk-bridge2.0-0 \
		libatk1.0-0 \
		libatomic1 \
		libatspi2.0-0 \
		libavif13 \
		libcairo2 \
		libcups2 \
		libdbus-1-3 \
		libdrm2 \
		libenchant-2-2 \
		libevent-2.1-7 \
		libflite1 \
		libgbm1 \
		libgles2 \
		libglib2.0-0 \
		libgstreamer-gl1.0-0 \
		libgstreamer-plugins-bad1.0-0 \
		libgstreamer-plugins-base1.0-0 \
		libgtk-3-0 \
		libharfbuzz-icu0 \
		libhyphen0 \
		libmanette-0.2-0 \
		libnspr4 \
		libnss3 \
		libopus0 \
		libpango-1.0-0 \
		libsecret-1-0 \
		libvpx7 \
		libwebpdemux2 \
		libwebpmux3 \
		libwoff1 \
		libx11-6 \
		libx11-xcb1 \
		libx264-163 \
		libxcb1 \
		libxcomposite1 \
		libxdamage1 \
		libxext6 \
		libxfixes3 \
		libxkbcommon0 \
		libxrandr2 \
		libxslt1.1 \
	&& rm -rf /var/lib/apt/lists/*
COPY --from=build /ms-playwright /ms-playwright
COPY --from=build /workspace/ecad-document-scraper/target/ecad-document-scraper-${JAR_VERSION}.jar /app/app.jar
RUN groupadd -r appuser && useradd -r -g appuser -d /app -s /sbin/nologin appuser \
	&& chown -R appuser:appuser /app /ms-playwright
USER appuser
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD pgrep -f "ecad-document-scraper" > /dev/null || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
