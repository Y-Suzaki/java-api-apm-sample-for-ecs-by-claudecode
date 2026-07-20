package com.example.logapi.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 全リクエストの method / path / 送信元 IP / レスポンスステータスを標準出力に記録する Filter。
 *
 * <p>API Gateway -> VPC Link -> NLB -> ECS の経路でリクエストが実際に届いているかを
 * CloudWatch Logs から確認できるようにするための最小限のアクセスログ。コントローラより
 * 手前（DispatcherServlet の前段）で必ず通るため、Content-Type 不一致（415）や不正な JSON（400）
 * のように各コントローラのメソッドまで到達しないリクエストも記録される。
 */
@Component
@Slf4j
public class RequestLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } finally {
            log.info(
                    "{} {} from {} -> {}",
                    request.getMethod(),
                    request.getRequestURI(),
                    request.getRemoteAddr(),
                    response.getStatus());
        }
    }
}
