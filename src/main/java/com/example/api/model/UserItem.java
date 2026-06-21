package com.example.api.model;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;

/**
 * DynamoDB の users テーブルにマッピングされるエンティティクラス（AWS SDK v1 DynamoDBMapper 用）。
 *
 * <h3>DynamoDBMapper のアノテーションルール</h3>
 * <ul>
 *   <li>@DynamoDBTable: 対象テーブル名を指定する。DynamoDbConfig の TableNameOverride で実行時に上書きされる。</li>
 *   <li>@DynamoDBHashKey: DynamoDB のパーティションキーを示す。getter メソッドに付与する必要がある。</li>
 *   <li>@DynamoDBAttribute: Java フィールド名と DynamoDB 属性名が異なる場合に attributeName で明示する。
 *       フィールド名と属性名が同じ場合は省略できるが、本クラスでは created_at / updated_at のように
 *       スネークケースとキャメルケースが異なるため明示している。</li>
 * </ul>
 *
 * <h3>DynamoDBMapper が必要とする制約</h3>
 * <ul>
 *   <li>public no-arg コンストラクタ: Mapper がリフレクションでインスタンスを生成するために必須。</li>
 *   <li>各属性に対応する getter / setter: Mapper がデータの読み書きに使用する。</li>
 * </ul>
 *
 * <h3>Lombok を使わない理由</h3>
 * DynamoDBMapper はアノテーションを getter メソッドから検索するため、
 * Lombok の @Getter が生成するメソッドにはアノテーションが付与されない。
 * アノテーションの配置を明確にするため、このクラスは getter/setter を明示的に記述している。
 */
@DynamoDBTable(tableName = "users")  // DynamoDbConfig の TableNameOverride で上書きされる
public class UserItem {

    private String email;
    private String name;
    private String createdAt;
    private String updatedAt;

    /** DynamoDBMapper がリフレクションでインスタンスを生成するために必要な no-arg コンストラクタ。 */
    public UserItem() {}

    /**
     * DynamoDB パーティションキー（email）。
     * @DynamoDBHashKey は必ず getter メソッドに付与すること。
     */
    @DynamoDBHashKey(attributeName = "email")
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    /** @DynamoDBAttribute を省略した場合、フィールド名 "name" がそのまま DynamoDB 属性名になる。 */
    @DynamoDBAttribute(attributeName = "name")
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    /**
     * DynamoDB 属性名は created_at（スネークケース）、Java フィールドは createdAt（キャメルケース）。
     * attributeName で明示することでこの差異を吸収する。
     * 値は ISO-8601 UTC 文字列として保存する（例: "2024-01-01T00:00:00Z"）。
     */
    @DynamoDBAttribute(attributeName = "created_at")
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    @DynamoDBAttribute(attributeName = "updated_at")
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}