package vip.jcfd.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import vip.jcfd.config.McpServerProperties;

import java.io.IOException;

public class McpSecurityFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final McpAuthenticator authenticator;
    private final McpServerProperties properties;

    public McpSecurityFilter(McpAuthenticator authenticator, McpServerProperties properties) {
        this.authenticator = authenticator;
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String token = extractToken(request);
        Authentication auth = authenticator.authenticate(token);

        if (auth == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "MCP authentication failed");
            return;
        }

        try {
            SecurityContextHolder.getContext().setAuthentication(auth);
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private String extractToken(HttpServletRequest request) {
        McpServerProperties.Security sec = properties.getSecurity();

        // SSE 连接 (GET): 从 query parameter 获取
        if ("GET".equalsIgnoreCase(request.getMethod())) {
            return request.getParameter(sec.getTokenParameterName());
        }

        // 消息端点 (POST): 从 Authorization: Bearer xxx 获取
        String header = request.getHeader(sec.getTokenHeaderName());
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return header;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.getSecurity().isEnabled();
    }
}
