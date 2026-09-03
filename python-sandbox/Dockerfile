# Build stage
# 注意：docker-compose 以仓库根目录为 build context（共用根目录 maven-settings.xml），
# 因此以下 COPY 路径均相对仓库根（python-sandbox/...）。
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# 优先 COPY 镜像配置并放到 Maven 默认位置（~/.m2/settings.xml），后续 mvn 命令自动使用
COPY maven-settings.xml /root/.m2/settings.xml

# 预拉取依赖到本地（命中 aliyun 镜像，国内构建显著加速）
COPY python-sandbox/pom.xml .
RUN mvn -B dependency:go-offline

# 复制源码与资源并打包（继续命中 aliyun 镜像）
COPY python-sandbox/src/main/java ./src/main/java
COPY python-sandbox/src/main/resources ./src/main/resources
RUN mvn -B clean package -DskipTests

# Run stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# 安装 docker CLI（如挂载了 /var/run/docker.sock 则需要此 CLI 来操作宿主机容器）
RUN apk add --no-cache docker-cli

COPY --from=build /app/target/python-sandbox-1.0.1.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
