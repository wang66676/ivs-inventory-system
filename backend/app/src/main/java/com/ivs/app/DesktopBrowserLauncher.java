package com.ivs.app;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.awt.Desktop;
import java.net.URI;

/** 安装版启动后自动打开本机登录页；服务器/服务模式保持关闭。 */
@Component
public class DesktopBrowserLauncher {

    @Value("${ivs.desktop.open-browser:false}")
    private boolean openBrowser;

    private final Environment environment;

    public DesktopBrowserLauncher(Environment environment) {
        this.environment = environment;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void openDesktopBrowser() {
        if (!openBrowser) return;
        try {
            if (Desktop.isDesktopSupported()) {
                String port = environment.getProperty("local.server.port",
                        environment.getProperty("server.port", "8090"));
                Desktop.getDesktop().browse(URI.create("http://localhost:" + port));
            }
        } catch (Exception ignored) {
            // 浏览器启动失败不影响后端服务。
        }
    }
}
