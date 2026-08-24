package br.com.neomind.jira.mcp;

import br.com.neomind.jira.mcp.tools.JiraTools;
import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

@SpringBootApplication
public class JiraMcpApplication {

    public static void main(String[] args) {
        new SpringApplicationBuilder(JiraMcpApplication.class)
                .bannerMode(Banner.Mode.OFF)
                .web(WebApplicationType.NONE)
                .logStartupInfo(false)
                .run(args);
    }

    @Bean
    ToolCallbackProvider jiraToolCallbackProvider(JiraTools jiraTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(jiraTools)
                .build();
    }
}
