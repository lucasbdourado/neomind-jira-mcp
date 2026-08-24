# Project State

## Active Context
- **Active Task**: Task 2 — Containerizar e publicar o MCP na VPS
- **Active Change**: `002-containerize-deploy-vps-mcp`
- **Specification**: [docs/changes/002-containerize-deploy-vps-mcp/change-spec.md](changes/002-containerize-deploy-vps-mcp/change-spec.md)
- **Current Phase**: Implementation completed & verified.

## Key Decisions & Architecture
- **Transport**: Spring AI Streamable HTTP (`spring-ai-starter-mcp-server-webmvc`).
- **Containerization**: Multi-stage Dockerfile (Maven 3.9 + Java 21 build -> Eclipse Temurin 21 JRE Alpine runtime, non-root user `appuser`).
- **Orchestration**: Docker Compose with `jira-mcp-server` and `caddy` (or `nginx`).
- **Network Isolation**: MCP Java container runs strictly on internal bridge network `mcp-internal` with no published host ports.
- **Ingress & TLS**: Reverse proxy handles public ports `80`/`443`, automatic TLS (Let's Encrypt), HTTP Basic Auth / Bearer Auth, unbuffered streaming (`flush_interval -1`), and forwards to `http://jira-mcp-server:8080/mcp`.
- **Credential Protection**: Jira secrets remain isolated in remote `.env` (`chmod 600`), never exposed to the proxy container or client machines.
- **Client Configuration**: Clients connect via HTTPS with authentication (e.g. `https://user:password@mcp.domain.com/mcp`).
