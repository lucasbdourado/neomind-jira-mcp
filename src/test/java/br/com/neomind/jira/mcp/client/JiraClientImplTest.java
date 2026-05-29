package br.com.neomind.jira.mcp.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpHeaders.COOKIE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import br.com.neomind.jira.mcp.config.JiraMcpProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.support.BasicAuthenticationInterceptor;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

class JiraClientImplTest {

    private static final String BASE_URL = "https://jira.local";
    private static final String USERNAME = "developer";
    private static final String PASSWORD = "secret-token";
    private static final String BASIC_AUTH_HEADER = "Basic "
            + Base64.getEncoder().encodeToString((USERNAME + ":" + PASSWORD).getBytes(StandardCharsets.UTF_8));

    private MockRestServiceServer server;
    private JiraClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(BASE_URL)
                .requestInterceptor(new BasicAuthenticationInterceptor(USERNAME, PASSWORD))
                .defaultStatusHandler(new JiraResponseErrorHandler());

        server = MockRestServiceServer.bindTo(builder).build();
        client = new JiraClientImpl(builder.build());
    }

    @Test
    void getServerInfoRoutesToLegacyEndpointAndUsesBasicAuth() {
        server.expect(requestTo(BASE_URL + "/rest/api/2/serverInfo"))
                .andExpect(method(GET))
                .andExpect(header(AUTHORIZATION, BASIC_AUTH_HEADER))
                .andRespond(withSuccess("{\"version\":\"7.0.9\"}", APPLICATION_JSON));

        String response = client.getServerInfo();

        assertThat(response).isEqualTo("{\"version\":\"7.0.9\"}");
        server.verify();
    }

    @Test
    void getIssueRoutesToIssueEndpoint() {
        server.expect(requestTo(BASE_URL + "/rest/api/2/issue/ABC-123"))
                .andExpect(method(GET))
                .andExpect(header(AUTHORIZATION, BASIC_AUTH_HEADER))
                .andRespond(withSuccess("{\"key\":\"ABC-123\"}", APPLICATION_JSON));

        String response = client.getIssue("ABC-123");

        assertThat(response).isEqualTo("{\"key\":\"ABC-123\"}");
        server.verify();
    }

    @Test
    void searchIssuesRoutesJqlAndMaxResultsAsQueryParameters() {
        server.expect(requestTo(containsString(BASE_URL + "/rest/api/2/search")))
                .andExpect(method(GET))
                .andExpect(header(AUTHORIZATION, BASIC_AUTH_HEADER))
                .andExpect(requestTo(containsString("jql=project%20%3D%20ABC%20ORDER%20BY%20created%20DESC")))
                .andExpect(queryParam("maxResults", "25"))
                .andRespond(withSuccess("{\"issues\":[]}", APPLICATION_JSON));

        String response = client.searchIssues("project = ABC ORDER BY created DESC", 25);

        assertThat(response).isEqualTo("{\"issues\":[]}");
        server.verify();
    }

    @Test
    void getIssueCommentsRoutesToCommentsEndpoint() {
        server.expect(requestTo(BASE_URL + "/rest/api/2/issue/ABC-123/comment"))
                .andExpect(method(GET))
                .andExpect(header(AUTHORIZATION, BASIC_AUTH_HEADER))
                .andRespond(withSuccess("{\"comments\":[]}", APPLICATION_JSON));

        String response = client.getIssueComments("ABC-123");

        assertThat(response).isEqualTo("{\"comments\":[]}");
        server.verify();
    }

    @Test
    void httpUnauthorizedErrorThrowsSanitizedException() {
        String leakingBody = "Authorization: " + BASIC_AUTH_HEADER + " password=" + PASSWORD;
        server.expect(requestTo(BASE_URL + "/rest/api/2/serverInfo"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(APPLICATION_JSON)
                        .body(leakingBody));

        assertThatThrownBy(() -> client.getServerInfo())
                .isInstanceOf(JiraApiException.class)
                .hasMessage("Jira API request failed: GET /rest/api/2/serverInfo returned HTTP 401")
                .hasMessageNotContaining(PASSWORD)
                .hasMessageNotContaining(BASIC_AUTH_HEADER)
                .hasMessageNotContaining(leakingBody);

        server.verify();
    }

    @Test
    void unauthorizedResponseLogsInThroughJspAndRetriesWithJsessionCookie() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(BASE_URL)
                .defaultStatusHandler(new JiraResponseErrorHandler());
        MockRestServiceServer loginServer = MockRestServiceServer.bindTo(builder).build();
        JiraMcpProperties properties = new JiraMcpProperties(
                BASE_URL,
                USERNAME,
                PASSWORD,
                "",
                "customfield_10007",
                "customfield_10005");
        JiraSessionAuthenticator authenticator = new JiraSessionAuthenticator(builder.build(), properties);
        RestClient retryingRestClient = builder
                .requestInterceptor(new JiraSessionCookieInterceptor(authenticator))
                .requestInterceptor(new BasicAuthenticationInterceptor(USERNAME, PASSWORD))
                .build();
        JiraClient retryingClient = new JiraClientImpl(retryingRestClient, authenticator);

        loginServer.expect(requestTo(BASE_URL + "/rest/api/2/serverInfo"))
                .andExpect(method(GET))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        loginServer.expect(requestTo(BASE_URL + "/login.jsp"))
                .andExpect(method(POST))
                .andExpect(content().formData(formData()))
                .andRespond(withSuccess()
                        .header(HttpHeaders.SET_COOKIE, "JSESSIONID=session-id; Path=/; HttpOnly"));
        loginServer.expect(requestTo(BASE_URL + "/rest/api/2/serverInfo"))
                .andExpect(method(GET))
                .andExpect(header(COOKIE, "JSESSIONID=session-id"))
                .andRespond(withSuccess("{\"version\":\"7.0.9\"}", APPLICATION_JSON));

        String response = retryingClient.getServerInfo();

        assertThat(response).isEqualTo("{\"version\":\"7.0.9\"}");
        loginServer.verify();
    }

    @Test
    void httpForbiddenErrorThrowsSanitizedException() {
        server.expect(requestTo(BASE_URL + "/rest/api/2/issue/ABC-123"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .contentType(APPLICATION_JSON)
                        .body("forbidden for user " + USERNAME + " with token " + PASSWORD));

        assertThatThrownBy(() -> client.getIssue("ABC-123"))
                .isInstanceOf(JiraApiException.class)
                .hasMessage("Jira API request failed: GET /rest/api/2/issue/ABC-123 returned HTTP 403")
                .hasMessageNotContaining(USERNAME)
                .hasMessageNotContaining(PASSWORD);

        server.verify();
    }

    @Test
    void clientFailureExceptionDoesNotRetainLeakingCause() {
        JiraApiException exception = JiraApiException.forClientFailure(
                new RestClientException("connection failed with password " + PASSWORD));

        assertThat(exception)
                .hasMessage("Jira API request failed before receiving a response: RestClientException")
                .hasMessageNotContaining(PASSWORD)
                .hasNoCause();
    }

    private static org.springframework.util.MultiValueMap<String, String> formData() {
        org.springframework.util.LinkedMultiValueMap<String, String> form = new org.springframework.util.LinkedMultiValueMap<>();
        form.add("os_username", USERNAME);
        form.add("os_password", PASSWORD);
        form.add("os_cookie", "true");
        return form;
    }
}
