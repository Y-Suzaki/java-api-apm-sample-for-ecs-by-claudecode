package com.example.logapi.controller;

import com.example.logapi.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * ALB / NLB ヘルスチェック用エンドポイント。
 */
@RestController
@RequiredArgsConstructor
public class HealthController {

    private final AppProperties appProperties;

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of(
                "status", "ok",
                "env", appProperties.getEnv()
        );
    }
}