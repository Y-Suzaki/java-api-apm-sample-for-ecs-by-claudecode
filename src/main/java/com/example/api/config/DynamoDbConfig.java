package com.example.api.config;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * DynamoDB クライアント（AmazonDynamoDB）と DynamoDBMapper の Bean 定義。
 *
 * <p>アプリ起動時に 1 度だけ生成し、シングルトン Bean として全コンポーネントで共有する。
 *
 * <h3>ローカル開発（DynamoDB Local 使用）時の起動方法</h3>
 * <pre>
 *   # DynamoDB Local を別ターミナルで起動しておく（Docker の場合）
 *   docker run -p 8000:8000 amazon/dynamodb-local
 *
 *   # テーブルを作成する
 *   aws dynamodb create-table \
 *     --table-name users \
 *     --attribute-definitions AttributeName=email,AttributeType=S \
 *     --key-schema AttributeName=email,KeyType=HASH \
 *     --billing-mode PAY_PER_REQUEST \
 *     --endpoint-url http://localhost:8000
 *
 *   # アプリを起動する（認証情報はダミーでよい）
 *   export AWS_ACCESS_KEY_ID=dummy
 *   export AWS_SECRET_ACCESS_KEY=dummy
 *   export AWS_DEFAULT_REGION=ap-northeast-1
 *   export DYNAMODB_ENDPOINT_URL=http://localhost:8000
 *   mvn spring-boot:run
 * </pre>
 *
 * <h3>本番（ECS Fargate）での動作</h3>
 * <ul>
 *   <li>DYNAMODB_ENDPOINT_URL は未設定（空）にする</li>
 *   <li>ECS タスクロールの IAM 権限で認証する（AWS_ACCESS_KEY_ID 不要）</li>
 * </ul>
 */
@Configuration
public class DynamoDbConfig {

    @Value("${aws.region}")
    private String region;

    /** 空文字の場合はデフォルトのリージョンエンドポイントを使用する */
    @Value("${aws.dynamodb.endpoint-url:}")
    private String endpointUrl;

    @Value("${aws.dynamodb.table-name:users}")
    private String tableName;

    /**
     * AmazonDynamoDB クライアントを生成する。
     *
     * <p>AWS SDK v1 のクライアントは内部でコネクションプールを管理しており、
     * 都度 new するとリソースリークになるため、Bean として 1 インスタンスだけ生成する。
     *
     * <p>認証情報は SDK のデフォルトプロバイダーチェーンで自動解決される:
     * 環境変数（AWS_ACCESS_KEY_ID） → ~/.aws/credentials → EC2/ECS の IAM ロール の順。
     */
    @Bean
    public AmazonDynamoDB amazonDynamoDB() {
        // SDK 共通のネットワーク設定（タイムアウト・リトライ）を一元管理する
        ClientConfiguration clientConfig = new ClientConfiguration()
                .withMaxErrorRetry(5);  // デフォルト(3)より多めに設定してレジリエンスを高める

        AmazonDynamoDBClientBuilder builder = AmazonDynamoDBClientBuilder.standard()
                .withClientConfiguration(clientConfig);

        if (StringUtils.hasText(endpointUrl)) {
            // ローカル DynamoDB Local や LocalStack など、カスタムエンドポイントを使う場合。
            // withEndpointConfiguration を使うと withRegion と排他になるため注意。
            builder.withEndpointConfiguration(
                    new AwsClientBuilder.EndpointConfiguration(endpointUrl, region));
        } else {
            // 本番: リージョンのデフォルト DynamoDB エンドポイントへ接続する
            builder.withRegion(region);
        }

        return builder.build();
    }

    /**
     * DynamoDBMapper を生成する。
     *
     * <p>DynamoDBMapper は @DynamoDBTable / @DynamoDBHashKey 等のアノテーションを読んで
     * Java オブジェクトと DynamoDB アイテムを相互変換する高レベル API。
     *
     * <p>TableNameOverride により、@DynamoDBTable に書いたハードコードされたテーブル名を
     * 環境変数（DYNAMODB_USERS_TABLE）で実行時に上書きできる。
     * これにより同じコードでステージングと本番の別テーブルを使い分けられる。
     */
    @Bean
    public DynamoDBMapper dynamoDBMapper(AmazonDynamoDB amazonDynamoDB) {
        DynamoDBMapperConfig mapperConfig = DynamoDBMapperConfig.builder()
                // テーブル名をコードのアノテーションではなく環境変数の値で決定する
                .withTableNameOverride(
                        DynamoDBMapperConfig.TableNameOverride.withTableNameReplacement(tableName))
                .build();
        return new DynamoDBMapper(amazonDynamoDB, mapperConfig);
    }
}