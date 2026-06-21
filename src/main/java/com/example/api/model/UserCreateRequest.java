package com.example.api.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * POST /users のリクエストボディ。
 *
 * <p>Jakarta Bean Validation アノテーションでバリデーションルールを定義する。
 * Controller の @RequestBody に @Valid を付与すると、Spring MVC がリクエスト受信時に
 * 自動で検証を実行し、違反があれば MethodArgumentNotValidException をスローする。
 * GlobalExceptionHandler がこれを HTTP 400 Bad Request に変換する。
 */
public record UserCreateRequest(

        /**
         * メールアドレス。DynamoDB のパーティションキーとなるため、システム内で一意でなければならない。
         *
         * @NotBlank: null / 空文字 / 空白のみのいずれも拒否する（@NotNull + 空白チェック）
         * @Email: RFC 5322 に準拠したメールアドレス形式を検証する
         */
        @NotBlank(message = "email is required")
        @Email(message = "email must be a valid email address")
        String email,

        /**
         * 表示名。
         *
         * @Size(max=100): DynamoDB に保存する文字列長を制限する
         */
        @NotBlank(message = "name is required")
        @Size(max = 100, message = "name must be 100 characters or less")
        String name
) {}