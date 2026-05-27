# Build stage
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /workspace
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .
COPY src src
RUN chmod +x gradlew && \
    ./gradlew dependencies --no-daemon || true && \
    find /root/.gradle -name "*.exe" -type f -exec chmod +x {} \; 2>/dev/null || true && \
    ./gradlew build -x test --no-daemon

# Run stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Xmx512m", "-Xms128m", "-jar", "app.jar"]