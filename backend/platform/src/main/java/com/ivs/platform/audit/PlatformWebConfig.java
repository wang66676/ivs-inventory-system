package com.ivs.platform.audit;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 注册审计拦截器（仅作用于 /api/**）。 */
@Configuration
public class PlatformWebConfig implements WebMvcConfigurer {

    private final ApiAuditInterceptor apiAuditInterceptor;

    public PlatformWebConfig(ApiAuditInterceptor apiAuditInterceptor) {
        this.apiAuditInterceptor = apiAuditInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(apiAuditInterceptor).addPathPatterns("/api/**");
    }
}