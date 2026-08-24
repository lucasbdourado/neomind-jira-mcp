# VPS Deployment & Security Runbook for Jira MCP Server

This guide covers deploying the containerized Jira MCP server to a remote Linux VPS behind a secure reverse proxy with HTTPS, automatic TLS, authentication, and network isolation.

---

## 1. Prerequisites on VPS
- Linux VPS (IP: `187.127.20.59`) com Ubuntu 22.04 / 24.04 LTS ou Debian 12
- Docker Engine 24+ e plugin Docker Compose (`docker compose`)
- **Sobre o Domínio e HTTPS**:
  - Como a VPS possui IP público (`187.127.20.59`) e não possui domínio próprio, a melhor opção para ter **HTTPS/TLS válido e oficial do Let's Encrypt** sem custos e sem registrar domínio é usar o wildcard DNS **`187.127.20.59.sslip.io`** (ou `187.127.20.59.nip.io`).
  - O serviço `sslip.io` resolve automaticamente qualquer subdomínio do tipo `<ip>.sslip.io` para o próprio IP, permitindo que o Caddy emita o certificado SSL automaticamente via ACME / Let's Encrypt.
  - Alternativamente, pode-se usar o próprio IP `187.127.20.59` (com TLS autoassinado interno pelo Caddy).

---

## 2. Step 1: Firewall Configuration (UFW)
Only expose necessary ports (`22` for SSH, `80` for HTTP-to-HTTPS redirect, and `443` for HTTPS):

```bash
# Allow SSH first so you don't lock yourself out
sudo ufw allow 22/tcp

# Allow Web traffic
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp

# Set defaults & enable firewall
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw enable
sudo ufw status verbose
```

---

## 3. Step 2: Clone Repository & Setup Secrets
On the VPS:
```bash
git clone <your-repo-url> /opt/jira-mcp
cd /opt/jira-mcp

# Copy production environment template
cp .env.production.example .env

# Restrict permissions so only root / deploy user can read secrets
chmod 600 .env
```

---

## 4. Step 3: Generate Password Hash for MCP Authentication
Generate a bcrypt hash for Caddy using the official Caddy image:

```bash
docker run --rm caddy:2.9-alpine caddy hash-password --plaintext "YourStrongPasswordHere"
```
Copy the generated hash (e.g., `$2a$14$...`) into your `.env` file under `MCP_AUTH_PASSWORD_HASH`.

---

## 5. Step 4: Configure `.env`
Edit `.env` and fill in:
- `MCP_DOMAIN=187.127.20.59.sslip.io`
- `MCP_AUTH_USER=mcpuser`
- `MCP_AUTH_PASSWORD_HASH=<the bcrypt hash generated in step 3>`
- `JIRA_BASE_URL=https://jira.yourcompany.com`
- `JIRA_USERNAME=your-jira-service-user`
- `JIRA_PASSWORD=your-jira-password`

---

## 6. Step 5: Start Services with Docker Compose
```bash
# Build image and start services in background
docker compose up -d --build

# Check status
docker compose ps

# View real-time logs
docker compose logs -f
```

Caddy will automatically obtain a free TLS certificate from Let's Encrypt / ZeroSSL for your domain.

---

## 7. Step 6: Test Remote Access
Test authentication and streamable endpoint:

```bash
# Test 1: Unauthenticated request (must return HTTP 401 Unauthorized)
curl -i https://187.127.20.59.sslip.io/mcp

# Test 2: Authenticated request
curl -i -u mcpuser:YourStrongPasswordHere \
  -H "Content-Type: application/json" \
  -X POST https://187.127.20.59.sslip.io/mcp \
  -d '{"jsonrpc":"2.0","id":"1","method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"test-client","version":"1.0"}}}'
```

---

## 8. Client Configuration Examples

### Claude Desktop (`claude_desktop_config.json`)
```json
{
  "mcpServers": {
    "jira-remote": {
      "url": "https://mcpuser:YourStrongPasswordHere@187.127.20.59.sslip.io/mcp"
    }
  }
}
```

### Cursor (`mcp.json`) / Generic MCP Client
```json
{
  "mcpServers": {
    "jira-server": {
      "url": "https://187.127.20.59.sslip.io/mcp",
      "headers": {
        "Authorization": "Basic bWNwdXNlcjpZb3VyU3Ryb25nUGFzc3dvcmRIZXJl"
      }
    }
  }
}
```
*(Note: `bWNwdXNlcjpZb3VyU3Ryb25nUGFzc3dvcmRIZXJl` is `base64(mcpuser:YourStrongPasswordHere)`)*

---

## 9. Security Verification Checklist
- [x] Java port `8080` is not published to the internet (accessible only inside `mcp-internal` network).
- [x] Reverse proxy terminates HTTPS with Let's Encrypt TLS.
- [x] Endpoint `/mcp` rejects unauthenticated access with 401 Unauthorized.
- [x] Jira credentials reside exclusively in `/opt/jira-mcp/.env` (`chmod 600`) and are never sent to clients.
- [x] Proxy unbuffers streaming connections (`flush_interval -1`) for real-time SSE.
