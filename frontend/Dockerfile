# syntax=docker/dockerfile:1
#
# Angular production build served by nginx. The bundle carries src/environments/environment.ts
# (Keycloak at localhost:8080, user-service at localhost:8082), which is what the browser on
# the developer's machine reaches when the whole stack runs under docker compose.

FROM node:26-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
# Install scripts for esbuild, lmdb and friends are approved in package.json (allowScripts);
# without them ng build has no native esbuild and fails.
RUN npm ci --no-audit --no-fund

COPY . .
RUN npm run build -- --configuration production

FROM nginx:1.29-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/frontend/browser/ /usr/share/nginx/html/
EXPOSE 80
