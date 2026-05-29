package br.com.neomind.jira.mcp.config;

import br.com.neomind.jira.mcp.client.JiraClient;
import br.com.neomind.jira.mcp.client.JiraClientImpl;
import br.com.neomind.jira.mcp.client.JiraResponseErrorHandler;
import br.com.neomind.jira.mcp.client.JiraSessionAuthenticator;
import br.com.neomind.jira.mcp.client.JiraSessionCookieInterceptor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.support.BasicAuthenticationInterceptor;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JiraMcpProperties.class)
public class JiraMcpConfig {

    @Bean
    JiraResponseErrorHandler jiraResponseErrorHandler() {
        return new JiraResponseErrorHandler();
    }

    @Bean
    RestClient jiraRestClient(
            RestClient.Builder restClientBuilder,
            JiraMcpProperties properties,
            JiraResponseErrorHandler errorHandler,
            JiraSessionAuthenticator sessionAuthenticator) {
        RestClient.Builder builder = restClientBuilder
                .baseUrl(properties.baseUrl())
                .defaultStatusHandler(errorHandler);

        builder.requestInterceptor(new JiraSessionCookieInterceptor(sessionAuthenticator));

        if (hasText(properties.cookie())) {
        } else if (hasText(properties.username()) && hasText(properties.password())) {
            builder.requestInterceptor(new BasicAuthenticationInterceptor(properties.username(), properties.password()));
        }

        return builder.build();
    }

    @Bean
    JiraSessionAuthenticator jiraSessionAuthenticator(RestClient.Builder restClientBuilder, JiraMcpProperties properties) {
        RestClient loginRestClient = restClientBuilder.clone()
                .baseUrl(properties.baseUrl())
                .build();
        return new JiraSessionAuthenticator(loginRestClient, properties);
    }

    @Bean
    JiraClient jiraClient(RestClient jiraRestClient, JiraSessionAuthenticator sessionAuthenticator) {
        return new JiraClientImpl(jiraRestClient, sessionAuthenticator);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
