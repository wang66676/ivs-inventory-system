package com.ivs.app;

import com.ivs.platform.security.api.LoginResponse;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/** 集成测试登录支持：以引导管理员登录，并给 RestTemplate 注入 Bearer Token。 */
final class TestAuthSupport {
    private static final Set<RestTemplate> CONFIGURED =
            Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));
    private static volatile String token;

    private TestAuthSupport() {
    }

    static void authenticate(TestRestTemplate client) {
        RestTemplate restTemplate = client.getRestTemplate();
        // HttpURLConnection 在 POST 收到 401 时会抛 HttpRetryException（JDK 行为），
        // 改用 JDK HttpClient 以便测试能断言 401 响应本身
        if (restTemplate.getRequestFactory() instanceof SimpleClientHttpRequestFactory) {
            restTemplate.setRequestFactory(new JdkClientHttpRequestFactory());
        }
        synchronized (CONFIGURED) {
            if (CONFIGURED.add(restTemplate)) {
                restTemplate.getInterceptors().add(new BearerTokenInterceptor());
            }
        }
        token = null;
        var response = client.postForEntity(
                "/api/platform/auth/login",
                Map.of("username", "admin", "password", "admin123"),
                LoginResponse.class
        );
        if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
            throw new IllegalStateException("测试登录失败: " + response.getStatusCode());
        }
        token = response.getBody().token();
    }

    static String currentToken() {
        return token;
    }

    private static final class BearerTokenInterceptor implements ClientHttpRequestInterceptor {
        @Override
        public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                            ClientHttpRequestExecution execution) throws IOException {
            String currentToken = token;
            if (currentToken != null
                    && !request.getURI().getPath().endsWith("/api/platform/auth/login")
                    && !request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
                request.getHeaders().setBearerAuth(currentToken);
            }
            return execution.execute(request, body);
        }
    }
}