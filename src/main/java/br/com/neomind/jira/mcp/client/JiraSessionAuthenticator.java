package br.com.neomind.jira.mcp.client;

import static org.springframework.util.StringUtils.hasText;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import br.com.neomind.jira.mcp.config.JiraMcpProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

public class JiraSessionAuthenticator {

    private static final String LOGIN_PATH = "/login.jsp";
    private static final String JSESSIONID_COOKIE_PREFIX = "JSESSIONID=";

    private final RestClient loginRestClient;
    private final JiraMcpProperties properties;
    private final AtomicReference<String> cookieHeader;

    public JiraSessionAuthenticator(RestClient loginRestClient, JiraMcpProperties properties) {
        this.loginRestClient = loginRestClient;
        this.properties = properties;
        this.cookieHeader = new AtomicReference<>(blankToNull(properties.cookie()));
    }

    public String currentCookieHeader() {
        return cookieHeader.get();
    }

    public synchronized String login() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("os_username", properties.username());
        form.add("os_password", properties.password());
        form.add("os_cookie", "true");

        try {
            HttpHeaders responseHeaders = loginRestClient.post()
                    .uri(LOGIN_PATH)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .exchange((request, response) -> response.getHeaders());

            String updatedCookieHeader = toCookieHeader(responseHeaders.get(HttpHeaders.SET_COOKIE));
            if (!hasText(updatedCookieHeader) || !updatedCookieHeader.contains(JSESSIONID_COOKIE_PREFIX)) {
                throw JiraApiException.forLoginFailure();
            }

            cookieHeader.set(updatedCookieHeader);
            return updatedCookieHeader;
        } catch (JiraApiException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw JiraApiException.forLoginFailure();
        }
    }

    private static String toCookieHeader(List<String> setCookieHeaders) {
        if (setCookieHeaders == null || setCookieHeaders.isEmpty()) {
            return null;
        }

        return setCookieHeaders.stream()
                .map(setCookie -> setCookie.split(";", 2)[0])
                .filter(org.springframework.util.StringUtils::hasText)
                .reduce((left, right) -> left + "; " + right)
                .orElse(null);
    }

    private static String blankToNull(String value) {
        return hasText(value) ? value : null;
    }
}
