package br.com.neomind.jira.mcp.tools;

import java.util.Map;

import br.com.neomind.jira.mcp.client.JiraApiException;
import br.com.neomind.jira.mcp.client.JiraClient;
import br.com.neomind.jira.mcp.config.JiraMcpProperties;
import br.com.neomind.jira.mcp.model.JiraDevelopmentContext;
import br.com.neomind.jira.mcp.parser.JiraPayloadParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class JiraTools {

    private static final int DEFAULT_SEARCH_MAX_RESULTS = 50;
    private static final int MIN_SEARCH_MAX_RESULTS = 1;
    private static final int MAX_SEARCH_MAX_RESULTS = 100;

    private final JiraClient jiraClient;
    private final JiraPayloadParser jiraPayloadParser;
    private final JiraMcpProperties properties;
    private final ObjectMapper objectMapper;

    public JiraTools(
            JiraClient jiraClient,
            JiraPayloadParser jiraPayloadParser,
            JiraMcpProperties properties,
            ObjectMapper objectMapper) {
        this.jiraClient = jiraClient;
        this.jiraPayloadParser = jiraPayloadParser;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Tool(name = "jira_get_server_info", description = "Get Jira Server metadata, including version, build, and server time, to verify connectivity.")
    public String getServerInfo() {
        return execute(jiraClient::getServerInfo);
    }

    @Tool(name = "jira_get_issue", description = "Get the raw Jira REST API issue JSON payload for a specific issue key.")
    public String getIssue(
            @ToolParam(description = "Jira issue key to retrieve, for example ABC-123.") String issueKey) {
        return execute(() -> jiraClient.getIssue(requireText(issueKey, "issueKey")));
    }

    @Tool(name = "jira_search_issues", description = "Search Jira issues using JQL and return Jira's raw search response.")
    public String searchIssues(
            @ToolParam(description = "Jira Query Language expression, for example project = ABC ORDER BY created DESC.") String jql,
            @ToolParam(required = false, description = "Maximum issues to return. Defaults to 50 and must be between 1 and 100.") Integer maxResults) {
        return execute(() -> jiraClient.searchIssues(requireText(jql, "jql"), boundedMaxResults(maxResults)));
    }

    @Tool(name = "jira_get_issue_comments", description = "Get the raw Jira comments JSON payload for a specific issue key.")
    public String getIssueComments(
            @ToolParam(description = "Jira issue key whose comments should be retrieved, for example ABC-123.") String issueKey) {
        return execute(() -> jiraClient.getIssueComments(requireText(issueKey, "issueKey")));
    }

    @Tool(name = "jira_get_development_context", description = "Get a normalized, compact development context JSON payload for a Jira issue.")
    public String getDevelopmentContext(
            @ToolParam(description = "Jira issue key to normalize into development context, for example ABC-123.") String issueKey) {
        return execute(() -> {
            String issueJson = jiraClient.getIssue(requireText(issueKey, "issueKey"));
            JiraDevelopmentContext context = jiraPayloadParser.parseIssue(issueJson, properties);
            return writeJson(context);
        });
    }

    private String execute(ToolOperation operation) {
        try {
            return operation.execute();
        } catch (JiraApiException | IllegalArgumentException exception) {
            return errorJson(exception.getMessage());
        } catch (RuntimeException exception) {
            return errorJson("Jira tool execution failed: " + exception.getClass().getSimpleName());
        }
    }

    private String requireText(String value, String parameterName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(parameterName + " must not be blank");
        }
        return value.trim();
    }

    private Integer boundedMaxResults(Integer maxResults) {
        int value = maxResults == null ? DEFAULT_SEARCH_MAX_RESULTS : maxResults;
        if (value < MIN_SEARCH_MAX_RESULTS || value > MAX_SEARCH_MAX_RESULTS) {
            throw new IllegalArgumentException("maxResults must be between 1 and 100");
        }
        return value;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Unable to serialize Jira tool response", exception);
        }
    }

    private String errorJson(String message) {
        return writeJson(Map.of("error", message));
    }

    @FunctionalInterface
    private interface ToolOperation {
        String execute();
    }
}
