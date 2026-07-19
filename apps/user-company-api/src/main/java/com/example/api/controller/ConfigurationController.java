package com.example.api.controller;

import com.example.api.client.IpifyClient;
import com.example.api.config.AppProperties;
import com.example.api.model.ConfigurationResponse;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 外部 HTTP API（ipify）呼び出しのサンプルエンドポイント。
 *
 * <p>目的:
 * <ul>
 *   <li>ECS タスク（Private Subnet）→ NAT Gateway → Internet の疎通確認</li>
 *   <li>後フェーズで Feign Client の自動計装が X-Ray サービスマップに外部 HTTP ノードとして
 *       現れることを確認するための準備</li>
 * </ul>
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class ConfigurationController {

    /** IpifyClient の実装は Spring Cloud OpenFeign が自動生成する */
    private final IpifyClient ipifyClient;

    private final AppProperties appProperties;

    /** application.yml の spring.application.name をインジェクト */
    @Value("${spring.application.name}")
    private String applicationName;

    /**
     * GET /configuration: グローバル IP アドレスとサービス設定を返す。
     *
     * <p>Feign Client が ipify を呼び出し、NAT Gateway の EIP を取得する。
     * タイムアウトは application.yml の spring.cloud.openfeign.client.config.ipify で管理する。
     * 外部 API が到達不能な場合は 502 Bad Gateway を返す。
     */
    @GetMapping("/configuration")
    public ConfigurationResponse getConfiguration() {
        IpifyClient.IpifyResponse ipifyResponse;
        try {
            // Feign Client がここで HTTP リクエストを発行する。
            // 実装クラスは Spring Cloud OpenFeign が自動生成しているため、コードには現れない。
            ipifyResponse = ipifyClient.getPublicIp("json");
        } catch (FeignException e) {
            // 接続失敗・タイムアウト・HTTP エラー等を一括で 502 Bad Gateway に変換する
            log.error("Failed to call ipify: {}", e.getMessage());
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Failed to call external API: " + e.getMessage(),
                    e);
        }

        return new ConfigurationResponse(
                applicationName,
                appProperties.getEnv(),
                ipifyResponse.ip()
        );
    }
}
