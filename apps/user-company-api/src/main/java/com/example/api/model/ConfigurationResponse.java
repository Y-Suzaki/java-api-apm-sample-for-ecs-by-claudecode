package com.example.api.model;

/**
 * GET /configuration のレスポンス DTO。
 *
 * <p>ipify から取得したグローバル送信元 IP アドレスを outboundIp として返す。
 * ECS Fargate（Private Subnet）からのリクエストでは NAT Gateway の EIP が返るため、
 * NAT Gateway 経由でインターネット接続できているかの確認にもなる。
 */
public record ConfigurationResponse(
        /** アプリケーション名（spring.application.name の値）。 */
        String serviceName,

        /** 稼働環境名（APP_ENV の値。例: local, prod）。 */
        String environment,

        /** ipify から取得したグローバル送信元 IP アドレス。 */
        String outboundIp
) {}