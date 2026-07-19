package com.example.api.exception;

/**
 * 同一メールアドレスのユーザーが DynamoDB に既に存在する場合にスローされる例外。
 *
 * <p>DynamoDB の条件付き書き込み（attribute_not_exists(email)）が失敗した場合に
 * UserService がスローする。
 * GlobalExceptionHandler が HTTP 409 Conflict に変換してクライアントへ返す。
 *
 * <p>RuntimeException を継承することで、呼び出し元に throws 宣言を強制しない（非チェック例外）。
 */
public class UserAlreadyExistsException extends RuntimeException {

    public UserAlreadyExistsException(String email) {
        super("User already exists: " + email);
    }
}