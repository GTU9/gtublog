FROM node:24.13.1-alpine3.23@sha256:4f696fbf39f383c1e486030ba6b289a5d9af541642fc78ab197e584a113b9c03 AS build
RUN corepack enable && corepack prepare pnpm@11.7.0 --activate
WORKDIR /workspace
COPY package.json pnpm-workspace.yaml pnpm-lock.yaml ./
COPY generation-worker/package.json generation-worker/package.json
RUN pnpm install --frozen-lockfile --filter @gtublog/generation-worker...
COPY generation-worker/tsconfig.json generation-worker/tsconfig.build.json generation-worker/
COPY generation-worker/Dockerfile generation-worker/Dockerfile
COPY generation-worker/src generation-worker/src
RUN pnpm --dir generation-worker build \
    && pnpm --filter @gtublog/generation-worker deploy --prod --legacy /opt/worker \
    && find /opt/worker/package.json /opt/worker/node_modules generation-worker/dist \
      generation-worker/Dockerfile generation-worker/tsconfig.json generation-worker/tsconfig.build.json \
      pnpm-lock.yaml -type f -print0 \
      | sort -z | xargs -0 sha256sum | sha256sum | cut -d ' ' -f 1 > /opt/worker/ARTIFACT_DIGEST

FROM node:24.13.1-alpine3.23@sha256:4f696fbf39f383c1e486030ba6b289a5d9af541642fc78ab197e584a113b9c03
ENV NODE_ENV=production HOME=/home/worker CODEX_HOME=/home/worker/.codex
RUN addgroup -S worker && adduser -S -G worker worker
WORKDIR /app
COPY --from=build --chown=worker:worker /opt/worker/package.json ./package.json
COPY --from=build --chown=worker:worker /opt/worker/node_modules ./node_modules
COPY --from=build --chown=worker:worker /opt/worker/ARTIFACT_DIGEST ./ARTIFACT_DIGEST
COPY --from=build --chown=worker:worker /workspace/generation-worker/dist ./dist
USER worker
ENTRYPOINT ["node", "dist/cli.js"]
