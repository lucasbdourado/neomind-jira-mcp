# jira-mcp-server

Read-only Model Context Protocol (MCP) server for legacy Jira Server instances. It runs as a Java 21 Spring Boot application, communicates with MCP clients over **Streamable HTTP** (at `/mcp` on port `8080` by default), and calls Jira REST API v2 using your own Jira credentials.

## Features

- `jira_get_server_info`: verifies Jira connectivity and returns server metadata.
- `jira_get_issue`: returns the raw Jira issue JSON payload for one issue key.
- `jira_search_issues`: runs a JQL search with a bounded result limit.
- `jira_get_issue_comments`: returns comments for one issue key.
- `jira_get_development_context`: returns compact development context extracted from an issue, including Sprint, related PRD, attachments metadata, comments, and timetracking.

The server is intentionally read-only. It does not create issues, update fields, transition statuses, or add comments.

## Requirements

- Java 21
- Maven 3.9+
- Network access to your Jira Server base URL
- Jira username plus password or token that can read the target projects

## Configuration

Configure the server with environment variables. The repository includes [.env.example](.env.example) as a template.

| Variable | Required | Example | Description |
|---|---:|---|---|
| `JIRA_BASE_URL` | Yes | `https://jira.example.com` | Base URL of the Jira Server instance. Do not include `/rest/api/2`. |
| `JIRA_USERNAME` | Yes, unless `JIRA_COOKIE` is set | `your.username` | Jira account username for Basic Authentication and automatic `/login.jsp` session refresh. |
| `JIRA_PASSWORD` | Yes, unless `JIRA_COOKIE` is set | `your.password.or.token` | Jira password or access token used for Basic Authentication and automatic `/login.jsp` session refresh. |
| `JIRA_SPRINT_FIELD_ID` | Yes | `customfield_10007` | Jira custom field ID that contains Sprint data. |

Optional variables:

| Variable | Default | Description |
|---|---|---|
| `PORT` | `8080` | HTTP port where the MCP server listens. |
| `MCP_ENDPOINT` | `/mcp` | Path for the Streamable HTTP MCP endpoint. |
| `JIRA_COOKIE` | empty | Optional initial raw Jira browser cookie header. If a Jira REST request returns 401, the server posts `JIRA_USERNAME` and `JIRA_PASSWORD` to `/login.jsp`, stores the returned `JSESSIONID`, and retries the request once. |
| `JIRA_RELATED_PRD_FIELD_ID` | `customfield_10005` | Jira custom field ID that stores the related PRD reference or URL. |
| `MCP_SERVER_NAME` | `jira-mcp-server` | MCP server name reported to clients. |
| `MCP_SERVER_VERSION` | `1.0.0` | MCP server version reported to clients. |

### Finding Custom Field IDs

Jira Server custom fields appear in REST payloads as IDs like `customfield_10007`.

To find the Sprint field ID:

1. Open an issue that has Sprint information in Jira.
2. Retrieve the raw issue payload through Jira REST API:

   ```bash
   curl -u "$JIRA_USERNAME:$JIRA_PASSWORD" "$JIRA_BASE_URL/rest/api/2/issue/ABC-123"
   ```

3. Search the JSON response for fields named `customfield_*` whose value contains Sprint or GreenHopper data.
4. Set `JIRA_SPRINT_FIELD_ID` to that field ID.

If `JIRA_SPRINT_FIELD_ID` is wrong, `jira_get_development_context` still works, but Sprint data will be empty or missing.

## Build

Build the executable Spring Boot JAR:

```bash
mvn package
```

The build creates:

```text
target/jira-mcp-server-1.0.0.jar
```

## Run Locally

Set the environment variables, then start the Streamable HTTP MCP server:

```bash
export JIRA_BASE_URL="https://jira.example.com"
export JIRA_USERNAME="your.username"
export JIRA_PASSWORD="your.password.or.token"
export JIRA_SPRINT_FIELD_ID="customfield_10007"
export PORT="8080"

java -jar target/jira-mcp-server-1.0.0.jar
```

PowerShell example:

```powershell
$env:JIRA_BASE_URL = "https://jira.example.com"
$env:JIRA_USERNAME = "your.username"
$env:JIRA_PASSWORD = "your.password.or.token"
$env:JIRA_SPRINT_FIELD_ID = "customfield_10007"
$env:PORT = "8080"

java -jar target\jira-mcp-server-1.0.0.jar
```

Once running, the MCP server is accessible at `http://localhost:8080/mcp`.

## Docker & Remote VPS Deployment

You can run the server in Docker or deploy it to a remote Linux VPS behind a reverse proxy (Caddy or Nginx) with automatic HTTPS (TLS) and authentication.

### Local Docker Run

Build and run using Docker Compose:

```bash
docker compose up -d --build
```

### Production VPS Deployment

See the detailed [deploy/README.md](deploy/README.md) for step-by-step instructions on:
- Configuring the UFW firewall (exposing only ports `80`, `443`, and `22`).
- Setting up DNS and automatic Let's Encrypt TLS via Caddy.
- Generating bcrypt authentication hashes for client Basic Auth.
- Keeping Jira credentials strictly inside `.env` on the VPS host with `chmod 600`.
- Connecting Claude Desktop, Cursor, and other MCP clients over HTTPS.

Example production compose start:

```bash
cp .env.production.example .env
chmod 600 .env
# Edit .env with your domain, credentials, and password hash
docker compose up -d --build
```

## MCP Client Configuration

### Remote Streamable HTTP (Recommended)

Clients connecting over Streamable HTTP can be configured to point to the server URL (`http://localhost:8080/mcp` or remote host):

#### Antigravity Configuration

In `mcp_config.json`:

```json
{
  "mcpServers": {
    "jira": {
      "url": "http://localhost:8080/mcp"
    }
  }
}
```

#### Cursor Configuration

In `.cursor/mcp.json`:

```json
{
  "mcpServers": {
    "jira": {
      "url": "http://localhost:8080/mcp"
    }
  }
}
```

#### Claude Desktop / Generic MCP Client Configuration

For clients supporting remote HTTP/SSE servers:

```json
{
  "mcpServers": {
    "jira": {
      "url": "http://localhost:8080/mcp"
    }
  }
}
```

## Example Tool Queries

Ask your MCP client natural-language questions that map to the exposed tools:

- "Use Jira to check the server info."
- "Get the raw Jira issue payload for `ABC-123`."
- "Search Jira for `project = ABC AND status != Done ORDER BY updated DESC`, maximum 10 results."
- "Get comments from Jira issue `ABC-123`."
- "Get the development context for Jira issue `ABC-123`."

Equivalent structured tool calls:

```json
{
  "tool": "jira_get_server_info",
  "arguments": {}
}
```

```json
{
  "tool": "jira_get_issue",
  "arguments": {
    "issueKey": "ABC-123"
  }
}
```

```json
{
  "tool": "jira_search_issues",
  "arguments": {
    "jql": "project = ABC AND status != Done ORDER BY updated DESC",
    "maxResults": 10
  }
}
```

```json
{
  "tool": "jira_get_issue_comments",
  "arguments": {
    "issueKey": "ABC-123"
  }
}
```

```json
{
  "tool": "jira_get_development_context",
  "arguments": {
    "issueKey": "ABC-123"
  }
}
```

## Development Notes

- Keep credentials out of commits and MCP chat transcripts.
- Use `.env.example` as a template only; store real values in local shell or MCP client environment configuration.
- `jira_get_development_context` returns attachment metadata only. It does not download or inline attachment contents.
- Search `maxResults` defaults to `50` and must be between `1` and `100`.
