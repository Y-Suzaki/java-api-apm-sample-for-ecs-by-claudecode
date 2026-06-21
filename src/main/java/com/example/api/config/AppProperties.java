package com.example.api.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * application.yml の {@code app.*} プレフィックスを持つ設定値を型安全にバインドするクラス。
 *
 * <p>@ConfigurationProperties は @Value("${app.env}") のような個別インジェクションと異なり、
 * プレフィックス配下の全プロパティを一括でバインドできる。
 * 設定値が増えた場合もフィールドを追加するだけで対応できる。
 *
 * <p>@Component を付与して Bean として登録し、コンストラクタインジェクションで利用する。
 * Spring Boot の自動設定により、application.yml / 環境変数の値が自動的にセットされる。
 */
@Component
@ConfigurationProperties(prefix = "app")
@Getter
@Setter  // @ConfigurationProperties のバインドに setter が必要
public class AppProperties {

    /**
     * 稼働環境名（例: local, prod）。
     * application.yml の app.env → 環境変数 APP_ENV の順で値が決まる。
     */
    private String env = "local";
}