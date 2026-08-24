# Build stage
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# 优先 COPY 镜像配置并放到 Maven 默认位置（~/.m2/settings.xml），后续 mvn 命令自动使用
COPY maven-settings.xml /root/.m2/settings.xml

# 预拉取依赖到本地（命中 aliyun 镜像，国内构建显著加速）
COPY pom.xml .
RUN mvn -B dependency:go-offline

# 复制源码并打包（继续命中 aliyun 镜像）
COPY src/main/java ./src/main/java
RUN mvn -B clean package -DskipTests

# Run stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# 安装 docker CLI（如挂载了 /var/run/docker.sock 则需要此 CLI 来操作宿主机容器）
RUN apk add --no-cache docker-cli

COPY --from=build /app/target/python-sandbox-1.0.0.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
