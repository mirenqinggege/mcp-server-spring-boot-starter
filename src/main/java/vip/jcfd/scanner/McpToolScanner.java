package vip.jcfd.scanner;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.DefaultParameterNameDiscoverer;
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
            new DefaultParameterNameDiscoverer();

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

            if (tp != null && tp.ignore()) continue;

            String name = (tp != null && !tp.name().isEmpty()) ? tp.name()
                    : (paramNames != null ? paramNames[i] : "arg" + i);
            String desc = (tp != null) ? tp.description() : "";
            boolean required = (tp == null || tp.required());
            result.add(new ToolMethodInfo.ParamInfo(name, desc, required,
                    parameters[i].getType(), parameters[i].getParameterizedType()));
        }
        return result;
    }

    public List<ToolMethodInfo> getToolMethods() {
        return Collections.unmodifiableList(toolMethods);
    }
}
