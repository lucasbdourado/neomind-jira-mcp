# Change Spec: Adapt MCP Server for Remote Streamable HTTP Access

## 1. Overview
The goal of this change is to adapt the Java 21 Spring Boot Jira MCP server from a local `stdio`-only transport to **Streamable HTTP** transport. This enables remote AI clients (such as remote agent hosts, IDEs, and desktop clients supporting HTTP MCP) to connect over network HTTP to an endpoint (defaulting to `/mcp` on port `8080`), while fully preserving all existing read-only Jira tools and environment variable-based credentials configuration.

## 2. Research & Source Context
| Source | Location / Path | Purpose |
| --- | --- | --- |
| Main App | [JiraMcpApplication.java](file:///c:/Users/lucas.dourado/projects/neomind-jira-mcp/src/main/java/br/com/neomind/jira/mcp/JiraMcpApplication.java) | Configures Spring Boot WebApplicationType and ToolCallbackProvider registration |
| Build Config | [pom.xml](file:///c:/Users/lucas.dourado/projects/neomind-jira-mcp/pom.xml) | Spring Boot & Spring AI starter dependencies |
| Config Properties | [application.properties](file:///c:/Users/lucas.dourado/projects/neomind-jira-mcp/src/main/resources/application.properties) | MCP protocol, endpoint, port, and Jira credentials properties |
| Tool Definitions | [JiraTools.java](file:///c:/Users/lucas.dourado/projects/neomind-jira-mcp/src/main/java/br/com/neomind/jira/mcp/tools/JiraTools.java) | Exposes read-only Jira operations (`jira_get_server_info`, `jira_get_issue`, `jira_search_issues`, `jira_get_issue_comments`, `jira_get_development_context`) |
| Client Configs & Docs | [README.md](file:///c:/Users/lucas.dourado/projects/neomind-jira-mcp/README.md) & [.env.example](file:///c:/Users/lucas.dourado/projects/neomind-jira-mcp/.env.example) | MCP client configuration guidelines and environment variables |

## 3. Confirmed Facts vs Assumptions
### Confirmed Facts
- Spring AI BOM version is `1.1.7` (`spring-ai-starter-mcp-server-webmvc` is supported and provides `STREAMABLE` transport).
- The existing tools (`JiraTools`) are registered via Spring AI `ToolCallbackProvider` and do not depend on the transport layer.
- Jira authentication mechanisms (Basic Auth + session cookie handling) operate over `RestClient` independent of MCP transport.

### Assumptions & Open Questions
- Default HTTP port will be `8080` (configurable via `PORT` or `SERVER_PORT` environment variable).
- Default MCP endpoint will be `/mcp` (configurable via `MCP_ENDPOINT`).
- `stdio` transport is disabled by default (`spring.ai.mcp.server.stdio=false`), replaced by `spring.ai.mcp.server.protocol=STREAMABLE`.

## 4. Current vs Expected Behavior
### Current Behavior
- Server launches with `WebApplicationType.NONE` and `spring.ai.mcp.server.stdio=true`.
- Requires execution as a sub-process attached to client `stdin`/`stdout`.
- Does not expose an HTTP port or network endpoint.

### Expected Behavior
- Server launches with `WebApplicationType.SERVLET` embedded web container (Tomcat) on port `8080` (or `PORT`).
- Exposes Streamable HTTP endpoint at `/mcp` (or `MCP_ENDPOINT`).
- Handles MCP protocol handshake (`initialize`), tool discovery (`tools/list`), and tool execution (`tools/call`) over HTTP POST / SSE.
- Fully preserves all existing Jira tool implementations and environment variable configurations (`JIRA_BASE_URL`, `JIRA_USERNAME`, `JIRA_PASSWORD`, `JIRA_COOKIE`, `JIRA_SPRINT_FIELD_ID`, `JIRA_RELATED_PRD_FIELD_ID`).

## 5. Scope & Out of Scope
### In Scope
- Upgrade dependencies in [pom.xml](file:///c:/Users/lucas.dourado/projects/neomind-jira-mcp/pom.xml) to include `spring-boot-starter-web` and `spring-ai-starter-mcp-server-webmvc`.
- Update [JiraMcpApplication.java](file:///c:/Users/lucas.dourado/projects/neomind-jira-mcp/src/main/java/br/com/neomind/jira/mcp/JiraMcpApplication.java) to enable Servlet web environment.
- Update [application.properties](file:///c:/Users/lucas.dourado/projects/neomind-jira-mcp/src/main/resources/application.properties) and [.env.example](file:///c:/Users/lucas.dourado/projects/neomind-jira-mcp/.env.example) to support `PORT`, `MCP_ENDPOINT`, `spring.ai.mcp.server.protocol=STREAMABLE`.
- Update logging configuration and tests to support web mode.
- Add integration test for HTTP MCP handshake and tool invocation.
- Update [README.md](file:///c:/Users/lucas.dourado/projects/neomind-jira-mcp/README.md) with remote Streamable HTTP instructions.

### Out of Scope
- Modifying Jira API schemas or read-only tools logic.
- Adding Jira write/mutation operations.
- Adding OAuth2 / token-based auth gateway on the MCP server itself (can be placed behind reverse proxy / container ingress if needed).

## 6. Functional Acceptance Criteria
### AC-001: HTTP Server Startup
**Given** the application is started with valid environment configuration
**When** the Spring Boot application initializes
**Then** an embedded HTTP web server listens on the configured port (default `8080`) and `stdin/stdout` transport is not required.

### AC-002: Streamable HTTP MCP Endpoint
**Given** the MCP server is running
**When** an MCP client connects via Streamable HTTP to `/mcp`
**Then** the server successfully responds to MCP protocol handshake (`initialize`) and reports its capabilities.

### AC-003: Tool Listing over HTTP
**Given** an active MCP session over Streamable HTTP
**When** the client sends a `tools/list` request
**Then** the server returns the 5 Jira tools: `jira_get_server_info`, `jira_get_issue`, `jira_search_issues`, `jira_get_issue_comments`, `jira_get_development_context`.

### AC-004: Tool Execution & Jira Communication
**Given** an active MCP session over Streamable HTTP and valid Jira credentials
**When** the client invokes `tools/call` for `jira_get_server_info`
**Then** the server queries Jira REST API and returns the execution result.

## 7. Technical Design & Contracts
- **Maven Dependencies**:
  - Add `org.springframework.boot:spring-boot-starter-web`
  - Replace `spring-ai-starter-mcp-server` with `org.springframework.ai:spring-ai-starter-mcp-server-webmvc`
- **Application Properties**:
  ```properties
  server.port=${PORT:8080}
  spring.main.web-application-type=servlet
  spring.ai.mcp.server.protocol=STREAMABLE
  spring.ai.mcp.server.streamable-http.mcp-endpoint=${MCP_ENDPOINT:/mcp}
  spring.ai.mcp.server.stdio=false
  ```
- **Application Entry Point**:
  Configure `SpringApplicationBuilder` or `SpringApplication.run` with Servlet web environment.

## 8. Validation References & Regression Risks
- **Validation**:
  - Automated tests: `mvn test` (including context load and HTTP MCP endpoint test).
  - Manual local verification: Run server locally, perform initialize JSON-RPC handshake over `/mcp`, verify tools list and execute `jira_get_server_info`.
- **Regression Risks**:
  - Port conflicts on 8080: Mitigated by allowing `PORT` or `server.port` environment override.
  - Logging interference: In stdio, logs had to go to stderr. In HTTP mode, standard logging can safely go to stdout/stderr.

## 9. Implementation Checklist
- [x] **1. Update Dependencies in pom.xml**
  - Goal: Switch from stdio MCP starter to WebMVC MCP starter and add Spring Boot Web starter.
  - Acceptance: `mvn test-compile` compiles cleanly with WebMVC and MCP WebMVC starters.
  - Depends on: None
- [x] **2. Update Application Entry Point and Properties**
  - Goal: Configure servlet web application type, server port 8080, Streamable HTTP protocol, and `/mcp` endpoint.
  - Acceptance: Application context loads in web mode and initializes Streamable HTTP endpoint.
  - Depends on: 1
- [x] **3. Update Logging and Existing Tests**
  - Goal: Adapt `JiraMcpApplicationTest` and `LogbackConfigurationTest` for Web environment.
  - Acceptance: All existing tests pass without regression.
  - Depends on: 2
- [x] **4. Add MCP Streamable HTTP Integration Test**
  - Goal: Add integration test verifying MCP initialize handshake and tool discovery over HTTP.
  - Acceptance: Integration test passes verifying HTTP MCP `/mcp` endpoint behavior.
  - Depends on: 2, 3
- [x] **5. Update Documentation & Examples**
  - Goal: Update `README.md` and `.env.example` with Streamable HTTP configuration, port settings, and remote MCP client connection instructions.
  - Acceptance: Documentation accurately explains how to run and connect to the remote MCP server.
  - Depends on: 4
- [x] **6. Verification Task**
  - Goal: Run complete test suite and build packaging (`mvn package`).
  - Acceptance: All tests pass and executable JAR is generated.
  - Depends on: 1, 2, 3, 4, 5
