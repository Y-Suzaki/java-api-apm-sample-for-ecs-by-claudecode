package com.example.logapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot アプリケーションのエントリポイント。
 *
 * <p>アプリケーション分離の最低限のサンプルとして、任意のログを受け付けて
 * 標準出力するだけの単機能 API を提供する。
 */
@SpringBootApplication
public class LogApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(LogApiApplication.class, args);
    }
}