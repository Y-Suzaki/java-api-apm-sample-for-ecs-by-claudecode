package com.example.api.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * log-api（内部専用 Private API Gateway 経由）へのリクエストを定義する Feign Client インターフェース。
 *
 * <p>{@code url} は application.yml の {@code internal.log-api.url}（環境変数 LOG_API_URL）を参照する。
 * この URL は 09-apigw-log-api-internal.yaml が作成する Private REST API の invoke URL で、
 * VPC 内の Interface VPC Endpoint 経由でのみ到達できる（インターネットには公開されない）。
 *
 * <p>呼び出しは {@link com.example.api.filter.RequestLoggingFilter} からのみ行われ、
 * fire-and-forget（失敗しても呼び出し元の API レスポンスに影響させない）で使用する。
 */
@FeignClient(name = "log-api", url = "${internal.log-api.url}")
public interface LogApiClient {

    /**
     * リクエスト内容を JSON としてそのまま log-api の POST /logs に送信する。
     * log-api 側は 202 Accepted を返すのみで、レスポンスボディは持たない。
     */
    @PostMapping("/logs")
    void postLog(@RequestBody Map<String, Object> logEntry);
}