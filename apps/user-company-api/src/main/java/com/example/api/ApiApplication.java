package com.example.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Spring Boot アプリケーションのエントリポイント。
 *
 * <p>@SpringBootApplication は以下 3 つのアノテーションの合成である:
 * <ul>
 *   <li>@Configuration: このクラスを Bean 定義のソースとして登録する</li>
 *   <li>@EnableAutoConfiguration: classpath の依存関係に基づいて自動設定を有効化する</li>
 *   <li>@ComponentScan: 同パッケージ配下の @Component / @Service / @Repository 等を検出する</li>
 * </ul>
 *
 * <p>@EnableFeignClients により、同パッケージ配下の @FeignClient アノテーションが付いた
 * インターフェース（IpifyClient）が自動的にプロキシ実装を生成され、Bean として登録される。
 */
@SpringBootApplication
@EnableFeignClients
public class ApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiApplication.class, args);
    }
}