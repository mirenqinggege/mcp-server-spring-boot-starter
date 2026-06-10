package vip.jcfd.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletSseServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import vip.jcfd.scanner.McpToolScanner;
import vip.jcfd.scanner.ToolMethodInfo;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;

@AutoConfiguration
@ConditionalOnClass(McpServer.class)
@EnableConfigurationProperties(McpServerProperties.class)
public class McpAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public McpToolScanner mcpToolScanner() {
        return new McpToolScanner();
    }

    @Bean
    @ConditionalOnMissingBean
    public JacksonMcpJsonMapper mcpJsonMapper(ObjectMapper objectMapper) {
        return new JacksonMcpJsonMapper(objectMapper);
    }

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

    @Bean
    @ConditionalOnMissingBean
    public ServletRegistrationBean<?> mcpServletRegistration(
            HttpServletSseServerTransportProvider transportProvider) {
        return new ServletRegistrationBean<>(transportProvider, "/mcp/*");
    }

    @Bean
    @ConditionalOnMissingBean
    public McpSyncServer mcpSyncServer(
            HttpServletSseServerTransportProvider transport,
            McpServerProperties properties,
            JacksonMcpJsonMapper jsonMapper,
            ObjectMapper objectMapper,
            McpToolScanner scanner) {

        McpSyncServer server = McpServer.sync(transport)
                .jsonMapper(jsonMapper)
                .serverInfo(properties.getServerName(), properties.getServerVersion())
                .build();

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
                            Object[] args = resolveArguments(toolInfo, request, objectMapper);
                            Object result = toolInfo.getMethod().invoke(toolInfo.getBean(), args);
                            return buildResult(result, jsonMapper);
                        } catch (Exception e) {
                            Throwable cause = (e instanceof InvocationTargetException)
                                    ? e.getCause() : e;
                            return McpSchema.CallToolResult.builder()
                                    .content(List.of(new McpSchema.TextContent(
                                            null, "Error: " + cause.getMessage())))
                                    .isError(true)
                                    .build();
                        }
                    })
                    .build();

            server.addTool(spec);
        }

        return server;
    }

    private McpSchema.JsonSchema buildJsonSchema(ToolMethodInfo toolInfo) {
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();

        for (ToolMethodInfo.ParamInfo param : toolInfo.getParams()) {
            Map<String, Object> prop = buildParamSchema(param);
            properties.put(param.getName(), prop);
            if (param.isRequired()) {
                required.add(param.getName());
            }
        }

        return new McpSchema.JsonSchema(
                "object", properties, required.isEmpty() ? null : required,
                false, null, null);
    }

    private Map<String, Object> buildParamSchema(ToolMethodInfo.ParamInfo param) {
        Map<String, Object> prop = new LinkedHashMap<>();
        Class<?> type = param.getType();
        String jsonType = mapJavaTypeToJsonType(type);
        prop.put("type", jsonType);

        if ("array".equals(jsonType)) {
            Class<?> elementType = inferElementType(param);
            if (elementType != null) {
                Map<String, Object> items = new LinkedHashMap<>();
                items.put("type", mapJavaTypeToJsonType(elementType));
                prop.put("items", items);
            }
        }

        if (!param.getDescription().isEmpty()) {
            prop.put("description", param.getDescription());
        }
        return prop;
    }

    private Class<?> inferElementType(ToolMethodInfo.ParamInfo param) {
        Class<?> type = param.getType();
        if (type.isArray()) {
            return type.getComponentType();
        }
        if (Collection.class.isAssignableFrom(type) && param.getGenericType() != null) {
            Type genericType = param.getGenericType();
            if (genericType instanceof ParameterizedType pt) {
                Type[] args = pt.getActualTypeArguments();
                if (args.length == 1 && args[0] instanceof Class<?> clazz) {
                    return clazz;
                }
            }
        }
        return null;
    }

    private String mapJavaTypeToJsonType(Class<?> javaType) {
        if (javaType == String.class || javaType == CharSequence.class) return "string";
        if (javaType == int.class || javaType == Integer.class
                || javaType == long.class || javaType == Long.class
                || javaType == short.class || javaType == Short.class
                || javaType == byte.class || javaType == Byte.class
                || javaType == BigInteger.class) return "integer";
        if (javaType == boolean.class || javaType == Boolean.class) return "boolean";
        if (javaType == double.class || javaType == Double.class
                || javaType == float.class || javaType == Float.class
                || javaType == BigDecimal.class || javaType == Number.class) return "number";
        if (javaType.isArray() || Collection.class.isAssignableFrom(javaType)) return "array";
        if (Map.class.isAssignableFrom(javaType)) return "object";
        return "object";
    }

    private Object[] resolveArguments(ToolMethodInfo toolInfo, McpSchema.CallToolRequest request, ObjectMapper objectMapper) {
        Map<String, Object> args = request.arguments();
        if (args == null) args = Map.of();
        List<ToolMethodInfo.ParamInfo> params = toolInfo.getParams();
        Object[] result = new Object[params.size()];
        for (int i = 0; i < params.size(); i++) {
            Object rawValue = args.get(params.get(i).getName());
            if (rawValue == null) {
                result[i] = null;
                continue;
            }
            Class<?> targetType = params.get(i).getType();
            if (targetType.isInstance(rawValue)) {
                result[i] = rawValue;
            } else {
                result[i] = objectMapper.convertValue(rawValue, targetType);
            }
        }
        return result;
    }

    private McpSchema.CallToolResult buildResult(Object result, JacksonMcpJsonMapper jsonMapper) {
        if (result == null) {
            return McpSchema.CallToolResult.builder()
                    .content(List.of(new McpSchema.TextContent(null, "")))
                    .build();
        }
        if (result instanceof String text) {
            return McpSchema.CallToolResult.builder()
                    .content(List.of(new McpSchema.TextContent(null, text)))
                    .build();
        }
        if (result instanceof McpSchema.CallToolResult ctr) {
            return ctr;
        }
        if (result instanceof byte[] bytes) {
            String base64 = Base64.getEncoder().encodeToString(bytes);
            return McpSchema.CallToolResult.builder()
                    .content(List.of(new McpSchema.TextContent(null, base64)))
                    .build();
        }
        try {
            String json = jsonMapper.writeValueAsString(result);
            return McpSchema.CallToolResult.builder()
                    .content(List.of(new McpSchema.TextContent(null, json)))
                    .build();
        } catch (Exception e) {
            return McpSchema.CallToolResult.builder()
                    .content(List.of(new McpSchema.TextContent(null, result.toString())))
                    .build();
        }
    }
}
