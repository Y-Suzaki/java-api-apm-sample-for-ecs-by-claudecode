package com.example.api.exception;

/**
 * 指定されたメールアドレスのユーザーが DynamoDB に存在しない場合にスローされる例外。
 *
 * <p>DynamoDB からの取得結果が null の場合、または条件付き更新
 * （attribute_exists(email)）が失敗した場合に UserService がスローする。
 * GlobalExceptionHandler が HTTP 404 Not Found に変換してクライアントへ返す。
 */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(String email) {
        super("User not found: " + email);
    }
}