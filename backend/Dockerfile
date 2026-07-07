FROM gradle:9.1.0-jdk25-alpine AS build
WORKDIR /workspace

COPY backend/settings.gradle.kts backend/build.gradle.kts backend/gradle.properties backend/gradle.lockfile /workspace/backend/
COPY backend/gradle /workspace/backend/gradle
COPY backend/gradlew /workspace/backend/gradlew
COPY backend/gradlew.bat /workspace/backend/gradlew.bat
COPY backend/src /workspace/backend/src

WORKDIR /workspace/backend
RUN chmod +x gradlew && ./gradlew --no-daemon bootJar

FROM eclipse-temurin:25-jre-alpine
ENV SPRING_PROFILES_ACTIVE=prod
WORKDIR /app

COPY --from=build /workspace/backend/build/libs/*.jar /app/app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
