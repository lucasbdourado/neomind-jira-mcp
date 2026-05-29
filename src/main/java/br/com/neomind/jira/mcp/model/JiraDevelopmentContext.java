package br.com.neomind.jira.mcp.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record JiraDevelopmentContext(
        String key,
        String type,
        String project,
        String summary,
        String description,
        String status,
        String assignee,
        String reporter,
        List<String> labels,
        List<String> components,
        List<String> fixVersions,
        String sprint,
        String relatedPrd,
        List<AttachmentMetadata> attachments,
        List<CommentSummary> comments,
        TimeTrackingSummary timetracking) {

    public JiraDevelopmentContext {
        labels = copyOrEmpty(labels);
        components = copyOrEmpty(components);
        fixVersions = copyOrEmpty(fixVersions);
        attachments = copyOrEmpty(attachments);
        comments = copyOrEmpty(comments);
    }

    private static <T> List<T> copyOrEmpty(List<T> value) {
        return value == null ? List.of() : List.copyOf(value);
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record AttachmentMetadata(String name, String author, long size, String url) {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record CommentSummary(String author, String body, String createdDate) {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record TimeTrackingSummary(String originalEstimate, String remainingEstimate, String timeSpent) {
    }
}
