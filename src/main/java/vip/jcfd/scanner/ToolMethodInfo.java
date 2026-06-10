package vip.jcfd.scanner;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
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
        private final Type genericType;

        public ParamInfo(String name, String description, boolean required,
                         Class<?> type, Type genericType) {
            this.name = name;
            this.description = description;
            this.required = required;
            this.type = type;
            this.genericType = genericType;
        }

        public String getName() { return name; }
        public String getDescription() { return description; }
        public boolean isRequired() { return required; }
        public Class<?> getType() { return type; }
        public Type getGenericType() { return genericType; }
    }
}
