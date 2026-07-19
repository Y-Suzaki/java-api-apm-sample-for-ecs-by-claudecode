package com.example.api.service;

import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.example.api.exception.UserAlreadyExistsException;
import com.example.api.exception.UserNotFoundException;
import com.example.api.model.UserCreateRequest;
import com.example.api.model.UserItem;
import com.example.api.model.UserResponse;
import com.example.api.model.UserUpdateRequest;
import com.example.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * ユーザー管理のビジネスロジック層。
 *
 * <p>Controller と Repository の間に位置し、以下の責務を持つ:
 * <ul>
 *   <li>DynamoDB 固有の例外（ConditionalCheckFailedException）を
 *       アプリケーション例外（UserAlreadyExistsException / UserNotFoundException）に変換する</li>
 *   <li>タイムスタンプの生成など、ビジネスルールを適用する</li>
 *   <li>UserItem（DynamoDB エンティティ）を UserResponse（API DTO）に変換する</li>
 * </ul>
 *
 * <p>@RequiredArgsConstructor（Lombok）: final フィールドに対するコンストラクタを自動生成する。
 * Spring がそのコンストラクタを使って依存性をインジェクトする（コンストラクタインジェクション推奨）。
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    /**
     * ユーザーを新規作成する。
     *
     * <p>タイムスタンプは UTC の現在時刻を ISO-8601 形式の文字列として DynamoDB に保存する。
     * 同一メールアドレスが既に存在する場合は UserAlreadyExistsException をスローする。
     */
    public UserResponse create(UserCreateRequest request) {
        // Instant.now().toString() → "2024-01-01T00:00:00.000Z" （ISO-8601 UTC 形式）
        String now = Instant.now().toString();

        UserItem item = new UserItem();
        item.setEmail(request.email());
        item.setName(request.name());
        item.setCreatedAt(now);
        item.setUpdatedAt(now);

        try {
            userRepository.save(item);
        } catch (ConditionalCheckFailedException e) {
            // DynamoDB の attribute_not_exists(email) 条件が失敗した → 同一 email が既に存在する
            throw new UserAlreadyExistsException(request.email());
        }

        return UserResponse.from(item);
    }

    /**
     * ユーザー一覧を取得する。
     *
     * @param limit 最大取得件数（Controller でデフォルト 50、最大 100 を適用済み）
     */
    public List<UserResponse> list(int limit) {
        // Stream.toList() は Java 16+ で使用可能（不変リストを返す）
        return userRepository.findAll(limit).stream()
                .map(UserResponse::from)
                .toList();
    }

    /**
     * メールアドレスでユーザーを取得する。
     *
     * @throws UserNotFoundException 指定 email のユーザーが存在しない場合
     */
    public UserResponse get(String email) {
        return userRepository.findByEmail(email)
                .map(UserResponse::from)
                .orElseThrow(() -> new UserNotFoundException(email));
    }

    /**
     * ユーザーの name を更新する。
     *
     * @throws UserNotFoundException 指定 email のユーザーが存在しない場合
     */
    public UserResponse update(String email, UserUpdateRequest request) {
        String updatedAt = Instant.now().toString();

        try {
            UserItem updated = userRepository.update(email, request.name(), updatedAt);
            return UserResponse.from(updated);
        } catch (ConditionalCheckFailedException e) {
            // DynamoDB の attribute_exists(email) 条件が失敗した → email が存在しない
            throw new UserNotFoundException(email);
        }
    }
}