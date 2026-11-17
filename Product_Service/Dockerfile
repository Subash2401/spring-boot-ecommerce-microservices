# ---- Stage 1: build the jar with Maven ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
# --mount=type=cache keeps Maven's downloads (/root/.m2) in Docker's build cache between
# builds, shared by all services: a dependency is downloaded once, not on every build.
# The cache is not part of the image. sharing=locked: one build uses it at a time, because
# Maven's local repository is not safe for parallel writes.
RUN --mount=type=cache,target=/root/.m2,sharing=locked mvn package -DskipTests -B

# ---- Stage 2: a small runtime image with only the JRE and the jar ----
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system spring && useradd --system --gid spring spring
COPY --from=build /app/target/*.jar app.jar
USER spring
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
