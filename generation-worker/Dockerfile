FROM node:24.18.0-alpine3.24@sha256:a0b9bf06e4e6193cf7a0f58816cc935ff8c2a908f81e6f1a95432d679c54fbfd AS build
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

FROM node:24.18.0-alpine3.24@sha256:a0b9bf06e4e6193cf7a0f58816cc935ff8c2a908f81e6f1a95432d679c54fbfd
ENV NODE_ENV=production HOME=/home/worker CODEX_HOME=/home/worker/.codex
RUN rm -rf /usr/local/lib/node_modules/npm /usr/local/lib/node_modules/corepack \
      /usr/local/bin/npm /usr/local/bin/npx /usr/local/bin/corepack /usr/local/bin/pnpm /usr/local/bin/pnpx \
    && addgroup -S worker \
    && adduser -S -G worker worker
WORKDIR /app
COPY --from=build --chown=worker:worker /opt/worker/package.json ./package.json
COPY --from=build --chown=worker:worker /opt/worker/node_modules ./node_modules
COPY --from=build --chown=worker:worker /opt/worker/ARTIFACT_DIGEST ./ARTIFACT_DIGEST
COPY --from=build --chown=worker:worker /workspace/generation-worker/dist ./dist
USER worker
ENTRYPOINT ["node", "dist/cli.js"]
