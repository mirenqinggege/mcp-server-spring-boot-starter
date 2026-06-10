# Spring Security Support Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 `@Tool` 方法中可通过 `SecurityContextHolder` 获取客户端 Token 对应的 `Authentication`，并支持 `@PreAuthorize` 方法级授权。

**Architecture:** 新增 `McpAuthenticator` SPI 供用户实现 Token→Authentication 转换；`McpSecurityFilter`(OncePerRequestFilter) 拦截 `/mcp/*` 提取 Token 并构建 SecurityContext；`McpSecurityConfiguration` 条件装配仅在 Spring Security 可用且用户提供了 Authenticator 时激活。

**Tech Stack:** Spring Security 6.x (spring-security-core, spring-security-web), provided scope；Jackson (已有)；MCP SDK 1.1.2 (已有)

---

### Task 1: 添加 Spring Security 依赖

**Files:**
- Modify: `pom.xml`

- [ ] **Step 1: 在 pom.xml 添加 spring-security 依赖**

在 `<!-- Spring WebMVC -->` 依赖之后添加：

```xml
<!-- Spring Security (可选，用户引入后才激活) -->
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-core</artifactId>
    <scope>provided</scope>
</dependency>
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-web</artifactId>
    <scope>provided</scope>
</dependency>
```

- [ ] **Step 2: 验证依赖下载**

```bash
mvn dependency:resolve -q
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add pom.xml
git commit -m "build: add spring-security dependencies (provided scope)"
```

---

### Task 2: 扩展 McpServerProperties 添加安全配置

**Files:**
- Modify: `src/main/java/vip/jcfd/config/McpServerProperties.java`

- [ ] **Step 1: 添加 SecurityProperties 内部类及字段**

在类末尾 `}` 之前添加：

```java
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
```

- [ ] **Step 2: 编译验证**

```bash
mvn compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/vip/jcfd/config/McpServerProperties.java
git commit -m "feat: add mcp.server.security.* configuration properties"
```

---

### Task 3: 创建 McpAuthenticator SPI

**Files:**
- Create: `src/main/java/vip/jcfd/security/McpAuthenticator.java`

- [ ] **Step 1: 创建接口文件**

```java
package vip.jcfd.security;

import org.springframework.lang.Nullable;
import org.springframework.security.core.Authentication;

@FunctionalInterface
public interface McpAuthenticator {

    /**
     * 将客户端Token转换为Spring Security Authentication对象。
     * @param token SSE连接时的query param或POST的Authorization头
     * @return 认证信息，返回null表示认证失败（Filter将返回401）
     */
    @Nullable
    Authentication authenticate(String token);
}
```

- [ ] **Step 2: 编译验证**

```bash
mvn compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/vip/jcfd/security/McpAuthenticator.java
git commit -m "feat: add McpAuthenticator SPI interface"
```

---

### Task 4: 创建 McpSecurityFilter

**Files:**
- Create: `src/main/java/vip/jcfd/security/McpSecurityFilter.java`

- [ ] **Step 1: 创建 Filter 实现**

```java
package vip.jcfd.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
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
```

- [ ] **Step 2: 编译验证**

```bash
mvn compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/vip/jcfd/security/McpSecurityFilter.java
git commit -m "feat: add McpSecurityFilter for token-based MCP authentication"
```

---

### Task 5: 创建 McpSecurityConfiguration 自动配置

**Files:**
- Create: `src/main/java/vip/jcfd/config/McpSecurityConfiguration.java`

- [ ] **Step 1: 创建自动配置类**

```java
package vip.jcfd.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
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
```

- [ ] **Step 2: 编译验证**

```bash
mvn compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/vip/jcfd/config/McpSecurityConfiguration.java
git commit -m "feat: add McpSecurityConfiguration auto-configuration"
```

---

### Task 6: 最终验证

**Files:** (none)

- [ ] **Step 1: 完整编译**

```bash
mvn compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 2: 检查所有新增文件**

```bash
find src -name "*.java" | sort
```

Expected:
```
src/main/java/vip/jcfd/annotation/Tool.java
src/main/java/vip/jcfd/annotation/ToolParam.java
src/main/java/vip/jcfd/config/McpAutoConfiguration.java
src/main/java/vip/jcfd/config/McpSecurityConfiguration.java
src/main/java/vip/jcfd/config/McpServerProperties.java
src/main/java/vip/jcfd/scanner/McpToolScanner.java
src/main/java/vip/jcfd/scanner/ToolMethodInfo.java
src/main/java/vip/jcfd/security/McpAuthenticator.java
src/main/java/vip/jcfd/security/McpSecurityFilter.java
```

- [ ] **Step 3: 检查所有提交**

```bash
git log --oneline -6
```

Expected: 5 个新提交（Task 1-5）+ 之前的提交
