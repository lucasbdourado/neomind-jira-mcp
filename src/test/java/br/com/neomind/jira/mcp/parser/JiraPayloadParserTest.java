package br.com.neomind.jira.mcp.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.neomind.jira.mcp.config.JiraMcpProperties;
import br.com.neomind.jira.mcp.model.JiraDevelopmentContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class JiraPayloadParserTest {

    private final JiraPayloadParser parser = new JiraPayloadParser(new ObjectMapper());

    @Test
    void parsesStandardIssueFieldsAndMetadataOnlyAttachments() {
        JiraDevelopmentContext context = parser.parseIssue("""
                {
                  "key": "ABC-123",
                  "fields": {
                    "issuetype": {"name": "Story"},
                    "project": {"key": "ABC", "name": "Example Project"},
                    "summary": "Implement parser",
                    "description": "Detailed ticket body",
                    "status": {"name": "In Progress"},
                    "assignee": {"displayName": "Ada Lovelace", "name": "ada"},
                    "reporter": {"name": "grace"},
                    "labels": ["backend", "mcp"],
                    "components": [{"name": "Integrations"}],
                    "fixVersions": [{"name": "1.2.0"}],
                    "customfield_10007": "com.atlassian.greenhopper.service.sprint.Sprint@1ab[id=1,name=Sprint 5,state=ACTIVE]",
                    "customfield_10005": "https://docs.local/prd/ABC-123",
                    "attachment": [
                      {
                        "filename": "spec.pdf",
                        "size": 12345,
                        "content": "https://jira.local/secure/attachment/1/spec.pdf",
                        "author": {"displayName": "Spec Author"}
                      }
                    ],
                    "comment": {
                      "comments": [
                        {
                          "author": {"displayName": "Reviewer"},
                          "body": "Looks ready",
                          "created": "2026-05-28T01:02:03.000-0300"
                        }
                      ]
                    },
                    "timetracking": {
                      "originalEstimate": "2d",
                      "remainingEstimate": "1d",
                      "timeSpent": "3h"
                    }
                  }
                }
                """, "customfield_10007");

        assertThat(context.key()).isEqualTo("ABC-123");
        assertThat(context.type()).isEqualTo("Story");
        assertThat(context.project()).isEqualTo("ABC");
        assertThat(context.summary()).isEqualTo("Implement parser");
        assertThat(context.description()).isEqualTo("Detailed ticket body");
        assertThat(context.status()).isEqualTo("In Progress");
        assertThat(context.assignee()).isEqualTo("Ada Lovelace");
        assertThat(context.reporter()).isEqualTo("grace");
        assertThat(context.labels()).containsExactly("backend", "mcp");
        assertThat(context.components()).containsExactly("Integrations");
        assertThat(context.fixVersions()).containsExactly("1.2.0");
        assertThat(context.sprint()).isEqualTo("Sprint 5");
        assertThat(context.relatedPrd()).isEqualTo("https://docs.local/prd/ABC-123");
        assertThat(context.attachments())
                .singleElement()
                .satisfies(attachment -> {
                    assertThat(attachment.name()).isEqualTo("spec.pdf");
                    assertThat(attachment.size()).isEqualTo(12345);
                    assertThat(attachment.author()).isEqualTo("Spec Author");
                    assertThat(attachment.url()).isEqualTo("https://jira.local/secure/attachment/1/spec.pdf");
                });
        assertThat(context.comments())
                .singleElement()
                .satisfies(comment -> {
                    assertThat(comment.author()).isEqualTo("Reviewer");
                    assertThat(comment.body()).isEqualTo("Looks ready");
                    assertThat(comment.createdDate()).isEqualTo("2026-05-28T01:02:03.000-0300");
                });
        assertThat(context.timetracking().originalEstimate()).isEqualTo("2d");
        assertThat(context.timetracking().remainingEstimate()).isEqualTo("1d");
        assertThat(context.timetracking().timeSpent()).isEqualTo("3h");
    }

    @Test
    void parsesSprintFromJsonObjectArray() {
        JiraDevelopmentContext context = parser.parseIssue("""
                {
                  "key": "ABC-124",
                  "fields": {
                    "customfield_20000": [
                      {"id": 4, "name": "Sprint 4", "state": "CLOSED"},
                      {"id": 5, "name": "Sprint 5", "state": "ACTIVE"}
                    ]
                  }
                }
                """, "customfield_20000");

        assertThat(context.sprint()).isEqualTo("Sprint 5");
    }

    @Test
    void extractsRelatedPrdFromConfiguredField() {
        JiraMcpProperties properties = new JiraMcpProperties(
                "https://jira.local",
                "developer",
                "secret",
                "",
                "customfield_10007",
                "customfield_54321");

        JiraDevelopmentContext context = parser.parseIssue("""
                {
                  "key": "ABC-125",
                  "fields": {
                    "customfield_54321": "PRD-778"
                  }
                }
                """, properties);

        assertThat(context.relatedPrd()).isEqualTo("PRD-778");
    }

    @Test
    void missingNullableFieldsDoNotFailParsing() {
        JiraDevelopmentContext context = parser.parseIssue("""
                {
                  "key": "ABC-126",
                  "fields": {
                    "summary": "Partial issue",
                    "assignee": null,
                    "reporter": null,
                    "labels": null,
                    "components": null,
                    "fixVersions": null,
                    "timetracking": null,
                    "attachment": null,
                    "comment": null
                  }
                }
                """, "customfield_10007");

        assertThat(context.key()).isEqualTo("ABC-126");
        assertThat(context.summary()).isEqualTo("Partial issue");
        assertThat(context.assignee()).isNull();
        assertThat(context.reporter()).isNull();
        assertThat(context.labels()).isEmpty();
        assertThat(context.components()).isEmpty();
        assertThat(context.fixVersions()).isEmpty();
        assertThat(context.attachments()).isEmpty();
        assertThat(context.comments()).isEmpty();
        assertThat(context.timetracking()).isNull();
        assertThat(context.sprint()).isNull();
        assertThat(context.relatedPrd()).isNull();
    }

    @Test
    void serializedContextOmitsNullAndEmptyFields() throws Exception {
        JiraDevelopmentContext context = parser.parseIssue("""
                {
                  "key": "ABC-126",
                  "fields": {
                    "summary": "Partial issue",
                    "labels": []
                  }
                }
                """, "customfield_10007");

        String json = new ObjectMapper().writeValueAsString(context);

        assertThat(json).isEqualTo("{\"key\":\"ABC-126\",\"summary\":\"Partial issue\"}");
    }

    @Test
    void customFieldsWithObjectValuesAreReturnedAsCompactJson() {
        JiraDevelopmentContext context = parser.parseIssue("""
                {
                  "key": "ABC-127",
                  "fields": {
                    "customfield_10005": {"url": "https://docs.local/prd/ABC-127", "id": 127}
                  }
                }
                """, "customfield_10007");

        assertThat(context.relatedPrd()).isEqualTo("{\"url\":\"https://docs.local/prd/ABC-127\",\"id\":127}");
    }

    @Test
    void invalidJsonThrowsClearParserException() {
        assertThatThrownBy(() -> parser.parseIssue("{not-json", "customfield_10007"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unable to parse Jira issue JSON");
    }
}
