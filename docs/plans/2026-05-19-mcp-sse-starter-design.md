# MCP SSE Spring Boot Starter 设计文档

## 概述

设计一个 Spring Boot Starter，通过 `@Tool` 和 `@ToolParam` 注解标注方法，在 Spring Boot 启动时自动扫描并注册为 MCP (Model Context Protocol) SSE 服务的工具。

## 架构

```
用户应用 (@SpringBootApplication)
  │
  ├─ @Tool / @ToolParam 注解层
  │
  ├─ McpAutoConfiguration        (自动配置)
  │  └─ McpServerProperties      (配置属性: application.yml)
  │
  ├─ McpToolScanner              (扫描层: BeanPostProcessor)
  │  └─ 扫描所有 Bean → 发现 @Tool 方法
  │
  ├─ McpToolRegistry             (注册层)
  │  └─ 构建 SyncToolSpecification → 注册到 McpSyncServer
  │
  └─ WebMvcSseServerTransport    (传输层: SSE)
     └─ 端点: /mcp/sse, /mcp/message
```

## 注解设计

### @Tool — 方法级别，标记 MCP 工具

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Tool {
    String name() default "";
    String description() default "";
}
```

### @ToolParam — 参数级别，描述参数元信息（可选）

```java
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface ToolParam {
    String name() default "";
    String description() default "";
    boolean required() default true;
    boolean ignore() default false;    // true 时跳过该参数
}
```

行为规则：
- 参数不标注 `@ToolParam` → 自动根据参数名 + 类型推断 JSON Schema
- 标注 `@ToolParam` → 覆盖/补充描述和必要信息
- `@ToolParam(ignore = true)` → 该参数不暴露

## 核心组件

### McpToolScanner

实现 `BeanPostProcessor.postProcessAfterInitialization`，在每个 Bean 初始化后检查方法。

- 扫描所有带有 `@Tool` 的方法
- 读取方法参数上的 `@ToolParam`（如有）
- 通过 Spring 的 `ParameterNameDiscoverer` 获取参数名
- 将参数类型映射为 JSON Schema 类型

类型映射规则：
| Java 类型 | JSON Schema 类型 |
|-----------|-----------------|
| String | string |
| int/Integer | integer |
| boolean/Boolean | boolean |
| double/Double, float/Float, BigDecimal | number |
| 其他 POJO | object |

### McpToolRegistry

收集所有扫描到的 `SyncToolSpecification`，在启动阶段通过 `McpServer.sync().tool()` 注册到 `McpSyncServer`。

### McpAutoConfiguration

Spring Boot 自动装配入口，通过 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 激活。

提供的 Bean：
1. `WebMvcSseServerTransportProvider` — SSE 传输层
2. `RouterFunction<ServerResponse>` — MCP 路由
3. `McpSyncServer` — MCP 同步服务器
4. `McpToolScanner` — 注解扫描器

### McpServerProperties

```yaml
mcp:
  server:
    sse-endpoint: /mcp/sse
    message-endpoint: /mcp/message
    server-name: mcp-server
    server-version: 1.0.0
```

## 错误处理

- 所有 `@Tool` 方法执行异常统一捕获，包装为 MCP `isError = true` 的 `CallToolResult`
- 不对外暴露堆栈信息

## 返回值序列化

| 返回类型 | 处理方式 |
|---------|---------|
| String | 直接文本内容 |
| CallToolResult | 原样透传 |
| byte[] | BlobResourceContents |
| 其他 POJO/集合 | JSON 序列化 |
| void/null | 空成功响应 |

## Maven 依赖

```xml
<dependency>
    <groupId>io.modelcontextprotocol.sdk</groupId>
    <artifactId>mcp</artifactId>
    <version>0.9.0</version>
</dependency>
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>mcp-spring-webmvc</artifactId>
    <version>2.0.0</version>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-autoconfigure</artifactId>
    <version>3.4.0</version>
    <scope>provided</scope>
</dependency>
```

## 包结构

```
vip.jcfd
├── annotation
│   ├── Tool.java
│   └── ToolParam.java
├── config
│   ├── McpServerProperties.java
│   └── McpAutoConfiguration.java
├── scanner
│   ├── McpToolScanner.java
│   └── ToolMethodInfo.java
└── registry
    └── McpToolRegistry.java
```
