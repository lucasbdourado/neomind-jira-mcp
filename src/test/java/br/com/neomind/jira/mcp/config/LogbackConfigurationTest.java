package br.com.neomind.jira.mcp.config;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.io.ClassPathResource;

@ExtendWith(OutputCaptureExtension.class)
class LogbackConfigurationTest {

    @Test
    void writesApplicationLogsToStandardErrorOnly(CapturedOutput output) throws Exception {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.reset();

        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(context);
        configurator.doConfigure(new ClassPathResource("logback-spring.xml").getInputStream());

        LoggerFactory.getLogger(LogbackConfigurationTest.class).info("stderr-only-log-message");

        assertThat(output.getOut()).doesNotContain("stderr-only-log-message");
        assertThat(output.getErr()).contains("stderr-only-log-message");
    }
}
