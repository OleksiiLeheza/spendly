# syntax=docker/dockerfile:1
#
# One Dockerfile for every Spring Boot service in the reactor. The build stage compiles the
# whole monorepo once - BuildKit deduplicates it across the service images - and SERVICE
# selects which jar the runtime stage receives:
#
#   docker build --build-arg SERVICE=user-service -t spendly/user-service .
#
# docker-compose.yml passes SERVICE for each service, so `docker compose up` needs nothing else.

FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace

COPY .mvn/ .mvn/
COPY pom.xml ./
COPY config/ config/
COPY spendly-bom/ spendly-bom/
COPY common/ common/
COPY events/ events/
COPY user-service/ user-service/
COPY account-service/ account-service/
COPY transaction-service/ transaction-service/

# Tests, Spotless, SpotBugs and the rest of the verify gate run on the host (./mvnw verify);
# the image only needs compiled, repackaged jars. Error Prone and NullAway still run, because
# they are part of compilation. The cache mount keeps the Maven repository between builds.
RUN --mount=type=cache,target=/root/.m2/repository,sharing=locked \
    mvn -B -ntp -q package -Dmaven.test.skip=true

# Boot's layered layout: dependencies change rarely and land in their own image layer, so a
# code change rebuilds only the last, small layer.
FROM eclipse-temurin:25-jre-alpine AS extract
ARG SERVICE
WORKDIR /extract
COPY --from=build /workspace/${SERVICE}/target/${SERVICE}-*.jar application.jar
RUN java -Djarmode=tools -jar application.jar extract --layers --destination extracted

FROM eclipse-temurin:25-jre-alpine
ARG SERVICE
LABEL org.opencontainers.image.title="spendly/${SERVICE}"

RUN addgroup -S spendly && adduser -S -G spendly spendly
WORKDIR /application
COPY --from=extract /extract/extracted/dependencies/ ./
COPY --from=extract /extract/extracted/spring-boot-loader/ ./
COPY --from=extract /extract/extracted/snapshot-dependencies/ ./
COPY --from=extract /extract/extracted/application/ ./
USER spendly

# The JVM sizes its heap against the container's memory limit, not the host's.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["java", "-jar", "application.jar"]
