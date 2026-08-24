package br.com.neomind.jira.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.neomind.jira.mcp.client.JiraClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.mockito.BDDMockito.given;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "jira.base-url=https://jira.local",
        "jira.username=developer",
        "jira.password=secret-token",
        "spring.ai.mcp.server.protocol=STREAMABLE",
        "spring.ai.mcp.server.stdio=false"
})
class JiraMcpHttpIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @MockBean
    private JiraClient jiraClient;

    @Test
    void respondsToMcpInitializeToolsListAndToolExecutionOverHttp() {
        given(jiraClient.getServerInfo()).willReturn("{\"version\":\"9.4.0\",\"serverTitle\":\"Test Jira\"}");

        String url = "http://localhost:" + port + "/mcp";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.valueOf("text/event-stream")));

        String initializePayload = """
                {
                    "jsonrpc": "2.0",
                    "id": "init-1",
                    "method": "initialize",
                    "params": {
                        "protocolVersion": "2024-11-05",
                        "capabilities": {},
                        "clientInfo": {
                            "name": "test-client",
                            "version": "1.0.0"
                        }
                    }
                }
                """;

        HttpEntity<String> initRequest = new HttpEntity<>(initializePayload, headers);
        ResponseEntity<String> initResponse = restTemplate.exchange(url, HttpMethod.POST, initRequest, String.class);

        assertThat(initResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(initResponse.getBody()).isNotNull();
        assertThat(initResponse.getBody()).contains("jira-mcp-server");
        assertThat(initResponse.getBody()).contains("protocolVersion");

        HttpHeaders sessionHeaders = new HttpHeaders();
        sessionHeaders.setContentType(MediaType.APPLICATION_JSON);
        sessionHeaders.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.valueOf("text/event-stream")));
        if (initResponse.getHeaders().containsKey("Mcp-Session-Id")) {
            sessionHeaders.set("Mcp-Session-Id", initResponse.getHeaders().getFirst("Mcp-Session-Id"));
        }

        String listToolsPayload = """
                {
                    "jsonrpc": "2.0",
                    "id": "tools-1",
                    "method": "tools/list",
                    "params": {}
                }
                """;

        HttpEntity<String> listRequest = new HttpEntity<>(listToolsPayload, sessionHeaders);
        ResponseEntity<String> listResponse = restTemplate.exchange(url, HttpMethod.POST, listRequest, String.class);

        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResponse.getBody()).isNotNull();
        assertThat(listResponse.getBody()).contains("jira_get_server_info");
        assertThat(listResponse.getBody()).contains("jira_get_issue");
        assertThat(listResponse.getBody()).contains("jira_search_issues");
        assertThat(listResponse.getBody()).contains("jira_get_issue_comments");
        assertThat(listResponse.getBody()).contains("jira_get_development_context");

        String callToolPayload = """
                {
                    "jsonrpc": "2.0",
                    "id": "call-1",
                    "method": "tools/call",
                    "params": {
                        "name": "jira_get_server_info",
                        "arguments": {}
                    }
                }
                """;

        HttpEntity<String> callRequest = new HttpEntity<>(callToolPayload, sessionHeaders);
        ResponseEntity<String> callResponse = restTemplate.exchange(url, HttpMethod.POST, callRequest, String.class);

        assertThat(callResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(callResponse.getBody()).isNotNull();
        assertThat(callResponse.getBody()).contains("Test Jira");
        assertThat(callResponse.getBody()).contains("9.4.0");
    }
}
