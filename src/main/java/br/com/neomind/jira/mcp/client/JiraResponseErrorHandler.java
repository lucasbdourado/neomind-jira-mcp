package br.com.neomind.jira.mcp.client;

import java.io.IOException;
import java.net.URI;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResponseErrorHandler;

public class JiraResponseErrorHandler implements ResponseErrorHandler {

    @Override
    public boolean hasError(ClientHttpResponse response) throws IOException {
        return response.getStatusCode().isError();
    }

    @Override
    @SuppressWarnings("removal")
    public void handleError(ClientHttpResponse response) throws IOException {
        throw JiraApiException.forStatus(response.getStatusCode(), HttpMethod.GET, "unknown");
    }

    @Override
    public void handleError(URI url, HttpMethod method, ClientHttpResponse response) throws IOException {
        HttpStatusCode statusCode = response.getStatusCode();
        throw JiraApiException.forStatus(statusCode, method, url.getPath());
    }
}
