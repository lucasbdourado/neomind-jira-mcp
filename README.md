# jira-mcp-server

Read-only Model Context Protocol (MCP) server for legacy Jira Server instances. It runs locally as a Java 21 Spring Boot application, communicates with MCP clients over stdio, and calls Jira REST API v2 using your own Jira credentials.

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
mvn clean package
```

The build creates:

```text
target/jira-mcp-server-1.0.0.jar
```

## Run Locally

Set the environment variables, then start the stdio MCP server:

```bash
export JIRA_BASE_URL="https://jira.example.com"
export JIRA_USERNAME="your.username"
export JIRA_PASSWORD="your.password.or.token"
export JIRA_SPRINT_FIELD_ID="customfield_10007"

java -jar target/jira-mcp-server-1.0.0.jar
```

PowerShell example:

```powershell
$env:JIRA_BASE_URL = "https://jira.example.com"
$env:JIRA_USERNAME = "your.username"
$env:JIRA_PASSWORD = "your.password.or.token"
$env:JIRA_SPRINT_FIELD_ID = "customfield_10007"

java -jar target\jira-mcp-server-1.0.0.jar
```

Optional initial cookie example:

```powershell
$env:JIRA_BASE_URL = "https://jira.example.com"
$env:JIRA_COOKIE = "JSESSIONID=your.session.id; atlassian.xsrf.token=your.xsrf.token"
$env:JIRA_SPRINT_FIELD_ID = "customfield_10007"

java -jar target\jira-mcp-server-1.0.0.jar
```

This process is intended to be launched by an MCP client. It keeps stdout reserved for JSON-RPC MCP traffic and sends application logs to stderr.

## Antigravity Configuration

Create or update the Antigravity MCP configuration file:

```text
C:\Users\your.user\.gemini\antigravity\mcp_config.json
```

Restart or reload MCP servers in Antigravity after changing the configuration. If the Jira browser session expires and `JIRA_USERNAME`/`JIRA_PASSWORD` are configured, the server refreshes `JSESSIONID` automatically through `/login.jsp` after a 401 response.

## Cursor Configuration

Create or update `.cursor/mcp.json` in the workspace that should use the Jira tools:

```json
{
  "mcpServers": {
    "jira": {
      "command": "java",
      "args": [
        "-jar",
        "C:\\Users\\your.user\\path\\to\\neomind-jira-mcp\\target\\jira-mcp-server-1.0.0.jar"
      ],
      "env": {
        "JIRA_BASE_URL": "https://jira.example.com",
        "JIRA_USERNAME": "your.username",
        "JIRA_PASSWORD": "your.password.or.token",
        "JIRA_SPRINT_FIELD_ID": "customfield_10007"
      }
    }
  }
}
```

Restart Cursor after changing the MCP configuration.

## Claude Desktop Configuration

Add the server to the `mcpServers` object in your Claude Desktop configuration file.

Windows configuration file:

```text
%APPDATA%\Claude\claude_desktop_config.json
```

Example:

```json
{
  "mcpServers": {
    "jira": {
      "command": "java",
      "args": [
        "-jar",
        "C:\\Users\\your.user\\path\\to\\neomind-jira-mcp\\target\\jira-mcp-server-1.0.0.jar"
      ],
      "env": {
        "JIRA_BASE_URL": "https://jira.example.com",
        "JIRA_USERNAME": "your.username",
        "JIRA_PASSWORD": "your.password.or.token",
        "JIRA_SPRINT_FIELD_ID": "customfield_10007"
      }
    }
  }
}
```

Restart Claude Desktop after changing the configuration.

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
