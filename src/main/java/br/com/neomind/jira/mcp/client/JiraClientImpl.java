package br.com.neomind.jira.mcp.client;

import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

public class JiraClientImpl implements JiraClient {

    private static final String SERVER_INFO_PATH = "/rest/api/2/serverInfo";
    private static final String ISSUE_PATH = "/rest/api/2/issue/{issueKey}";
    private static final String SEARCH_PATH = "/rest/api/2/search";
    private static final String ISSUE_COMMENTS_PATH = "/rest/api/2/issue/{issueKey}/comment";

    private final RestClient restClient;
    private final JiraSessionAuthenticator sessionAuthenticator;

    public JiraClientImpl(RestClient restClient) {
        this(restClient, null);
    }

    public JiraClientImpl(RestClient restClient, JiraSessionAuthenticator sessionAuthenticator) {
        this.restClient = restClient;
        this.sessionAuthenticator = sessionAuthenticator;
    }

    @Override
    public String getServerInfo() {
        return execute(() -> restClient.get()
                .uri(SERVER_INFO_PATH)
                .retrieve()
                .body(String.class));
    }

    @Override
    public String getIssue(String issueKey) {
        return execute(() -> restClient.get()
                .uri(ISSUE_PATH, issueKey)
                .retrieve()
                .body(String.class));
    }

    @Override
    public String searchIssues(String jql, Integer maxResults) {
        return execute(() -> restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(SEARCH_PATH)
                        .queryParam("jql", jql)
                        .queryParamIfPresent("maxResults", Optional.ofNullable(maxResults))
                        .build())
                .retrieve()
                .body(String.class));
    }

    @Override
    public String getIssueComments(String issueKey) {
        return execute(() -> restClient.get()
                .uri(ISSUE_COMMENTS_PATH, issueKey)
                .retrieve()
                .body(String.class));
    }

    private String execute(JiraRequest request) {
        try {
            return request.exchange();
        } catch (JiraApiException exception) {
            if (sessionAuthenticator != null
                    && exception.statusCode() != null
                    && exception.statusCode().isSameCodeAs(HttpStatus.UNAUTHORIZED)) {
                sessionAuthenticator.login();
                return retryAfterLogin(request);
            }
            throw exception;
        } catch (RestClientException exception) {
            throw JiraApiException.forClientFailure(exception);
        }
    }

    private String retryAfterLogin(JiraRequest request) {
        try {
            return request.exchange();
        } catch (JiraApiException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw JiraApiException.forClientFailure(exception);
        }
    }

    @FunctionalInterface
    private interface JiraRequest {
        String exchange();
    }
}
