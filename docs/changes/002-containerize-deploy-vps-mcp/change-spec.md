# Change Spec: Containerize and Deploy MCP Server to VPS with Reverse Proxy & HTTPS

## 1. Overview
This specification details the design and deployment architecture for containerizing the Spring Boot Jira MCP Server (Streamable HTTP transport) and hosting it securely on a VPS. 

The architecture isolates the Java MCP container in a private Docker network (with no host ports published) and places it behind a reverse proxy (Caddy or Nginx) that handles SSL/TLS termination, incoming traffic on ports 80/443, client authentication, and SSE/HTTP stream forwarding to the internal `/mcp` endpoint. Jira credentials remain strictly on the remote server environment and are never exposed to clients or the proxy container.

```mermaid
flowchart LR
    Client["MCP Client<br/>(Claude Desktop / Cursor / IDE)"]
    subgraph VPS ["Remote VPS Host"]
        FW["Firewall (UFW)<br/>Ports 80, 443 (and 22 SSH)"]
        subgraph Docker ["Docker Compose"]
            Proxy["Reverse Proxy (Caddy / Nginx)<br/>• TLS Termination (Let's Encrypt)<br/>• Client Authentication"]
            subgraph InternalNet ["Internal Docker Bridge Network (mcp-net)"]
                Server["Jira MCP Server<br/>(Java 21 / Spring Boot)<br/>• Port 8080 (No Host Binding)<br/>• Reads .env Jira Secrets"]
            end
        end
    end
    Jira["Jira Server Instance<br/>(Legacy Jira Server)"]

    Client -- "HTTPS / Basic Auth" --> FW
    FW --> Proxy
    Proxy -- "HTTP (Private)" --> Server
    Server -- "REST API (Basic/Cookie Auth)" --> Jira
```

---

## 2. Research & Source Context
| Source | Location / Path | Purpose |
| --- | --- | --- |
| MCP Application | [JiraMcpApplication.java](file:///c:/Users/lucas.dourado/projects/neomind-jira-mcp/src/main/java/br/com/neomind/jira/mcp/JiraMcpApplication.java) | Spring Boot Servlet web entry point |
| Maven Config | [pom.xml](file:///c:/Users/lucas.dourado/projects/neomind-jira-mcp/pom.xml) | Java 21 packaging and Spring Boot Maven plugin |
| Properties | [application.properties](file:///c:/Users/lucas.dourado/projects/neomind-jira-mcp/src/main/resources/application.properties) | Configures port 8080, Streamable HTTP `/mcp` endpoint, and Jira property bindings |
| Change 001 | [docs/changes/001-adapt-mcp-remote-streamable-http/change-spec.md](file:///c:/Users/lucas.dourado/projects/neomind-jira-mcp/docs/changes/001-adapt-mcp-remote-streamable-http/change-spec.md) | Baseline specification for Streamable HTTP transport |
| Env Template | [.env.example](file:///c:/Users/lucas.dourado/projects/neomind-jira-mcp/.env.example) | Environment variables template |

---

## 3. Confirmed Facts vs Assumptions
### Confirmed Facts
- The Spring Boot application runs on Java 21, listens on port `8080` (or `PORT`), and exposes Streamable HTTP at `/mcp` (or `MCP_ENDPOINT`).
- All Jira interactions are read-only and consume credentials via environment variables (`JIRA_BASE_URL`, `JIRA_USERNAME`, `JIRA_PASSWORD`, `JIRA_COOKIE`, `JIRA_SPRINT_FIELD_ID`, `JIRA_RELATED_PRD_FIELD_ID`).
- MCP Streamable HTTP requires long-lived HTTP POST and Server-Sent Events (SSE) connections with unbuffered streaming.

### Assumptions & Design Decisions
- **Container Base Image**: Multi-stage Docker build utilizing `maven:3.9.9-eclipse-temurin-21-alpine` for building and `eclipse-temurin:21-jre-alpine` for runtime to minimize container size (<200MB) and attack surface.
- **Process Security**: Container will run as a non-privileged user (`appuser`, UID 10001).
- **Reverse Proxy**: Caddy is provided as the primary recommended proxy for zero-touch Let's Encrypt TLS automation and native streaming support. A production-ready Nginx configuration will also be provided for teams standardizing on Nginx/Certbot.
- **Client Authentication**: HTTP Basic Authentication or Bearer Token authentication enforced at the reverse proxy layer before proxying requests to the backend.
- **Port Exposure**: Only ports `80` and `443` are exposed on the host. The Java MCP container has no published ports (`ports:` omitted in compose; communicates strictly over internal Docker bridge `mcp-net`).

---

## 4. Current vs Expected Behavior
### Current Behavior
- MCP server runs locally as a standalone JAR executable (`java -jar target/jira-mcp-server-1.0.0.jar`).
- No Dockerfile, Docker Compose file, or reverse proxy configuration exists in the repository.
- No VPS deployment guide, security firewall instructions, or authentication proxy recipes exist.

### Expected Behavior
- Standardized `Dockerfile` builds a production-ready container image for the Java MCP server.
- `docker-compose.yml` orchestrates the `jira-mcp-server` and `reverse-proxy` services with an isolated private network.
- Reverse proxy automatically terminates HTTPS via Let's Encrypt / ZeroSSL, authenticates client requests, disables buffering for SSE / Streamable HTTP, and forwards traffic to `http://jira-mcp-server:8080/mcp`.
- Unauthenticated requests are rejected with `401 Unauthorized` at the proxy boundary.
- Jira credentials exist only in the remote `.env` file on the VPS with restricted filesystem permissions (`chmod 600`).
- Remote MCP clients connect via `https://<domain>/mcp` using credentials defined at the proxy layer.

---

## 5. Scope & Out of Scope
### In Scope
- Create multi-stage `Dockerfile` with non-root security and healthcheck.
- Create `.dockerignore` to keep build context clean and fast.
- Create `docker-compose.yml` (and production overrides if needed) defining `jira-mcp-server`, `caddy` / `nginx`, volumes, and internal private network.
- Create reverse proxy configuration templates (`Caddyfile` and `nginx.conf`) with TLS, SSE streaming optimizations, and authentication.
- Create `.env.production.example` documenting all VPS and proxy configuration parameters.
- Provide a VPS Security & Deployment Runbook covering UFW firewall setup, DNS pointing, secret management, and client connection examples (Claude Desktop, Cursor, generic HTTP MCP clients).
- Add automated verification script / docker build smoke test in repository.

### Out of Scope
- Provisioning or purchasing the VPS instance or domain registrar DNS records (handled by operator).
- Modifying Java application code or adding Spring Security dependency to the JAR (authentication is handled at the network edge/reverse proxy).
- Jira write/mutating API operations.

---

## 6. Functional Acceptance Criteria

### AC-001: Java Containerization
**Given** the repository source code
**When** `docker build` is executed
**Then** a lightweight, non-root Docker image running Java 21 JRE is created and starts the Spring Boot MCP server on port 8080.

### AC-002: Docker Compose Orchestration & Network Isolation
**Given** the Docker Compose configuration
**When** `docker compose up -d` is executed
**Then** both `jira-mcp-server` and `reverse-proxy` containers start, share an internal bridge network `mcp-net`, and `jira-mcp-server` does not bind any port to the VPS host interface.

### AC-003: Reverse Proxy HTTPS & Domain Handling
**Given** a registered domain pointing to the VPS IP and ports 80/443 open
**When** an external HTTPS request hits `https://<domain>/mcp`
**Then** the reverse proxy successfully terminates TLS with a valid certificate and proxies the streamable request to `http://jira-mcp-server:8080/mcp`.

### AC-004: Reverse Proxy Authentication Gate
**Given** the reverse proxy is configured with client authentication credentials
**When** an unauthenticated client sends a request to `/mcp`
**Then** the proxy immediately rejects the request with HTTP `401 Unauthorized`.
**When** a client presents valid authentication credentials
**Then** the request is forwarded to the MCP server and streamable JSON-RPC responses are returned.

### AC-005: Jira Access & Credential Isolation
**Given** the MCP server running in the isolated Docker container with valid Jira env vars
**When** an authenticated client executes a Jira tool call (e.g. `jira_get_server_info`)
**Then** the MCP container reaches the external Jira server and returns issue data, while Jira credentials remain exclusively within the remote MCP container environment.

---

## 7. Technical Design & Contracts

### 7.1 Multi-Stage Dockerfile (`Dockerfile`)
```dockerfile
# Stage 1: Build
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder
WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --from=builder /build/target/jira-mcp-server-*.jar app.jar

USER appuser:appgroup

ENV PORT=8080 \
    MCP_ENDPOINT=/mcp \
    JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Dfile.encoding=UTF-8"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
```

### 7.2 Docker Compose (`docker-compose.yml`)
```yaml
services:
  jira-mcp-server:
    build:
      context: .
      dockerfile: Dockerfile
    image: jira-mcp-server:latest
    container_name: jira-mcp-server
    restart: unless-stopped
    env_file:
      - .env
    environment:
      PORT: "8080"
      MCP_ENDPOINT: "/mcp"
    networks:
      - mcp-internal
    # Notice: NO ports: section exposed to the host!

  caddy:
    image: caddy:2.9-alpine
    container_name: caddy-mcp-proxy
    restart: unless-stopped
    ports:
      - "80:80"
      - "443:443"
    environment:
      MCP_DOMAIN: ${MCP_DOMAIN}
      MCP_AUTH_USER: ${MCP_AUTH_USER}
      MCP_AUTH_PASSWORD_HASH: ${MCP_AUTH_PASSWORD_HASH}
    volumes:
      - ./deploy/caddy/Caddyfile:/etc/caddy/Caddyfile:ro
      - caddy_data:/data
      - caddy_config:/config
    networks:
      - mcp-internal
    depends_on:
      - jira-mcp-server

networks:
  mcp-internal:
    driver: bridge

volumes:
  caddy_data:
  caddy_config:
```

### 7.3 Caddy Configuration (`deploy/caddy/Caddyfile`)
```caddy
{$MCP_DOMAIN} {
    # Client Basic Authentication
    basicauth /mcp* {
        {$MCP_AUTH_USER} {$MCP_AUTH_PASSWORD_HASH}
    }

    # Reverse proxy to internal Java MCP server with unbuffered streaming for SSE / HTTP Streamable
    reverse_proxy jira-mcp-server:8080 {
        flush_interval -1
        header_up X-Real-IP {remote_host}
        header_up X-Forwarded-For {remote_host}
        header_up X-Forwarded-Proto {scheme}
    }

    # Security headers
    header {
        Strict-Transport-Security "max-age=31536000; includeSubDomains; preload"
        X-Content-Type-Options "nosniff"
        X-Frame-Options "DENY"
        Referrer-Policy "no-referrer"
    }

    log {
        output stdout
        format console
    }
}
```

### 7.4 Nginx Alternative Configuration (`deploy/nginx/nginx.conf`)
```nginx
events { worker_connections 1024; }

http {
    upstream mcp_backend {
        server jira-mcp-server:8080;
    }

    server {
        listen 80;
        server_name ${MCP_DOMAIN};
        return 301 https://$host$request_uri;
    }

    server {
        listen 443 ssl http2;
        server_name ${MCP_DOMAIN};

        ssl_certificate /etc/letsencrypt/live/${MCP_DOMAIN}/fullchain.pem;
        ssl_certificate_key /etc/letsencrypt/live/${MCP_DOMAIN}/privkey.pem;

        location /mcp {
            auth_basic "Jira MCP Restricted";
            auth_basic_user_file /etc/nginx/.htpasswd;

            proxy_pass http://mcp_backend;
            proxy_http_version 1.1;

            # Streamable HTTP / SSE optimizations
            proxy_set_header Connection '';
            proxy_buffering off;
            proxy_cache off;
            proxy_read_timeout 86400s;
            proxy_send_timeout 86400s;
            chunked_transfer_encoding off;

            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto $scheme;
        }
    }
}
```

### 7.5 Production Environment Template (`.env.production.example`)
```bash
# --- Remote VPS & Proxy Config ---
MCP_DOMAIN=mcp-jira.yourdomain.com
MCP_AUTH_USER=mcpuser
# Generated via: docker run --rm caddy:2.9-alpine caddy hash-password --plaintext "YourStrongPassword"
MCP_AUTH_PASSWORD_HASH=$2a$14$exampleHashedPasswordHere...

# --- Jira Secrets (Remote only, never committed) ---
JIRA_BASE_URL=https://jira.yourcompany.com
JIRA_USERNAME=service-account-jira
JIRA_PASSWORD=super-secret-jira-password
JIRA_COOKIE=
JIRA_SPRINT_FIELD_ID=customfield_10007
JIRA_RELATED_PRD_FIELD_ID=customfield_10005
```

### 7.6 VPS Security & Firewall Runbook
1. **Firewall (UFW)**:
   ```bash
   sudo ufw default deny incoming
   sudo ufw default allow outgoing
   sudo ufw allow 22/tcp
   sudo ufw allow 80/tcp
   sudo ufw allow 443/tcp
   sudo ufw enable
   ```
2. **File Permissions**:
   ```bash
   chmod 600 .env
   chmod 700 deploy
   ```
3. **Client Configuration Example (Claude Desktop / Cursor)**:
   ```json
   {
     "mcpServers": {
       "jira-remote": {
         "url": "https://mcpuser:YourStrongPassword@mcp-jira.yourdomain.com/mcp"
       }
     }
   }
   ```

---

## 8. Validation References & Regression Risks
- **Validation**:
  - `docker build -t jira-mcp-server:test .` creates and validates container image locally.
  - `docker compose config` validates syntax, networks, and service bindings.
  - Integration smoke test verifying proxy HTTP Basic Auth rejection (401 without auth, 200 with auth) and JSON-RPC initialization handshake over HTTP.
- **Regression Risks**:
  - SSE Buffering in Reverse Proxy: Mitigated by `flush_interval -1` (Caddy) / `proxy_buffering off` (Nginx).
  - Port leak on host: Mitigated by avoiding `ports:` in `jira-mcp-server` compose service and only attaching to `mcp-internal` bridge network.
  - Credential leakage: Mitigated by ignoring `.env` in `.gitignore`, setting file permissions to `600`, and ensuring proxy container has no Jira credentials passed to it.

---

## 9. Sequential Implementation Checklist

- [x] **1. Create Dockerfile and .dockerignore**
  - Goal: Build multi-stage Java 21 container image with non-root security.
  - Acceptance: `docker build -t jira-mcp-server .` compiles cleanly and produces functional image.
  - Depends on: None

- [x] **2. Create Reverse Proxy Configurations (Caddy & Nginx)**
  - Goal: Provide zero-touch Caddy Caddyfile (with SSE unbuffering & basic auth) and Nginx alternative config.
  - Acceptance: Configurations validate syntax and define streaming reverse proxy rules for `/mcp`.
  - Depends on: None

- [x] **3. Create Docker Compose Orchestration**
  - Goal: Define `docker-compose.yml` tying `jira-mcp-server` and reverse proxy on private `mcp-internal` network.
  - Acceptance: `docker compose config` passes validation with network isolation.
  - Depends on: 1, 2

- [x] **4. Create Production Env Template and VPS Runbook Documentation**
  - Goal: Create `.env.production.example` and update `README.md` / `deploy/README.md` with complete VPS installation, UFW firewall rules, password hashing, and client setup instructions.
  - Acceptance: Documentation provides step-by-step reproducible deployment guide.
  - Depends on: 3

- [x] **5. Local Container Smoke Test & Verification**
  - Goal: Spin up compose stack locally with test credentials, verify port isolation, verify 401 on unauthenticated access, and verify 200 + MCP handshake on authenticated access.
  - Acceptance: End-to-end containerized MCP handshake succeeds over reverse proxy.
  - Depends on: 1, 2, 3, 4
