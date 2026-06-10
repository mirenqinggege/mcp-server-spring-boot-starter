# MCP Server Spring Boot Starter

基于 Spring Boot 的 [MCP (Model Context Protocol)](https://modelcontextprotocol.io) Server 快速启动器。通过注解 `@Tool` 和
`@ToolParam`，将任意 Spring Bean 的方法一键暴露为 MCP 工具，通过 SSE 传输层对外提供服务。

## 快速开始

### 1. 添加依赖

```xml

<dependency>
    <groupId>vip.jcfd</groupId>
    <artifactId>mcp-server-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. 编写工具方法

```java
import vip.jcfd.annotation.Tool;
import vip.jcfd.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class WeatherTool {

    @Tool(name = "get_weather", description = "根据城市名称查询天气")
    public String getWeather(
            @ToolParam(description = "城市名称") String city,
            @ToolParam(description = "语言，例如 zh-CN", required = false) String lang) {
        return city + " 今天晴天，25°C";
    }

    @Tool(description = "两数相加")
    public int add(@ToolParam(description = "第一个数") int a,
                   @ToolParam(description = "第二个数") int b) {
        return a + b;
    }
}
```

### 3. 配置文件

```yaml
# application.yml (可选，以下均为默认值)
mcp:
  server:
    sse-endpoint: /mcp/sse          # SSE 连接端点
    message-endpoint: /mcp/message  # JSON-RPC 消息端点
    server-name: mcp-server         # 服务名称
    server-version: 1.0.0           # 服务版本
```

### 4. 启动并连接

启动 Spring Boot 应用后，MCP 客户端通过以下地址连接：

```
http://localhost:8080/mcp/sse
```

在 Claude Code 中配置示例：

```json
{
  "mcpServers": {
    "my-server": {
      "type": "sse",
      "url": "http://localhost:8080/mcp/sse"
    }
  }
}
```

## 注解说明

### @Tool

标记方法为 MCP 工具。

| 属性            | 类型       | 默认值  | 说明               |
|---------------|----------|------|------------------|
| `name`        | `String` | 方法名  | 工具名称，须全局唯一       |
| `description` | `String` | `""` | 工具描述，供 AI 模型理解用途 |

### @ToolParam

描述方法参数的元信息。

| 属性            | 类型        | 默认值     | 说明      |
|---------------|-----------|---------|---------|
| `name`        | `String`  | 参数名     | 参数名称    |
| `description` | `String`  | `""`    | 参数描述    |
| `required`    | `boolean` | `true`  | 是否必填    |
| `ignore`      | `boolean` | `false` | 是否忽略该参数 |

> 参数名默认通过字节码获取（需 `-parameters` 编译选项），也可通过 `@ToolParam(name = "xxx")` 显式指定。

## 支持的类型

### 参数类型

方法参数支持所有常用 Java 类型，自动映射为 JSON Schema：

| Java 类型                                                                  | JSON Schema 类型 |
|--------------------------------------------------------------------------|----------------|
| `String`、`CharSequence`                                                  | `string`       |
| `int`/`Integer`、`long`/`Long`、`short`/`Short`、`byte`/`Byte`、`BigInteger` | `integer`      |
| `boolean`/`Boolean`                                                      | `boolean`      |
| `double`/`Double`、`float`/`Float`、`BigDecimal`、`Number`                  | `number`       |
| 数组 (`int[]`、`String[]` 等)、`List`/`Set`/`Collection`                      | `array`        |
| `Map`、POJO                                                               | `object`       |

参数值通过 Jackson 自动转换到目标类型，无需手动处理类型强制。

### 返回值类型

| 返回类型             | 处理方式         |
|------------------|--------------|
| `null`           | 空文本          |
| `String`         | 直接作为文本返回     |
| `CallToolResult` | 原样透传（高级用法）   |
| `byte[]`         | Base64 编码为文本 |
| 其他 POJO          | JSON 序列化为文本  |

## 高级用法

### 直接返回 CallToolResult

需要对返回内容精细控制时，直接返回 `CallToolResult`：

```java
import io.modelcontextprotocol.spec.McpSchema;

@Tool(name = "search", description = "搜索文档")
public McpSchema.CallToolResult search(
        @ToolParam(description = "关键词") String keyword) {

    List<McpSchema.Content> contents = List.of(
            new McpSchema.TextContent(null, "结果1"),
            new McpSchema.TextContent(null, "结果2")
    );

    return McpSchema.CallToolResult.builder()
            .content(contents)
            .build();
}
```

### 注入上下文参数

使用 `@ToolParam(ignore = true)` 忽略框架层参数，在方法中通过其他方式获取：

```java

@Tool(name = "user_info", description = "获取用户信息")
public String userInfo(
        @ToolParam(ignore = true) HttpServletRequest request) {
    String userId = request.getHeader("X-User-Id");
    return "User: " + userId;
}
```

### 错误处理

工具方法抛出异常时，自动捕获并包装为 `isError=true` 的 `CallToolResult`，错误消息原样返回给客户端。

## 兼容性

| 组件          | 版本     |
|-------------|--------|
| Java        | 17+    |
| Spring Boot | 3.4.0+ |
| MCP SDK     | 1.1.2  |

## 原理简述

1. `McpToolScanner`（`BeanPostProcessor`）在容器启动时扫描所有 Bean，收集标记了 `@Tool` 的方法
2. `McpAutoConfiguration` 自动装配 SSE 传输层（`HttpServletSseServerTransportProvider`），注册到 `/mcp/*`
3. 为每个工具方法生成 JSON Schema，构建 `SyncToolSpecification` 并注册到 `McpSyncServer`
4. 客户端通过 SSE 端点建立连接 → 接收消息端点地址 → 通过 POST 调用工具

## 协议实现范围

| 能力            | 状态  |
|---------------|-----|
| `tools/list`  | 已实现 |
| `tools/call`  | 已实现 |
| `resources/*` | 未实现 |
| `prompts/*`   | 未实现 |
| `logging/*`   | 未实现 |
