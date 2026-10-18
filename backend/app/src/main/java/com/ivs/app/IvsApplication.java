package com.ivs.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 进销存系统（IVS）启动入口。 */
@SpringBootApplication(scanBasePackages = "com.ivs")
@EnableJpaRepositories(basePackages = "com.ivs")
@EntityScan(basePackages = "com.ivs")
@EnableScheduling
public class IvsApplication {
    public static void main(String[] args) {
        SpringApplication.run(IvsApplication.class, args);
    }
}