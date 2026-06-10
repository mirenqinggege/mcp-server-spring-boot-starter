package vip.jcfd.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfiguration;
import org.springframework.security.core.Authentication;
import org.springframework.web.filter.OncePerRequestFilter;
import vip.jcfd.security.McpAuthenticator;
import vip.jcfd.security.McpSecurityFilter;

@AutoConfiguration
@AutoConfigureBefore(McpAutoConfiguration.class)
@ConditionalOnClass({Authentication.class, OncePerRequestFilter.class, WebSecurityConfiguration.class})
@ConditionalOnBean(McpAuthenticator.class)
@ConditionalOnProperty(prefix = "mcp.server.security", name = "enabled", havingValue = "true", matchIfMissing = true)
public class McpSecurityConfiguration {

    @Bean
    public McpSecurityFilter mcpSecurityFilter(
            McpAuthenticator authenticator, McpServerProperties properties) {
        return new McpSecurityFilter(authenticator, properties);
    }

    @Bean
    public FilterRegistrationBean<McpSecurityFilter> mcpSecurityFilterRegistration(
            McpSecurityFilter filter) {
        FilterRegistrationBean<McpSecurityFilter> registration = new FilterRegistrationBean<>(filter);
        registration.addUrlPatterns("/mcp/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 100);
        return registration;
    }
}
