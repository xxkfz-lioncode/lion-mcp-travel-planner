package com.lion.travel.config;

import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


import java.time.Duration;

/**
 * RestClient 超时配置。
 *
 * <p>Spring AI Alibaba 的 DashScope 模块默认 read timeout 只有 10~30 秒,
 * 而通义千问生成长文本(如完整行程规划)需要 60~180 秒,导致频繁
 * {@code SocketTimeoutException / ReadTimeoutException}。此处通过
 * {@link RestClientCustomizer} 全局调大超时时间。</p>
 */
@Configuration
public class RestClientConfig {

    /** 连接超时 */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    /** 读取超时(3~5 分钟,覆盖大多数生成长文本场景) */
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(300);

    @Bean
    public RestClientCustomizer restClientCustomizer() {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(CONNECT_TIMEOUT)
                .withReadTimeout(READ_TIMEOUT);

        return restClientBuilder -> restClientBuilder.requestFactory(
                ClientHttpRequestFactoryBuilder.detect().build(settings)
        );
    }
}
