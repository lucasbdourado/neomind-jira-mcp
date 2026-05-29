package br.com.neomind.jira.mcp.client;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;

public class JiraApiException extends RuntimeException {

    private final HttpStatusCode statusCode;

    public JiraApiException(String message) {
        super(message);
        this.statusCode = null;
    }

    private JiraApiException(String message, HttpStatusCode statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public static JiraApiException forStatus(HttpStatusCode statusCode, HttpMethod method, String path) {
        return new JiraApiException(
                "Jira API request failed: " + method.name() + " " + path + " returned HTTP "
                        + statusCode.value(),
                statusCode);
    }

    public static JiraApiException forClientFailure(Throwable cause) {
        return new JiraApiException(
                "Jira API request failed before receiving a response: " + cause.getClass().getSimpleName());
    }

    public static JiraApiException forLoginFailure() {
        return new JiraApiException("Jira login failed: unable to obtain JSESSIONID cookie from /login.jsp");
    }

    public HttpStatusCode statusCode() {
        return statusCode;
    }
}
