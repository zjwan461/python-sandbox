# Build stage
# 注意：docker-compose 以仓库根目录为 build context（共用根目录 maven-settings.xml），
# 因此以下 COPY 路径均相对仓库根（admin-server/...）。
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# 优先 COPY 镜像配置并放到 Maven 默认位置（~/.m2/settings.xml），后续 mvn 命令自动使用
COPY maven-settings.xml /root/.m2/settings.xml

# 预拉取依赖到本地（命中 aliyun 镜像，国内构建显著加速）
COPY admin-server/pom.xml .
RUN mvn -B dependency:go-offline

# 复制源码并打包（继续命中 aliyun 镜像）
COPY admin-server/src/main/java ./src/main/java
COPY admin-server/src/main/resources ./src/main/resources
RUN mvn -B clean package -DskipTests

# Run stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# 安装 curl 供容器 healthcheck 使用
RUN apk add --no-cache curl

COPY --from=build /app/target/admin-server-1.0.0.jar app.jar

EXPOSE 9090

ENTRYPOINT ["java", "-jar", "app.jar"]
