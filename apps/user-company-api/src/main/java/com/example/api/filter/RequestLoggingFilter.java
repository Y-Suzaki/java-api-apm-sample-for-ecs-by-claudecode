package com.example.api.filter;

import com.example.api.client.LogApiClient;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 全リクエスト（/health を除く）の method / path / body を、非同期で log-api に転送する Filter。
 *
 * <p>ContentCachingRequestWrapper はボディを直接読み取っただけではキャッシュされず、
 * 下流（コントローラ側の @RequestBody 読み取り等）がストリームを消費した時点で初めて
 * キャッシュが埋まる。そのため {@code filterChain.doFilter()} を実行した後でなければ
 * ボディを取り出せない（Spring の AbstractRequestLoggingFilter と同じ制約）。
 *
 * <p>log-api への送信は {@link com.example.api.config.LogApiAsyncConfig} が定義する専用スレッドプールで
 * fire-and-forget に実行し、例外はここで握りつぶす。log-api の障害や遅延が
 * user-company-api 本来の API レスポンスに影響しないようにするため。
 */
@Component
@Slf4j
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final String HEALTH_CHECK_PATH = "/health";

    /** キャッシュするボディの上限（バイト）。巨大なリクエストでもメモリを使い切らないよう上限を設ける。 */
    private static final int MAX_CACHED_BODY_BYTES = 65536;

    private final LogApiClient logApiClient;
    private final ThreadPoolTaskExecutor logApiTaskExecutor;

    public RequestLoggingFilter(
            LogApiClient logApiClient,
            @Qualifier("logApiTaskExecutor") ThreadPoolTaskExecutor logApiTaskExecutor) {
        this.logApiClient = logApiClient;
        this.logApiTaskExecutor = logApiTaskExecutor;
    }

    /** ALB/NLB のヘルスチェックはログ対象から除外する（X-Ray の /health 除外と同じ方針）。 */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return HEALTH_CHECK_PATH.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        ContentCachingRequestWrapper wrappedRequest =
                new ContentCachingRequestWrapper(request, MAX_CACHED_BODY_BYTES);
        try {
            filterChain.doFilter(wrappedRequest, response);
        } finally {
            dispatchLog(wrappedRequest);
        }
    }

    private void dispatchLog(ContentCachingRequestWrapper request) {
        Map<String, Object> logEntry = new LinkedHashMap<>();
        logEntry.put("timestamp", Instant.now().toString());
        logEntry.put("method", request.getMethod());
        logEntry.put("path", request.getRequestURI());
        logEntry.put("body", extractBody(request));

        logApiTaskExecutor.execute(() -> {
            try {
                logApiClient.postLog(logEntry);
                log.info("Sent request log to log-api: method={} path={}", logEntry.get("method"), logEntry.get("path"));
            } catch (Exception e) {
                log.warn("Failed to send request log to log-api: method={} path={} error={}",
                        logEntry.get("method"), logEntry.get("path"), e.getMessage());
            }
        });
    }

    private String extractBody(ContentCachingRequestWrapper request) {
        byte[] content = request.getContentAsByteArray();
        return content.length == 0 ? null : new String(content, StandardCharsets.UTF_8);
    }
}