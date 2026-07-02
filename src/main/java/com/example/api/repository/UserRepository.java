package com.example.api.repository;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBSaveExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.ScanResultPage;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.amazonaws.services.dynamodbv2.model.ExpectedAttributeValue;
import com.amazonaws.services.dynamodbv2.model.ReturnValue;
import com.amazonaws.services.dynamodbv2.model.UpdateItemRequest;
import com.amazonaws.services.dynamodbv2.model.UpdateItemResult;
import com.example.api.model.UserItem;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * DynamoDB の users テーブルに対する CRUD 操作を担うリポジトリ。
 *
 * <h3>API の使い分け</h3>
 * <ul>
 *   <li>DynamoDBMapper（高レベル API）: @DynamoDBTable マッピングを使ったオブジェクト操作。
 *       save / load / scan 等の基本操作に使用する。</li>
 *   <li>AmazonDynamoDB（低レベル API）: DynamoDBMapper がサポートしない操作に使用する。
 *       本クラスでは条件付き UpdateItem（UpdateExpression + ConditionExpression）に使用する。</li>
 * </ul>
 */
@Repository
@RequiredArgsConstructor
public class UserRepository {

    /** 高レベル API: @DynamoDBTable マッピングを使ったオブジェクト操作 */
    private final DynamoDBMapper dynamoDBMapper;

    /** 低レベル API: UpdateItem 等、DynamoDBMapper でカバーできない操作に使用 */
    private final AmazonDynamoDB amazonDynamoDB;

    /**
     * テーブル名は DynamoDbConfig の TableNameOverride と一致させる必要がある。
     * 低レベル API（updateItem）では TableNameOverride が効かないため、
     * 同じプロパティを直接参照する。
     */
    @Value("${aws.dynamodb.table-name:users}")
    private String tableName;

    /**
     * ユーザーを新規保存する。
     *
     * <p>DynamoDBSaveExpression に {@code attribute_not_exists(email)} を指定することで、
     * 同一パーティションキーが存在する場合に DynamoDB が ConditionalCheckFailedException をスローする。
     * これにより二重登録をアトミックに防ぐ（Python 版の put_item の ConditionExpression と同等）。
     *
     * @throws ConditionalCheckFailedException 同一 email が既に存在する場合（呼び出し元でキャッチする）
     */
    public void save(UserItem item) {
        // withExpected: SDK v1 の条件付き書き込みの古い記法（ConditionExpression より互換性が高い）。
        // exists(false) は「email 属性が存在しない場合のみ書き込む」を意味し、
        // ConditionExpression の attribute_not_exists(email) と同等。
        Map<String, ExpectedAttributeValue> expected = new HashMap<>();
        expected.put("email", new ExpectedAttributeValue().withExists(false));

        DynamoDBSaveExpression saveExpression = new DynamoDBSaveExpression()
                .withExpected(expected);
        dynamoDBMapper.save(item, saveExpression);
    }

    /**
     * メールアドレスでユーザーを取得する。
     *
     * <p>DynamoDBMapper.load はパーティションキー（email）で 1 件取得する。
     * 対象アイテムが存在しない場合は null を返すため、Optional でラップして返す。
     */
    public Optional<UserItem> findByEmail(String email) {
        // load(クラス, PK値): GetItem に相当する操作
        UserItem item = dynamoDBMapper.load(UserItem.class, email);
        return Optional.ofNullable(item);
    }

    /**
     * ユーザー一覧を取得する（最大 limit 件）。
     *
     * <p>DynamoDB の Scan 操作を使用する。Scan はテーブル全体を読み込む低効率な操作だが、
     * 本サンプルではインデックス設計を省くため採用している。
     *
     * <p>DynamoDBScanExpression.withLimit() は DynamoDB が 1 回のリクエストで評価する最大アイテム数を指定する。
     * フィルター条件がない場合は評価数 = 返却数になる。
     * scanPage() で 1 ページ分のみ取得する（scan() は遅延ロードで全件フェッチするため使わない）。
     */
    public List<UserItem> findAll(int limit) {
        DynamoDBScanExpression scanExpression = new DynamoDBScanExpression()
                .withLimit(limit);
        // scanPage: 1 ページ分のみ取得する（Python の scan(Limit=limit) と同等）
        ScanResultPage<UserItem> page = dynamoDBMapper.scanPage(UserItem.class, scanExpression);
        return page.getResults();
    }

    /**
     * ユーザーの name と updated_at を更新する。
     *
     * <p>DynamoDBMapper.save は全属性を上書きする（全フィールドが PUT される）ため、
     * 部分更新（name だけ変更し created_at は変えない）には低レベルの updateItem を使用する。
     *
     * <p>ConditionExpression: {@code attribute_exists(email)} により、
     * 対象ユーザーが存在しない場合に ConditionalCheckFailedException がスローされる。
     * 存在チェックと更新をアトミックに実行できる（Python の update_item の ConditionExpression と同等）。
     *
     * @return 更新後の UserItem（DynamoDB の ReturnValues=ALL_NEW から取得）
     * @throws ConditionalCheckFailedException 指定 email が存在しない場合（呼び出し元でキャッチする）
     */
    public UserItem update(String email, String name, String updatedAt) {
        // GetItem / PutItem の Key 指定: パーティションキーを Map<String, AttributeValue> で渡す
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("email", new AttributeValue(email));

        // "name" は DynamoDB の予約語のため、ExpressionAttributeNames でエイリアス "#n" を使う
        Map<String, String> expressionNames = new HashMap<>();
        expressionNames.put("#n", "name");

        // ":name" / ":updatedAt" はプレースホルダー（ExpressionAttributeValues で実値を渡す）
        Map<String, AttributeValue> expressionValues = new HashMap<>();
        expressionValues.put(":name", new AttributeValue(name));
        expressionValues.put(":updatedAt", new AttributeValue(updatedAt));

        UpdateItemRequest request = new UpdateItemRequest()
                .withTableName(tableName)
                .withKey(key)
                // SET 句: 指定した属性だけを更新する（他の属性は変更しない）
                .withUpdateExpression("SET #n = :name, updated_at = :updatedAt")
                // attribute_exists(email): email が存在しない場合はエラーにする（存在チェック）
                .withConditionExpression("attribute_exists(email)")
                .withExpressionAttributeNames(expressionNames)
                .withExpressionAttributeValues(expressionValues)
                // ALL_NEW: 更新後のアイテム全体を返す（UserItem への変換に必要）
                .withReturnValues(ReturnValue.ALL_NEW);

        UpdateItemResult result = amazonDynamoDB.updateItem(request);

        // UpdateItemResult の Attributes（Map<String, AttributeValue>）を UserItem に手動変換する
        // DynamoDBMapper.marshallIntoObject() を使う方法もあるが、依存が増えるため手動変換を採用
        UserItem updated = new UserItem();
        updated.setEmail(result.getAttributes().get("email").getS());
        updated.setName(result.getAttributes().get("name").getS());
        updated.setCreatedAt(result.getAttributes().get("created_at").getS());
        updated.setUpdatedAt(result.getAttributes().get("updated_at").getS());
        return updated;
    }
}