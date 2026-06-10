package vip.jcfd.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mcp.server")
public class McpServerProperties {

    private String sseEndpoint = "/mcp/sse";

    private String messageEndpoint = "/mcp/message";

    private String serverName = "mcp-server";

    private String serverVersion = "1.0.0";

    public String getSseEndpoint() { return sseEndpoint; }
    public void setSseEndpoint(String sseEndpoint) { this.sseEndpoint = sseEndpoint; }
    public String getMessageEndpoint() { return messageEndpoint; }
    public void setMessageEndpoint(String messageEndpoint) { this.messageEndpoint = messageEndpoint; }
    public String getServerName() { return serverName; }
    public void setServerName(String serverName) { this.serverName = serverName; }
    public String getServerVersion() { return serverVersion; }
    public void setServerVersion(String serverVersion) { this.serverVersion = serverVersion; }

    private final Security security = new Security();

    public Security getSecurity() { return security; }

    public static class Security {
        private boolean enabled = true;
        private String tokenParameterName = "token";
        private String tokenHeaderName = "Authorization";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getTokenParameterName() { return tokenParameterName; }
        public void setTokenParameterName(String tokenParameterName) { this.tokenParameterName = tokenParameterName; }
        public String getTokenHeaderName() { return tokenHeaderName; }
        public void setTokenHeaderName(String tokenHeaderName) { this.tokenHeaderName = tokenHeaderName; }
    }
}
