package br.com.neomind.jira.mcp.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class JiraMcpPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class)
            .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class));

    @Test
    void bindsJiraProperties() {
        contextRunner
                .withPropertyValues(
                        "jira.base-url=https://jira.local",
                        "jira.username=developer",
                        "jira.password=secret",
                        "jira.cookie=JSESSIONID=session-id",
                        "jira.sprint-field-id=customfield_12345",
                        "jira.related-prd-field-id=customfield_54321")
                .run(context -> {
                    JiraMcpProperties properties = context.getBean(JiraMcpProperties.class);

                    assertThat(properties.baseUrl()).isEqualTo("https://jira.local");
                    assertThat(properties.username()).isEqualTo("developer");
                    assertThat(properties.password()).isEqualTo("secret");
                    assertThat(properties.cookie()).isEqualTo("JSESSIONID=session-id");
                    assertThat(properties.sprintFieldId()).isEqualTo("customfield_12345");
                    assertThat(properties.relatedPrdFieldId()).isEqualTo("customfield_54321");
                });
    }

    @Test
    void usesCustomFieldDefaultsWhenOptionalPropertiesAreMissing() {
        contextRunner
                .withPropertyValues(
                        "jira.base-url=https://jira.local",
                        "jira.username=developer",
                        "jira.password=secret")
                .run(context -> {
                    JiraMcpProperties properties = context.getBean(JiraMcpProperties.class);

                    assertThat(properties.sprintFieldId()).isEqualTo("customfield_10007");
                    assertThat(properties.relatedPrdFieldId()).isEqualTo("customfield_10005");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(JiraMcpProperties.class)
    static class TestConfiguration {
    }
}
