package br.com.neomind.jira.mcp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jira")
public record JiraMcpProperties(
        String baseUrl,
        String username,
        String password,
        String cookie,
        String sprintFieldId,
        String relatedPrdFieldId) {

    private static final String DEFAULT_SPRINT_FIELD_ID = "customfield_10007";
    private static final String DEFAULT_RELATED_PRD_FIELD_ID = "customfield_10005";

    public JiraMcpProperties {
        sprintFieldId = defaultIfBlank(sprintFieldId, DEFAULT_SPRINT_FIELD_ID);
        relatedPrdFieldId = defaultIfBlank(relatedPrdFieldId, DEFAULT_RELATED_PRD_FIELD_ID);
    }

    private static String defaultIfBlank(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
