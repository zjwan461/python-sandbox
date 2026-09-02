# Build stage — Vite 构建静态产物
# 注意：docker-compose 中本服务 build context 为 ./admin-web，COPY 路径相对该目录。
FROM node:20-alpine AS build
WORKDIR /app

# 优先复制依赖清单，命中 Docker 层缓存
COPY package.json package-lock.json ./
RUN npm ci --registry=https://registry.npmmirror.com

# 复制源码并构建
COPY . ./
RUN npm run build

# Run stage — Nginx 静态托管 + /admin-api 反向代理
FROM nginx:1.27-alpine

# 站点配置（SPA history 路由回退 + admin-server 反代）
COPY nginx.conf /etc/nginx/conf.d/default.conf

COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
