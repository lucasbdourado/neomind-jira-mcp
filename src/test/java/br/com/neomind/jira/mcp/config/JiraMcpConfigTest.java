package br.com.neomind.jira.mcp.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.COOKIE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import br.com.neomind.jira.mcp.client.JiraClient;
import br.com.neomind.jira.mcp.client.JiraClientImpl;
import br.com.neomind.jira.mcp.client.JiraResponseErrorHandler;
import br.com.neomind.jira.mcp.client.JiraSessionAuthenticator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class JiraMcpConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RestClientAutoConfiguration.class))
            .withUserConfiguration(JiraMcpConfig.class)
            .withPropertyValues(
                    "jira.base-url=https://jira.local",
                    "jira.username=developer",
                    "jira.password=secret-token");

    @Test
    void createsJiraClientBeans() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(JiraResponseErrorHandler.class);
            assertThat(context).hasSingleBean(RestClient.class);
            assertThat(context).hasSingleBean(JiraClient.class);
            assertThat(context.getBean(JiraClient.class)).isInstanceOf(JiraClientImpl.class);
        });
    }

    @Test
    void usesCookieHeaderWhenCookieIsConfigured() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        JiraMcpProperties properties = new JiraMcpProperties(
                "https://jira.local",
                "developer",
                "secret",
                "JSESSIONID=session-id; atlassian.xsrf.token=token",
                "customfield_10007",
                "customfield_10005");

        RestClient restClient = new JiraMcpConfig().jiraRestClient(
                builder,
                properties,
                new JiraResponseErrorHandler(),
                new JiraSessionAuthenticator(builder.build(), properties));

        server.expect(requestTo("https://jira.local/rest/api/2/serverInfo"))
                .andExpect(method(GET))
                .andExpect(header(COOKIE, "JSESSIONID=session-id; atlassian.xsrf.token=token"))
                .andRespond(withSuccess("{\"version\":\"7.0.9\"}", APPLICATION_JSON));

        String response = restClient.get()
                .uri("/rest/api/2/serverInfo")
                .retrieve()
                .body(String.class);

        assertThat(response).isEqualTo("{\"version\":\"7.0.9\"}");
        server.verify();
    }
}
