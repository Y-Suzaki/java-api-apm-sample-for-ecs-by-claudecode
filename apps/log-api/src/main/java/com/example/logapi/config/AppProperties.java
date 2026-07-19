package com.example.logapi.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * application.yml の {@code app.*} プレフィックスを持つ設定値を型安全にバインドするクラス。
 */
@Component
@ConfigurationProperties(prefix = "app")
@Getter
@Setter
public class AppProperties {

    /**
     * 稼働環境名（例: local, prod）。
     * application.yml の app.env → 環境変数 APP_ENV の順で値が決まる。
     */
    private String env = "local";
}