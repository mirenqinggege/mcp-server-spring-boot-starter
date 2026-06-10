# Spring Security Support 设计

日期: 2026-06-10

## 目标

让使用了 Spring Security 的 Spring Boot 项目，在 `@Tool` 方法中能够：
1. 通过 `SecurityContextHolder.getContext().getAuthentication()` 获取当前用户
2. 使用 `@PreAuthorize`、`@Secured` 等方法安全注解控制工具访问权限

## 数据流

```
客户端连接 /mcp/sse?token=xxx
        │
        ▼
  McpSecurityFilter ─── 提取 token ──→ McpAuthenticator.authenticate(token)
        │                                        │
        │                                        ▼
        │                              Authentication (包含用户信息/权限)
        │                                        │
        ▼                                        ▼
  SecurityContextHolder.setContext(...)
        │
        ▼
  POST /mcp/message (Header: Authorization: Bearer xxx)
        │
        ▼
  McpSecurityFilter (同上流程)
        │
        ▼
  McpSyncServer callHandler ──→ toolMethod.invoke()
        │                              │
        ▼                              ▼
  SecurityContext 立即可用      @PreAuthorize 生效
```

## 新增组件

### 1. McpAuthenticator SPI

```java
package vip.jcfd.security;

@FunctionalInterface
public interface McpAuthenticator {
    @Nullable
    Authentication authenticate(String token);
}
```

- 用户实现并注册为 Spring Bean
- 返回 `null` 表示认证失败，Filter 返回 401
- 返回的 `Authentication` 应包含 `GrantedAuthority` 集合以支持 `@PreAuthorize("hasRole(...)")`

### 2. McpSecurityFilter

继承 `OncePerRequestFilter`，拦截 `/mcp/**`：

1. **SSE 端点 (GET)**：从 `request.getParameter("token")` 获取 token
2. **消息端点 (POST)**：从 `Authorization: Bearer xxx` 请求头获取 token
3. 调用 `McpAuthenticator.authenticate(token)`
4. 成功 → 构建 `UsernamePasswordAuthenticationToken` → `SecurityContextHolder.getContext().setAuthentication()` → `filterChain.doFilter()` 继续
5. 失败 → `response.sendError(401, "Unauthorized")` → 不进入后续链路
6. `finally` 中 `SecurityContextHolder.clearContext()` 清理

### 3. McpSecurityConfiguration (自动配置)

- `@AutoConfiguration`
- `@ConditionalOnClass({SecurityFilterChain.class, OncePerRequestFilter.class})` — 仅在 Spring Security 可用时激活
- `@ConditionalOnBean(McpAuthenticator.class)` — 仅当用户提供了 Authenticator 实现时才生效
- `@AutoConfigureBefore(McpAutoConfiguration.class)` — 确保 Filter 在 SSE transport 之前注册

装配 `McpSecurityFilter` Bean，通过 `FilterRegistrationBean` 注册到特定的 URL 模式。

### 4. McpServerProperties 补充

新增 `security` 嵌套配置：

```java
public static class SecurityProperties {
    private boolean enabled = true;
    private String tokenParameterName = "token";
    private String tokenHeaderName = "Authorization";
}
```

## 依赖变更

pom.xml 新增 (provided scope)：
- `spring-security-core` — `Authentication`、`GrantedAuthority`
- `spring-security-web` — `OncePerRequestFilter`

## 方法安全支持

无需 starter 额外配置。用户侧：
1. 在自己项目的 `@Configuration` 上加 `@EnableMethodSecurity`
2. 在 `@Tool` 方法上加 `@PreAuthorize("hasRole('ADMIN')")`

`@Tool` 方法所在 Bean 必须是 Spring 代理管理的（通常由 `@Component` 自然满足），AOP 切面运行时通过 `SecurityContextHolder` 获取当前 `Authentication` 完成权限校验。

## 用户使用步骤

1. 实现 `McpAuthenticator`，注册为 `@Component`
2. 在 `@Configuration` 上加 `@EnableMethodSecurity`
3. 在 `@Tool` 方法上根据需要加 `@PreAuthorize`
4. 方法内可通过 `SecurityContextHolder.getContext().getAuthentication()` 获取用户信息
