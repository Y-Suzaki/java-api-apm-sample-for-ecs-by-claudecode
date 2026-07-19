package com.example.logapi.controller;

import tools.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * JSON 形式のログを受け付けて標準出力するエンドポイント。
 *
 * <p>Content-Type: application/json のリクエストのみを受け付ける
 * （それ以外は 415、不正な JSON は 400 を Spring のデフォルトエラーハンドリングが返す）。
 * JsonNode で受けることで、ログのフィールド構成自体は特定のスキーマに固定せず
 * 任意の JSON 構造を許容する。SLF4J（Logback のコンソール Appender）経由で標準出力へ出し、
 * ECS では awslogs ドライバがそれを CloudWatch Logs に転送する。
 */
@Slf4j
@RestController
public class LogController {

    @PostMapping("/logs")
    public ResponseEntity<Void> receiveLog(@RequestBody JsonNode body) {
        log.info("{}", body);
        return ResponseEntity.accepted().build();
    }
}