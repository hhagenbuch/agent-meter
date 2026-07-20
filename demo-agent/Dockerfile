# Build context is the repo root (the reactor build needs the sibling meter-* modules).
# Note: not Docker-built in this dev environment; standard multi-stage Spring Boot image.
FROM maven:3-eclipse-temurin-26 AS build
WORKDIR /src
COPY . .
RUN mvn -q -pl demo-agent -am -DskipTests package

FROM eclipse-temurin:25-jre
COPY --from=build /src/demo-agent/target/demo-agent-0.1.0-SNAPSHOT.jar /app.jar
EXPOSE 8086
ENTRYPOINT ["java", "-jar", "/app.jar"]
