package br.com.neomind.jira.mcp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "jira.base-url=https://jira.local",
        "jira.username=developer",
        "jira.password=secret-token",
        "spring.ai.mcp.server.enabled=false",
        "spring.ai.mcp.server.stdio=false"
})
class JiraMcpApplicationTest {

    @Test
    void contextLoads() {
    }
}
