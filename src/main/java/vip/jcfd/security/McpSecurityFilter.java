package vip.jcfd.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import vip.jcfd.config.McpServerProperties;

import java.io.IOException;

public class McpSecurityFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(McpSecurityFilter.class);
    private static final String BEARER_PREFIX = "bearer ";

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
        if (token == null || token.isEmpty()) {
            log.debug("MCP authentication failed: no token provided for {} {}", request.getMethod(), request.getRequestURI());
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "MCP authentication failed: no token");
            return;
        }

        Authentication auth = authenticator.authenticate(token);

        if (auth == null) {
            log.debug("MCP authentication failed: invalid token for {} {}", request.getMethod(), request.getRequestURI());
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
        if (header != null && header.toLowerCase().startsWith(BEARER_PREFIX)) {
            return header.substring(header.indexOf(' ') + 1);
        }
        return header;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.getSecurity().isEnabled()
                || "OPTIONS".equalsIgnoreCase(request.getMethod());
    }
}
