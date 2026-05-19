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
}
