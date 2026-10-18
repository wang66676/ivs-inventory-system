package com.ivs.platform.audit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 写操作留痕：记录所有 POST / PUT / DELETE 的 API 调用（操作人、路径、结果状态）。
 * 业务动作的语义审计由各模块的领域事件承担，两者的检索入口相同。
 */
@Component
public class ApiAuditInterceptor implements HandlerInterceptor {

    private final PlatformActionAuditor auditor;

    public ApiAuditInterceptor(PlatformActionAuditor auditor) {
        this.auditor = auditor;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception exception) {
        String method = request.getMethod();
        if (!"POST".equals(method) && !"PUT".equals(method) && !"DELETE".equals(method)) {
            return;
        }
        String path = request.getRequestURI();
        if (path == null || !path.startsWith("/api/")) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("method", method);
        payload.put("path", path);
        payload.put("status", response.getStatus());
        if (exception != null) {
            payload.put("exception", exception.getClass().getSimpleName());
        }
        auditor.record("ApiWrite", "ApiCall", UUID.randomUUID(), payload);
    }
}