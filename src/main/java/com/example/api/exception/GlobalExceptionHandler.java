package com.example.api.exception;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.stream.Collectors;

/**
 * アプリケーション全体の例外を一元処理するハンドラー。
 *
 * <p>@RestControllerAdvice は @ControllerAdvice + @ResponseBody の合成アノテーション。
 * 全 @RestController で発生した例外をここでキャッチし、JSON レスポンスに変換する。
 * Python 版 FastAPI の HTTPException と同じ {@code {"detail": "..."}} 形式で返す。
 *
 * <p>@ExceptionHandler のメソッドは最も具体的な例外クラスが優先して呼ばれる。
 * 最後の {@code Exception} ハンドラーはフォールバックとして機能する。
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 全エラーレスポンスの共通ボディ。
     * Python 版と同じ {@code {"detail": "..."}} 形式にする。
     */
    record ErrorResponse(String detail) {}

    /**
     * 同一メールアドレスのユーザーが既に存在する場合: 409 Conflict。
     */
    @ExceptionHandler(UserAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleUserAlreadyExists(UserAlreadyExistsException ex) {
        return new ErrorResponse(ex.getMessage());
    }

    /**
     * 指定ユーザーが存在しない場合: 404 Not Found。
     */
    @ExceptionHandler(UserNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleUserNotFound(UserNotFoundException ex) {
        return new ErrorResponse(ex.getMessage());
    }

    /**
     * 同一名称の会社が既に存在する場合: 409 Conflict。
     */
    @ExceptionHandler(CompanyAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleCompanyAlreadyExists(CompanyAlreadyExistsException ex) {
        return new ErrorResponse(ex.getMessage());
    }

    /**
     * 指定会社が存在しない場合: 404 Not Found。
     */
    @ExceptionHandler(CompanyNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleCompanyNotFound(CompanyNotFoundException ex) {
        return new ErrorResponse(ex.getMessage());
    }

    /**
     * @RequestBody のバリデーション失敗: 400 Bad Request。
     *
     * <p>@Valid が付いた @RequestBody のバリデーションに失敗すると Spring MVC が
     * MethodArgumentNotValidException をスローする。
     * 全フィールドのエラーメッセージを結合して detail として返す。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidationException(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return new ErrorResponse(detail);
    }

    /**
     * @RequestParam / @PathVariable のバリデーション失敗: 400 Bad Request。
     *
     * <p>Controller クラスに @Validated を付けると @RequestParam の @Min / @Max 等が
     * 有効になり、違反時に ConstraintViolationException がスローされる。
     * MethodArgumentNotValidException とは別クラスであることに注意。
     */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleConstraintViolation(ConstraintViolationException ex) {
        String detail = ex.getConstraintViolations().stream()
                .map(v -> v.getMessage())
                .collect(Collectors.joining(", "));
        return new ErrorResponse(detail);
    }

    /**
     * ResponseStatusException: ConfigurationController 等がスローする HTTP ステータス付き例外。
     *
     * <p>Spring MVC では @ExceptionHandler(Exception.class) が ResponseStatusExceptionResolver より
     * 先に実行されるため、ResponseStatusException を明示的にここで処理しないと 500 に変換されてしまう。
     * ここでキャッチして元のステータスコードをそのまま返す。
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode())
                .body(new ErrorResponse(ex.getReason() != null ? ex.getReason() : ex.getMessage()));
    }

    /**
     * 上記いずれにも該当しない予期しない例外: 500 Internal Server Error。
     *
     * <p>スタックトレースをサーバーログに残しつつ、クライアントには詳細を返さない。
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleGenericException(Exception ex) {
        log.error("Unexpected error", ex);
        return new ErrorResponse("Internal server error");
    }
}