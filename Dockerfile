FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /app
COPY . .
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre-jammy
RUN groupadd -r appuser && useradd -r -g appuser appuser
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
RUN chown appuser:appuser app.jar
USER appuser
ENTRYPOINT ["java", "-jar", "app.jar"]
