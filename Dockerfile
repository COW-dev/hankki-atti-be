# syntax=docker/dockerfile:1

# 빌드 스테이지는 대상 플랫폼과 무관하게 러너 아키텍처에서 한 번만 돈다 (jar는 아키텍처 무관)
FROM --platform=$BUILDPLATFORM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# 빌드 스크립트를 먼저 복사해 의존성 다운로드 레이어를 소스 변경과 분리한다
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null

# 테스트는 CI test job에서 이미 통과했으므로 여기서는 실행 jar만 만든다
COPY src/main src/main
RUN ./gradlew --no-daemon bootJar \
    && cp "$(ls build/libs/*.jar | grep -v -- '-plain.jar$')" app.jar \
    && java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

FROM eclipse-temurin:21-jre
WORKDIR /app

RUN groupadd --system app && useradd --system --gid app --no-create-home app

# 자주 바뀌는 순서대로 아래에 둔다 — 의존성 레이어는 버전 간에 재사용된다
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

USER app
EXPOSE 8080

ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["java", "-Duser.timezone=Asia/Seoul", "org.springframework.boot.loader.launch.JarLauncher"]
