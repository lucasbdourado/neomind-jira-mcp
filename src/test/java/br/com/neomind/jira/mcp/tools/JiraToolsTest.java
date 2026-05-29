package br.com.neomind.jira.mcp.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import br.com.neomind.jira.mcp.client.JiraApiException;
import br.com.neomind.jira.mcp.client.JiraClient;
import br.com.neomind.jira.mcp.config.JiraMcpProperties;
import br.com.neomind.jira.mcp.parser.JiraPayloadParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

class JiraToolsTest {

    private final JiraClient jiraClient = org.mockito.Mockito.mock(JiraClient.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JiraMcpProperties properties = new JiraMcpProperties(
            "https://jira.local",
            "developer",
            "secret",
            "",
            "customfield_10007",
            "customfield_10005");

    private JiraTools tools;

    @BeforeEach
    void setUp() {
        tools = new JiraTools(jiraClient, new JiraPayloadParser(objectMapper), properties, objectMapper);
    }

    @Test
    void registersFiveAnnotatedMcpToolsWithDescriptionsAndInputSchemas() {
        Map<String, ToolCallback> callbacks = Arrays.stream(MethodToolCallbackProvider.builder()
                        .toolObjects(tools)
                        .build()
                        .getToolCallbacks())
                .collect(Collectors.toMap(callback -> callback.getToolDefinition().name(), callback -> callback));

        assertThat(callbacks).containsOnlyKeys(
                "jira_get_server_info",
                "jira_get_issue",
                "jira_search_issues",
                "jira_get_issue_comments",
                "jira_get_development_context");
        assertThat(callbacks.get("jira_get_server_info").getToolDefinition().description())
                .contains("Jira Server metadata");
        assertThat(callbacks.get("jira_get_issue").getToolDefinition().inputSchema())
                .contains("Jira issue key to retrieve");
        assertThat(callbacks.get("jira_search_issues").getToolDefinition().inputSchema())
                .contains("Maximum issues to return")
                .contains("between 1 and 100");
        assertThat(callbacks.get("jira_get_development_context").getToolDefinition().description())
                .contains("normalized")
                .contains("development context");
    }

    @Test
    void getServerInfoReturnsExpectedJsonFormat() {
        when(jiraClient.getServerInfo()).thenReturn("{\"version\":\"7.0.9\",\"serverTime\":\"2026-05-28\"}");

        String response = tools.getServerInfo();

        assertThat(response).isEqualTo("{\"version\":\"7.0.9\",\"serverTime\":\"2026-05-28\"}");
        verify(jiraClient).getServerInfo();
    }

    @Test
    void getIssueRoutesTrimmedIssueKeyToClient() {
        when(jiraClient.getIssue("ABC-123")).thenReturn("{\"key\":\"ABC-123\"}");

        String response = tools.getIssue(" ABC-123 ");

        assertThat(response).isEqualTo("{\"key\":\"ABC-123\"}");
        verify(jiraClient).getIssue("ABC-123");
    }

    @Test
    void searchIssuesValidatesAndDefaultsMaxResults() {
        when(jiraClient.searchIssues("project = ABC", 50)).thenReturn("{\"issues\":[]}");

        String response = tools.searchIssues(" project = ABC ", null);

        assertThat(response).isEqualTo("{\"issues\":[]}");
        verify(jiraClient).searchIssues("project = ABC", 50);
    }

    @Test
    void searchIssuesRejectsOutOfBoundsMaxResultsWithoutCallingClient() throws Exception {
        String response = tools.searchIssues("project = ABC", 101);

        assertThat(errorMessage(response)).isEqualTo("maxResults must be between 1 and 100");
        verifyNoInteractions(jiraClient);
    }

    @Test
    void getIssueCommentsRoutesIssueKeyToClient() {
        when(jiraClient.getIssueComments("ABC-123")).thenReturn("{\"comments\":[]}");

        String response = tools.getIssueComments("ABC-123");

        assertThat(response).isEqualTo("{\"comments\":[]}");
        verify(jiraClient).getIssueComments("ABC-123");
    }

    @Test
    void getDevelopmentContextReturnsSerializedJiraDevelopmentContext() throws Exception {
        when(jiraClient.getIssue("ABC-123")).thenReturn("""
                {
                  "key": "ABC-123",
                  "fields": {
                    "issuetype": {"name": "Story"},
                    "project": {"key": "ABC"},
                    "summary": "Implement tools",
                    "status": {"name": "In Progress"},
                    "assignee": {"displayName": "Ada Lovelace"},
                    "customfield_10007": "com.atlassian.greenhopper.service.sprint.Sprint@1[id=1,name=Sprint 5,state=ACTIVE]",
                    "customfield_10005": "PRD-123",
                    "labels": ["backend"],
                    "components": [{"name": "MCP"}],
                    "comment": {"comments": [{"author": {"displayName": "Reviewer"}, "body": "Ready", "created": "2026-05-28"}]},
                    "attachment": [{"filename": "spec.pdf", "size": 42, "content": "https://jira.local/attachment/spec.pdf"}]
                  }
                }
                """);

        JsonNode response = objectMapper.readTree(tools.getDevelopmentContext("ABC-123"));

        assertThat(response.path("key").asText()).isEqualTo("ABC-123");
        assertThat(response.path("type").asText()).isEqualTo("Story");
        assertThat(response.path("summary").asText()).isEqualTo("Implement tools");
        assertThat(response.path("sprint").asText()).isEqualTo("Sprint 5");
        assertThat(response.path("relatedPrd").asText()).isEqualTo("PRD-123");
        assertThat(response.path("labels").get(0).asText()).isEqualTo("backend");
        assertThat(response.path("components").get(0).asText()).isEqualTo("MCP");
        assertThat(response.path("comments").get(0).path("author").asText()).isEqualTo("Reviewer");
        assertThat(response.path("attachments").get(0).path("name").asText()).isEqualTo("spec.pdf");
        verify(jiraClient).getIssue("ABC-123");
    }

    @Test
    void clientExceptionReturnsCleanErrorJson() throws Exception {
        when(jiraClient.getServerInfo()).thenThrow(new JiraApiException("Jira API request failed: GET /rest/api/2/serverInfo returned HTTP 401"));

        String response = tools.getServerInfo();

        assertThat(errorMessage(response)).isEqualTo("Jira API request failed: GET /rest/api/2/serverInfo returned HTTP 401");
    }

    @Test
    void blankIssueKeyReturnsValidationErrorWithoutCallingClient() throws Exception {
        String response = tools.getIssue(" ");

        assertThat(errorMessage(response)).isEqualTo("issueKey must not be blank");
        verifyNoInteractions(jiraClient);
    }

    private String errorMessage(String json) throws Exception {
        return objectMapper.readTree(json).path("error").asText();
    }
}
