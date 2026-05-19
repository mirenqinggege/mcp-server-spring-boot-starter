# MCP SSE Spring Boot Starter 实现计划

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 从零构建一个 Spring Boot Starter，通过 `@Tool` + `@ToolParam` 注解自动扫描并注册 MCP SSE 服务

**Architecture:** Spring Boot Auto-Configuration + MCP Java SDK v1.1.2 (稳定版) 的 `HttpServletSseServerTransportProvider`，通过 `BeanPostProcessor` 扫描所有 Spring Bean 发现 `@Tool` 方法，构建 `SyncToolSpecification` 注册到 `McpSyncServer`

**Tech Stack:** MCP Java SDK 1.1.2, Spring Boot 3.x AutoConfiguration, Jackson 2 (与 Spring Boot 兼容), Jakarta Servlet

**注意:** 此项目不是 git 仓库，所以不包含 commit 步骤。用户要求不自动测试，所以不包含测试步骤。

---

### Task 1: 更新 pom.xml — 添加所有依赖

**Files:**
- Modify: `pom.xml`

**依赖项：**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>vip.jcfd</groupId>
    <artifactId>mcp-server-spring-boot-starter</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>

    <properties>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <mcp-sdk.version>1.1.2</mcp-sdk.version>
        <spring-boot.version>3.4.0</spring-boot.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-dependencies</artifactId>
                <version>${spring-boot.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <dependencies>
        <!-- MCP SDK core (不含 Jackson 实现，避免版本冲突) -->
        <dependency>
            <groupId>io.modelcontextprotocol.sdk</groupId>
            <artifactId>mcp-core</artifactId>
            <version>${mcp-sdk.version}</version>
        </dependency>

        <!-- MCP Jackson 2 适配器（兼容 Spring Boot 的 Jackson） -->
        <dependency>
            <groupId>io.modelcontextprotocol.sdk</groupId>
            <artifactId>mcp-json-jackson2</artifactId>
            <version>${mcp-sdk.version}</version>
        </dependency>

        <!-- Spring Boot AutoConfigure -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-autoconfigure</artifactId>
            <scope>provided</scope>
        </dependency>

        <!-- Spring Boot Configuration Processor -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-configuration-processor</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- Spring WebMVC (for ServletRegistrationBean) -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
            <scope>provided</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <configuration>
                    <parameters>true</parameters>  <!-- 编译时保留方法参数名 -->
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

**步骤 1:** 用以上内容完整替换 pom.xml

---

### Task 2: 创建 @Tool 注解

**Files:**
- Create: `src/main/java/vip/jcfd/annotation/Tool.java`

```java
package vip.jcfd.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Tool {
    String name() default "";
    String description() default "";
}
```

**步骤 1:** 创建文件

---

### Task 3: 创建 @ToolParam 注解

**Files:**
- Create: `src/main/java/vip/jcfd/annotation/ToolParam.java`

```java
package vip.jcfd.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface ToolParam {
    String name() default "";
    String description() default "";
    boolean required() default true;
    boolean ignore() default false;
}
```

**步骤 1:** 创建文件

---

### Task 4: 创建 ToolMethodInfo — 内部模型

**Files:**
- Create: `src/main/java/vip/jcfd/scanner/ToolMethodInfo.java`

```java
package vip.jcfd.scanner;

import java.lang.reflect.Method;
import java.util.List;

public class ToolMethodInfo {
    private final String toolName;
    private final String description;
    private final Object bean;
    private final Method method;
    private final List<ParamInfo> params;

    public ToolMethodInfo(String toolName, String description, Object bean,
                          Method method, List<ParamInfo> params) {
        this.toolName = toolName;
        this.description = description;
        this.bean = bean;
        this.method = method;
        this.params = params;
    }

    public String getToolName() { return toolName; }
    public String getDescription() { return description; }
    public Object getBean() { return bean; }
    public Method getMethod() { return method; }
    public List<ParamInfo> getParams() { return params; }

    public static class ParamInfo {
        private final String name;
        private final String description;
        private final boolean required;
        private final Class<?> type;

        public ParamInfo(String name, String description, boolean required, Class<?> type) {
            this.name = name;
            this.description = description;
            this.required = required;
            this.type = type;
        }

        public String getName() { return name; }
        public String getDescription() { return description; }
        public boolean isRequired() { return required; }
        public Class<?> getType() { return type; }
    }
}
```

**步骤 1:** 创建文件

---

### Task 5: 创建 McpToolScanner — BeanPostProcessor

**Files:**
- Create: `src/main/java/vip/jcfd/scanner/McpToolScanner.java`

**核心逻辑：**
1. 实现 `BeanPostProcessor`
2. 在 `postProcessAfterInitialization` 中扫描每个 Bean 的所有方法
3. 找到带 `@Tool` 的方法，提取 `@ToolParam` 信息
4. 收集所有 `ToolMethodInfo` 到 List

```java
package vip.jcfd.scanner;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.LocalVariableTableParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.util.ReflectionUtils;
import vip.jcfd.annotation.Tool;
import vip.jcfd.annotation.ToolParam;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class McpToolScanner implements BeanPostProcessor {

    private final List<ToolMethodInfo> toolMethods = new CopyOnWriteArrayList<>();
    private final ParameterNameDiscoverer parameterNameDiscoverer =
            new LocalVariableTableParameterNameDiscoverer();

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        ReflectionUtils.doWithMethods(bean.getClass(), method -> {
            Tool toolAnn = method.getAnnotation(Tool.class);
            if (toolAnn == null) return;

            String toolName = toolAnn.name().isEmpty() ? method.getName() : toolAnn.name();
            String description = toolAnn.description();
            List<ToolMethodInfo.ParamInfo> params = extractParams(method);

            toolMethods.add(new ToolMethodInfo(toolName, description, bean, method, params));
        });
        return bean;
    }

    private List<ToolMethodInfo.ParamInfo> extractParams(Method method) {
        Parameter[] parameters = method.getParameters();
        String[] paramNames = parameterNameDiscoverer.getParameterNames(method);

        List<ToolMethodInfo.ParamInfo> result = new ArrayList<>();
        for (int i = 0; i < parameters.length; i++) {
            ToolParam tp = parameters[i].getAnnotation(ToolParam.class);

            // 如果标注了 ignore=true，跳过
            if (tp != null && tp.ignore()) continue;

            String name = (tp != null && !tp.name().isEmpty()) ? tp.name()
                    : (paramNames != null ? paramNames[i] : "arg" + i);
            String desc = (tp != null) ? tp.description() : "";
            boolean required = (tp == null || tp.isRequired());
            result.add(new ToolMethodInfo.ParamInfo(name, desc, required, parameters[i].getType()));
        }
        return result;
    }

    public List<ToolMethodInfo> getToolMethods() {
        return Collections.unmodifiableList(toolMethods);
    }
}
```

**步骤 1:** 创建文件

---

### Task 6: 创建 McpServerProperties — 配置属性

**Files:**
- Create: `src/main/java/vip/jcfd/config/McpServerProperties.java`

```java
package vip.jcfd.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mcp.server")
public class McpServerProperties {

    /** SSE 端点路径 */
    private String sseEndpoint = "/mcp/sse";

    /** 消息端点路径 */
    private String messageEndpoint = "/mcp/message";

    /** 服务器名称 */
    private String serverName = "mcp-server";

    /** 服务器版本 */
    private String serverVersion = "1.0.0";

    // getters and setters
    public String getSseEndpoint() { return sseEndpoint; }
    public void setSseEndpoint(String sseEndpoint) { this.sseEndpoint = sseEndpoint; }
    public String getMessageEndpoint() { return messageEndpoint; }
    public void setMessageEndpoint(String messageEndpoint) { this.messageEndpoint = messageEndpoint; }
    public String getServerName() { return serverName; }
    public void setServerName(String serverName) { this.serverName = serverName; }
    public String getServerVersion() { return serverVersion; }
    public void setServerVersion(String serverVersion) { this.serverVersion = serverVersion; }
}
```

**步骤 1:** 创建文件

---

### Task 7: 创建 McpAutoConfiguration — 自动配置核心

**Files:**
- Create: `src/main/java/vip/jcfd/config/McpAutoConfiguration.java`

**核心逻辑：**
1. 创建 `McpToolScanner` 扫描 Bean
2. 创建 `McpJsonMapper` (Jackson 2 适配器，包装 Spring Boot 的 ObjectMapper)
3. 创建 `HttpServletSseServerTransportProvider` (SSE 传输层)
4. 注册 Servlet
5. 从扫描到的 `@Tool` 方法构建 `McpSyncServer`
6. 在 `@PostConstruct` 或 `SmartInitializingSingleton` 阶段注册所有工具

```java
package vip.jcfd.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletSseServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import vip.jcfd.scanner.McpToolScanner;
import vip.jcfd.scanner.ToolMethodInfo;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

@AutoConfiguration
@ConditionalOnClass(McpServer.class)
@EnableConfigurationProperties(McpServerProperties.class)
public class McpAutoConfiguration {

    /**
     * Bean 扫描器：收集所有 @Tool 方法
     */
    @Bean
    @ConditionalOnMissingBean
    public McpToolScanner mcpToolScanner() {
        return new McpToolScanner();
    }

    /**
     * MCP JSON Mapper：包装 Spring Boot 的 ObjectMapper
     */
    @Bean
    @ConditionalOnMissingBean
    public JacksonMcpJsonMapper mcpJsonMapper(ObjectMapper objectMapper) {
        return new JacksonMcpJsonMapper(objectMapper);
    }

    /**
     * SSE 传输层：基于 Servlet 的 SSE Server Transport
     */
    @Bean
    @ConditionalOnMissingBean
    public HttpServletSseServerTransportProvider sseTransportProvider(
            McpServerProperties properties, JacksonMcpJsonMapper jsonMapper) {
        return new HttpServletSseServerTransportProvider.Builder()
                .jsonMapper(jsonMapper)
                .messageEndpoint(properties.getMessageEndpoint())
                .sseEndpoint(properties.getSseEndpoint())
                .build();
    }

    /**
     * 注册 SSE Servlet
     */
    @Bean
    @ConditionalOnMissingBean
    public ServletRegistrationBean<?> mcpServletRegistration(
            HttpServletSseServerTransportProvider transportProvider) {
        return new ServletRegistrationBean<>(transportProvider, "/mcp/*");
    }

    /**
     * 构建 McpSyncServer 并注册所有 @Tool 方法
     */
    @Bean
    @ConditionalOnMissingBean
    public McpSyncServer mcpSyncServer(
            HttpServletSseServerTransportProvider transport,
            McpServerProperties properties,
            JacksonMcpJsonMapper jsonMapper,
            McpToolScanner scanner) {

        McpSyncServer server = McpServer.sync(transport)
                .jsonMapper(jsonMapper)
                .serverInfo(properties.getServerName(), properties.getServerVersion())
                .build();

        // 注册所有扫描到的工具
        for (ToolMethodInfo toolInfo : scanner.getToolMethods()) {
            McpSchema.JsonSchema schema = buildJsonSchema(toolInfo);
            McpSchema.Tool tool = McpSchema.Tool.builder()
                    .name(toolInfo.getToolName())
                    .description(toolInfo.getDescription())
                    .inputSchema(schema)
                    .build();

            McpServerFeatures.SyncToolSpecification spec = McpServerFeatures.SyncToolSpecification.builder()
                    .tool(tool)
                    .callHandler((exchange, request) -> {
                        try {
                            Object[] args = resolveArguments(toolInfo, request);
                            Object result = toolInfo.getMethod().invoke(toolInfo.getBean(), args);
                            return buildResult(result, jsonMapper);
                        } catch (Exception e) {
                            Throwable cause = (e instanceof InvocationTargetException)
                                    ? e.getCause() : e;
                            return McpSchema.CallToolResult.builder()
                                    .content(List.of(new McpSchema.TextContent(
                                            "Error: " + cause.getMessage())))
                                    .isError(true)
                                    .build();
                        }
                    })
                    .build();

            server.addTool(spec);
        }

        return server;
    }

    /**
     * 根据方法参数信息构建 JSON Schema
     */
    private McpSchema.JsonSchema buildJsonSchema(ToolMethodInfo toolInfo) {
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();

        for (ToolMethodInfo.ParamInfo param : toolInfo.getParams()) {
            Map<String, Object> prop = new LinkedHashMap<>();
            prop.put("type", mapJavaTypeToJsonType(param.getType()));
            if (!param.getDescription().isEmpty()) {
                prop.put("description", param.getDescription());
            }
            properties.put(param.getName(), prop);
            if (param.isRequired()) {
                required.add(param.getName());
            }
        }

        return new McpSchema.JsonSchema(
                "object", properties, required.isEmpty() ? null : required,
                false, null, null);
    }

    /**
     * Java 类型 → JSON Schema 类型映射
     */
    private String mapJavaTypeToJsonType(Class<?> javaType) {
        if (javaType == String.class) return "string";
        if (javaType == int.class || javaType == Integer.class
                || javaType == long.class || javaType == Long.class) return "integer";
        if (javaType == boolean.class || javaType == Boolean.class) return "boolean";
        if (javaType == double.class || javaType == Double.class
                || javaType == float.class || javaType == Float.class
                || javaType == java.math.BigDecimal.class) return "number";
        return "string"; // 其他类型默认 string
    }

    /**
     * 从 CallToolRequest 中提取参数值，反射调用方法
     */
    private Object[] resolveArguments(ToolMethodInfo toolInfo, McpSchema.CallToolRequest request) {
        Map<String, Object> args = request.arguments();
        List<ToolMethodInfo.ParamInfo> params = toolInfo.getParams();
        Object[] result = new Object[params.size()];
        for (int i = 0; i < params.size(); i++) {
            result[i] = args.get(params.get(i).getName());
        }
        return result;
    }

    /**
     * 构建 CallToolResult
     */
    @SuppressWarnings("unchecked")
    private McpSchema.CallToolResult buildResult(Object result, JacksonMcpJsonMapper jsonMapper) {
        if (result == null) {
            return McpSchema.CallToolResult.builder()
                    .content(List.of(new McpSchema.TextContent("")))
                    .build();
        }
        if (result instanceof String text) {
            return McpSchema.CallToolResult.builder()
                    .content(List.of(new McpSchema.TextContent(text)))
                    .build();
        }
        if (result instanceof McpSchema.CallToolResult ctr) {
            return ctr;
        }
        if (result instanceof byte[] bytes) {
            return McpSchema.CallToolResult.builder()
                    .content(List.of(new McpSchema.BlobResourceContents(bytes)))
                    .build();
        }
        // 其他类型 JSON 序列化
        try {
            ObjectMapper om = (ObjectMapper) jsonMapper.getObjectMapper();
            String json = om.writeValueAsString(result);
            return McpSchema.CallToolResult.builder()
                    .content(List.of(new McpSchema.TextContent(json)))
                    .build();
        } catch (Exception e) {
            return McpSchema.CallToolResult.builder()
                    .content(List.of(new McpSchema.TextContent(result.toString())))
                    .build();
        }
    }
}
```

**注意:** 需要确认 `JacksonMcpJsonMapper` 是否有 `getObjectMapper()` 方法。如果没有，需要注入单独的 `ObjectMapper`。

**步骤 1:** 创建文件

---

### Task 8: 创建 AutoConfiguration.imports

**Files:**
- Create: `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

```
vip.jcfd.config.McpAutoConfiguration
```

**步骤 1:** 创建文件，确保目录存在

---

### Task 9: 删除存根 Main.java，验证编译

**步骤 1:** 删除 `src/main/java/vip/jcfd/Main.java`

**步骤 2:** 编译验证

运行: `cd /data2/project/mcp-server-spring-boot-starter && mvn compile -q`
期望: BUILD SUCCESS

---

## 任务依赖关系

```
Task 1 (pom.xml)
  ├── Task 2 (@Tool)        — 无依赖
  ├── Task 3 (@ToolParam)   — 无依赖
  ├── Task 4 (ToolMethodInfo) — 无依赖
  ├── Task 5 (McpToolScanner) — 依赖 Task 2, 3, 4
  ├── Task 6 (McpServerProperties) — 无依赖
  ├── Task 7 (McpAutoConfiguration) — 依赖 Task 2, 3, 4, 5, 6
  ├── Task 8 (AutoConfiguration.imports) — 依赖 Task 7
  └── Task 9 (验证编译) — 依赖 Task 1-8
```

无需等待依赖关系，可以直接按顺序依次执行 Task 1→2→3→4→5→6→7→8→9。
