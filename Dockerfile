# ■■ Stage 1: Build the JAR with Maven ■■■■■■■■■■■■■■■■■■■■■■■■■■■
# Digest-pinned: Render builds this file independently of CI, so a floating tag would
# let Trivy scan different layers than ship. Tag kept only so Dependabot can scope its
# digest updates — without it, it resolves against `latest`.
FROM eclipse-temurin:21-jdk-alpine@sha256:4ec2402e5ebb803add08b063b9e5e52e7c11961caaae1f490479d925753f0d92 AS builder
WORKDIR /app
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B
COPY src src
RUN ./mvnw clean package -DskipTests

# ■■ Stage 2: Run with lightweight JRE ■■■■■■■■■■■■■■■■■■■■■■■■■■■
FROM eclipse-temurin:21-jre-alpine@sha256:426401268a42785be73823f6115ee0e721bdb59c12c779947b83fcead1a66645
RUN addgroup -S spring && adduser -S spring -G spring
WORKDIR /app
# Wildcard so a <version> bump doesn't break the build. The pre-repackage jar is
# *.jar.original, so exactly one file matches.
COPY --from=builder /app/target/*.jar app.jar
RUN chown spring:spring app.jar
USER spring
# Metadata only — Render injects its own PORT at runtime.
EXPOSE 8081
# Shell form, so ${PORT} expands at runtime rather than being fixed at 8081.
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider "http://localhost:${PORT:-8081}/api/health" || exit 1

# ■■ Build provenance ■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■■
# Must stay LAST: GIT_SHA changes every commit, so anything below it is cache-busted
# on every build. Render never passes the build-arg and supplies RENDER_GIT_COMMIT
# at runtime instead (see app.git-sha in application.properties).
ARG GIT_SHA=unknown
LABEL org.opencontainers.image.revision="$GIT_SHA"
ENV APP_GIT_SHA=$GIT_SHA
ENTRYPOINT ["java", "-jar", "app.jar"]
