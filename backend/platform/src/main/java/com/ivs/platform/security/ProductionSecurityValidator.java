package com.ivs.platform.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/** 生产环境启动门禁：禁止默认管理员密码和未配置的安全参数。 */
@Component
public class ProductionSecurityValidator implements ApplicationRunner {
    private final String activeProfiles;
    private final boolean bootstrapEnabled;
    private final String bootstrapPassword;

    public ProductionSecurityValidator(@Value("${spring.profiles.active:}") String activeProfiles,
                                       @Value("${ivs.security.bootstrap-admin.enabled:true}") boolean bootstrapEnabled,
                                       @Value("${ivs.security.bootstrap-admin.password:admin123}") String bootstrapPassword) {
        this.activeProfiles = activeProfiles;
        this.bootstrapEnabled = bootstrapEnabled;
        this.bootstrapPassword = bootstrapPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean production = Arrays.stream(activeProfiles.split(","))
                .map(String::trim).anyMatch("prod"::equalsIgnoreCase);
        if (!production) return;
        if (bootstrapEnabled && (bootstrapPassword == null || bootstrapPassword.isBlank()
                || "admin123".equals(bootstrapPassword) || bootstrapPassword.length() < 12)) {
            throw new IllegalStateException("生产环境引导管理员密码必须至少 12 位且不能使用默认密码");
        }
    }
}