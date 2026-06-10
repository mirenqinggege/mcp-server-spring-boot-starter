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
