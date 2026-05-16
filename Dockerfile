# ─── Stage 1: Build ───────────────────────────────────────────────
# Use Maven image to build the jar
FROM maven:3.9.5-eclipse-temurin-17 AS build

# Set working directory inside container
WORKDIR /app

# Copy pom.xml first (Docker caches layers - speeds up rebuild)
COPY pom.xml .

# Download dependencies (cached if pom.xml didn't change)
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build the jar (skip tests during Docker build)
RUN mvn package -DskipTests

# ─── Stage 2: Run ─────────────────────────────────────────────────
# Use small Java runtime image (not full Maven - saves space)
FROM eclipse-temurin:17-jre-alpine

# Set working directory
WORKDIR /app

# Copy only the jar from the build stage
COPY --from=build /app/target/simbu-app-1.0.jar simbu-app.jar

# Expose port 8080 (Spring Boot default)
EXPOSE 8080

# Command to run the app when container starts
ENTRYPOINT ["java", "-jar", "simbu-app.jar"]
