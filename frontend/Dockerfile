FROM node:24.18.0-alpine3.24@sha256:a0b9bf06e4e6193cf7a0f58816cc935ff8c2a908f81e6f1a95432d679c54fbfd AS build
RUN corepack enable && corepack prepare pnpm@11.7.0 --activate
WORKDIR /workspace

COPY package.json pnpm-workspace.yaml pnpm-lock.yaml /workspace/
COPY frontend/package.json /workspace/frontend/package.json
RUN pnpm install --frozen-lockfile --filter @gtublog/frontend...

COPY frontend /workspace/frontend
WORKDIR /workspace/frontend
RUN pnpm build

FROM node:24.18.0-alpine3.24@sha256:a0b9bf06e4e6193cf7a0f58816cc935ff8c2a908f81e6f1a95432d679c54fbfd
ENV NODE_ENV=production \
    PORT=3000 \
    HOSTNAME=0.0.0.0
WORKDIR /app

COPY --from=build /workspace/frontend/.next/standalone /app
COPY --from=build /workspace/frontend/.next/static /app/frontend/.next/static

WORKDIR /app/frontend
EXPOSE 3000
ENTRYPOINT ["node", "server.js"]
