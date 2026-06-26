# syntax=docker/dockerfile:1.7

FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /workspace

COPY backend/pom.xml backend/pom.xml
COPY backend/src backend/src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -f backend/pom.xml -Dmaven.test.skip=true package

FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=build /workspace/backend/target/vasp-show-backend-0.0.1-SNAPSHOT.jar /app/app.jar
COPY documents/data/frontend_template_data.mv.db /app/documents/data/frontend_template_data.mv.db

EXPOSE 8080

ENV JAVA_OPTS=""

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
