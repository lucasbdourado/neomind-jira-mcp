package br.com.neomind.jira.mcp.client;

import java.io.IOException;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

public class JiraSessionCookieInterceptor implements ClientHttpRequestInterceptor {

    private final JiraSessionAuthenticator authenticator;

    public JiraSessionCookieInterceptor(JiraSessionAuthenticator authenticator) {
        this.authenticator = authenticator;
    }

    @Override
    public ClientHttpResponse intercept(
            HttpRequest request,
            byte[] body,
            ClientHttpRequestExecution execution) throws IOException {
        String cookieHeader = authenticator.currentCookieHeader();
        if (cookieHeader != null && !request.getHeaders().containsKey(HttpHeaders.COOKIE)) {
            request.getHeaders().set(HttpHeaders.COOKIE, cookieHeader);
        }

        return execution.execute(request, body);
    }
}
