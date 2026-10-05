# syntax=docker/dockerfile:1
# ---- 构建阶段：Maven + JDK 21，产出分层 jar ----
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace

# 先拷贝依赖描述文件，最大化利用 Docker 层缓存
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -q -DskipTests dependency:go-offline

COPY src/ src/
RUN ./mvnw -q -DskipTests package \
  && java -Djarmode=tools -jar target/citadel-api.jar extract --layers --destination extracted

# ---- 运行阶段：JRE 21 + 非 root 用户 ----
FROM eclipse-temurin:21-jre-jammy
RUN apt-get update \
  && apt-get install -y --no-install-recommends curl \
  && rm -rf /var/lib/apt/lists/* \
  && groupadd --system citadel && useradd --system --gid citadel citadel

WORKDIR /app
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

USER citadel
EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=5 \
  CMD curl -fsS http://127.0.0.1:8080/actuator/health | grep -q '"UP"' || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
