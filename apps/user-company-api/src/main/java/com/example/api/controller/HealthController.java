package com.example.api.controller;

import com.example.api.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * ALB ヘルスチェック用エンドポイント。
 *
 * <p>ALB ターゲットグループは定期的に GET /health を呼び出し、
 * HTTP 200 OK を受信することで ECS タスクが正常稼働していると判断する。
 * ECS タスクが unhealthy と判断された場合、ALB はトラフィックをそのタスクに送らなくなる。
 */
@RestController
@RequiredArgsConstructor
public class HealthController {

    private final AppProperties appProperties;

    /**
     * Python 版と同じ形式 {@code {"status": "ok", "env": "local"}} を返す。
     *
     * <p>Map を返すと Spring MVC の Jackson が自動的に JSON にシリアライズする。
     * {@code Map.of()} は Java 9+ で使用可能な不変 Map。
     */
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of(
                "status", "ok",
                "env", appProperties.getEnv()
        );
    }
}
