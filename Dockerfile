# jar 는 CPU 아키텍처와 무관하므로 Gradle 빌드는 빌드 머신(amd64)에서 한 번만 돌린다.
# 이렇게 하지 않으면 arm64 이미지를 만들 때 QEMU 에뮬레이션으로 Gradle 이 돌아 몇 배 느려진다.
FROM --platform=$BUILDPLATFORM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /app
COPY . .
RUN chmod +x gradlew && ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre-jammy
RUN groupadd -r appuser && useradd -r -g appuser appuser
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
RUN chown appuser:appuser app.jar
USER appuser
ENTRYPOINT ["java", "-jar", "app.jar"]
