package com.example.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * log-api へのリクエストログ送信（{@link com.example.api.filter.RequestLoggingFilter}）専用の
 * スレッドプール。
 *
 * <p>Spring Boot が自動構成する共有 Executor（applicationTaskExecutor）を使わず専用に切り出す理由:
 * ログ送信が詰まっても本来のリクエスト処理に使う汎用スレッドプールを消費しないようにするため。
 * fire-and-forget 用途なので小さめのサイズに抑える。
 */
@Configuration
public class LogApiAsyncConfig {

    @Bean
    public ThreadPoolTaskExecutor logApiTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("log-api-");
        executor.initialize();
        return executor;
    }
}