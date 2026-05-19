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
