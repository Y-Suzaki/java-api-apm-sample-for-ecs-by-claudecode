package com.example.api.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * ipify.org へのリクエストを定義する Feign Client インターフェース。
 *
 * <p>@FeignClient を付けたインターフェースを定義するだけで、
 * Spring Cloud OpenFeign が実行時にプロキシ実装クラスを自動生成し Bean として登録する。
 * 呼び出し側はこのインターフェースをそのままインジェクトして使用できる。
 *
 * <ul>
 *   <li>{@code name}: FeignClient の識別名。application.yml の
 *       {@code spring.cloud.openfeign.client.config.<name>} でタイムアウト等を設定する。</li>
 *   <li>{@code url}: 接続先ベース URL。application.yml の {@code external.ipify.url} を参照する。</li>
 * </ul>
 */
@FeignClient(name = "ipify", url = "${external.ipify.url}")
public interface IpifyClient {

    /**
     * グローバル IP アドレスを JSON 形式で取得する。
     *
     * <p>リクエスト: {@code GET https://api.ipify.org?format=json}
     * <br>レスポンス: {@code {"ip": "x.x.x.x"}}
     *
     * <p>@RequestParam("format") により、Feign がクエリパラメータ {@code ?format=json} を
     * 自動でURL に付与する。
     */
    @GetMapping
    IpifyResponse getPublicIp(@RequestParam("format") String format);

    /**
     * ipify API のレスポンスを表す DTO。
     *
     * <p>Java Record として定義する。Jackson が JSON の {@code "ip"} キーを
     * {@code ip()} アクセサにマッピングする（Spring Boot 3.x の Jackson 2.14+ はレコードをネイティブサポート）。
     */
    record IpifyResponse(String ip) {}
}