package com.example.api.model;

import java.time.Instant;

/**
 * ユーザー API のレスポンス DTO。
 *
 * <p>Java Record として定義することで、コンストラクタ・アクセサ（getter）・
 * equals / hashCode / toString が自動生成される。イミュータブルなため、
 * API レスポンスのような読み取り専用オブジェクトに適している。
 *
 * <p>Jackson はデフォルトで Record のアクセサ名（email(), name() 等）を
 * キャメルケースの JSON キーとしてシリアライズする。
 * → {"email":"...", "name":"...", "createdAt":"...", "updatedAt":"..."}
 */
public record UserResponse(
        String email,
        String name,
        Instant createdAt,
        Instant updatedAt
) {
    /**
     * DynamoDB のエンティティ（UserItem）から UserResponse を生成するファクトリメソッド。
     *
     * <p>UserItem の createdAt / updatedAt は ISO-8601 文字列のため、
     * Instant.parse() で変換する。
     */
    public static UserResponse from(UserItem item) {
        return new UserResponse(
                item.getEmail(),
                item.getName(),
                Instant.parse(item.getCreatedAt()),
                Instant.parse(item.getUpdatedAt())
        );
    }
}