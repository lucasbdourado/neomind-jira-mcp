package br.com.neomind.jira.mcp.parser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import br.com.neomind.jira.mcp.config.JiraMcpProperties;
import br.com.neomind.jira.mcp.model.JiraDevelopmentContext;
import br.com.neomind.jira.mcp.model.JiraDevelopmentContext.AttachmentMetadata;
import br.com.neomind.jira.mcp.model.JiraDevelopmentContext.CommentSummary;
import br.com.neomind.jira.mcp.model.JiraDevelopmentContext.TimeTrackingSummary;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class JiraPayloadParser {

    private static final String DEFAULT_RELATED_PRD_FIELD_ID = "customfield_10005";
    private static final Pattern LEGACY_SPRINT_NAME = Pattern.compile("(?<=\\[|,)name=([^,\\]]+)");

    private final ObjectMapper objectMapper;

    public JiraPayloadParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public JiraDevelopmentContext parseIssue(String issueJson, JiraMcpProperties properties) {
        return parseIssue(issueJson, properties.sprintFieldId(), properties.relatedPrdFieldId());
    }

    public JiraDevelopmentContext parseIssue(String issueJson, String sprintFieldId) {
        return parseIssue(issueJson, sprintFieldId, DEFAULT_RELATED_PRD_FIELD_ID);
    }

    public JiraDevelopmentContext parseIssue(String issueJson, String sprintFieldId, String relatedPrdFieldId) {
        JsonNode root = readTree(issueJson);
        JsonNode fields = root.path("fields");

        return new JiraDevelopmentContext(
                text(root, "key"),
                text(fields.path("issuetype"), "name"),
                firstText(fields.path("project"), "key", "name"),
                text(fields, "summary"),
                text(fields, "description"),
                text(fields.path("status"), "name"),
                userDisplayName(fields.path("assignee")),
                userDisplayName(fields.path("reporter")),
                textArray(fields.path("labels"), null),
                textArray(fields.path("components"), "name"),
                textArray(fields.path("fixVersions"), "name"),
                sprintName(fields.path(sprintFieldId)),
                compactValue(fields.path(relatedPrdFieldId)),
                attachments(fields.path("attachment")),
                comments(fields.path("comment").path("comments")),
                timetracking(fields.path("timetracking")));
    }

    private JsonNode readTree(String issueJson) {
        try {
            return objectMapper.readTree(issueJson);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to parse Jira issue JSON", exception);
        }
    }

    private String sprintName(JsonNode sprintNode) {
        List<String> names = new ArrayList<>();
        collectSprintNames(sprintNode, names);
        return names.isEmpty() ? null : names.getLast();
    }

    private void collectSprintNames(JsonNode node, List<String> names) {
        if (isMissingOrNull(node)) {
            return;
        }
        if (node.isArray()) {
            node.forEach(item -> collectSprintNames(item, names));
            return;
        }
        if (node.isObject()) {
            addIfPresent(names, text(node, "name"));
            return;
        }
        if (node.isTextual()) {
            addIfPresent(names, parseLegacySprintName(node.asText()));
            return;
        }
        addIfPresent(names, compactValue(node));
    }

    private String parseLegacySprintName(String value) {
        Matcher matcher = LEGACY_SPRINT_NAME.matcher(value);
        if (matcher.find()) {
            return blankToNull(matcher.group(1));
        }
        return blankToNull(value);
    }

    private List<AttachmentMetadata> attachments(JsonNode attachmentsNode) {
        List<AttachmentMetadata> attachments = new ArrayList<>();
        if (!attachmentsNode.isArray()) {
            return attachments;
        }
        attachmentsNode.forEach(attachment -> attachments.add(new AttachmentMetadata(
                text(attachment, "filename"),
                userDisplayName(attachment.path("author")),
                attachment.path("size").asLong(0),
                firstText(attachment, "content", "self"))));
        return attachments;
    }

    private List<CommentSummary> comments(JsonNode commentsNode) {
        List<CommentSummary> comments = new ArrayList<>();
        if (!commentsNode.isArray()) {
            return comments;
        }
        commentsNode.forEach(comment -> comments.add(new CommentSummary(
                userDisplayName(comment.path("author")),
                text(comment, "body"),
                text(comment, "created"))));
        return comments;
    }

    private TimeTrackingSummary timetracking(JsonNode timetrackingNode) {
        if (!timetrackingNode.isObject()) {
            return null;
        }
        return new TimeTrackingSummary(
                text(timetrackingNode, "originalEstimate"),
                text(timetrackingNode, "remainingEstimate"),
                text(timetrackingNode, "timeSpent"));
    }

    private List<String> textArray(JsonNode node, String fieldName) {
        List<String> values = new ArrayList<>();
        if (!node.isArray()) {
            return values;
        }
        node.forEach(item -> addIfPresent(values, fieldName == null ? compactValue(item) : text(item, fieldName)));
        return values;
    }

    private String userDisplayName(JsonNode userNode) {
        if (!userNode.isObject()) {
            return null;
        }
        return firstText(userNode, "displayName", "name", "emailAddress");
    }

    private String firstText(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            String value = text(node, fieldName);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String text(JsonNode node, String fieldName) {
        return compactValue(node.path(fieldName));
    }

    private String compactValue(JsonNode node) {
        if (isMissingOrNull(node)) {
            return null;
        }
        if (node.isTextual()) {
            return blankToNull(node.asText());
        }
        if (node.isNumber() || node.isBoolean()) {
            return node.asText();
        }
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Unable to render Jira field value", exception);
        }
    }

    private boolean isMissingOrNull(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull();
    }

    private void addIfPresent(List<String> values, String value) {
        if (value != null) {
            values.add(value);
        }
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
