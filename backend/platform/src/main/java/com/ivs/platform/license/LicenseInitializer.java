package com.ivs.platform.license;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** 首次启动即开始试用计时，而不是等到第一次登录。 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class LicenseInitializer implements ApplicationRunner {
    private final LicenseService service;

    public LicenseInitializer(LicenseService service) {
        this.service = service;
    }

    @Override
    public void run(ApplicationArguments args) {
        service.status();
    }
}
