# ---------------------------------------------------------------------------
# Stage 1: build
# Uses the full Maven + JDK image because we need the compiler and dependency
# resolution here. This stage is discarded from the final image.
# ---------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# Copy the POM first and resolve dependencies in its own layer.
# Docker caches this layer, so rebuilds skip downloads unless pom.xml changes.
COPY pom.xml .
RUN mvn -q -B dependency:go-offline

# Now copy the source and build the shaded JAR.
COPY src ./src
RUN mvn -q -B package -DskipTests

# ---------------------------------------------------------------------------
# Stage 2: runtime
# Slim JRE-only image. Contains the fat JAR and nothing else.
# ---------------------------------------------------------------------------
FROM eclipse-temurin:17-jre
WORKDIR /app

# Bring in the artifact produced by stage 1.
COPY --from=build /build/target/cryptolens.jar app.jar

# The app listens on 8080; document it for `docker run -p`.
EXPOSE 8080

# Exec form so signals (Ctrl+C, docker stop) reach the JVM directly.
ENTRYPOINT ["java", "-jar", "app.jar"]