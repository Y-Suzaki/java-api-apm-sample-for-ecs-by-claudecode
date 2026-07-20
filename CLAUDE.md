
# CLAUDE.md — Java バックエンド開発ガイド

## プロジェクト概要
* Spring Boot によるバックエンド REST API を開発する。
* Python 版（`python-api-apm-sample-for-ecs-by-claudecode`）と同等の API 機能を提供する。
* インフラは AWS を利用し、ALB / API Gateway と ECS を利用する。
* インフラコードの管理は CloudFormation Template を利用する。
* **リポジトリはマルチアプリ構成**。Spring Boot アプリケーション・Docker Image・ECS Service は
  アプリごとに分離し、それぞれ独立してビルド・デプロイできるようにする。
  ECS Cluster・ALB・NLB は全アプリで共有する（詳細は「リポジトリ構成（マルチアプリ）」節を参照）。

## リポジトリ構成（マルチアプリ）

`apps/` 配下にアプリごとの独立した Spring Boot プロジェクトを置く。各アプリは自前の
`pom.xml`（`spring-boot-starter-parent` を直接継承）・`Dockerfile` を持ち、コード共有はしない。

| アプリ | ディレクトリ | 役割 | APM | Internet からの入口 |
|---|---|---|---|---|
| `user-company-api` | `apps/user-company-api/` | User API（DynamoDB）+ Company API（MySQL/Aurora） | あり（ADOT Java Agent + CloudWatch Agent サイドカー） | ALB |
| `log-api` | `apps/log-api/` | JSON ログを受け付けて標準出力するだけの最小サンプル（アプリケーション分離の実証用） | なし | API Gateway（REST API） |

**NLB 共有の実現方法**: NLB は L4 でパスベースルーティングができないため、**アプリごとに NLB の
リスナーポートを分ける**（`user-company-api`=80、`log-api`=8081）。NLB オブジェクト自体
（`AWS::ElasticLoadBalancingV2::LoadBalancer`）は共有スタック（`06-shared-cluster-lb.yaml`）が
1つだけ所有し、Listener・TargetGroup はアプリ別スタック（log-api: `07-`、user-company-api: `09-`）が
それぞれ追加する。

**Internet からの入口はアプリごとに異なってよい**。`user-company-api` は ALB の path-pattern
ListenerRule 経由（ALB → NLB → ECS）。`log-api` は API Gateway（REST API）の VPC Link 経由で
NLB に直接到達する（API Gateway → VPC Link → NLB → ECS、ALB は経由しない）。詳細は
「通信経路」節を参照。

アプリを追加する場合:
1. `apps/<new-app>/` に独立した Spring Boot プロジェクトを作成する。
2. `cloudformation/0X-ecs-<new-app>.yaml` を作成し、`06-shared-cluster-lb.yaml` の Export
   （ECS Cluster / SG / NLBArn / NlbPrivateIp / OpenApiBucketName 等）を Import して、そのアプリ専用の
   NLB Listener（他アプリと重複しない新しいポート）・TargetGroup・TaskDefinition・ECS Service を
   定義する。ALB 経由にする場合は ALBListenerArn も Import して ALB TargetGroup/ListenerRule を追加し、
   API Gateway 経由にする場合は VpcLink/RestApi/Deployment/Stage を追加する。
3. `cloudformation/02-ecr.yaml` を `AppName=<new-app>` で追加デプロイする。
4. `scripts/common.sh` の `APPS` 配列と `app_ecs_template()` に追加する。

## 技術スタック
| 用途                   | ライブラリ／ツール                                       |
|----------------------|--------------------------------------------------|
| Web フレームワーク          | Spring Boot 3.5（Spring MVC / Embedded Tomcat）    |
| 言語                   | Java 17                                          |
| ビルドツール               | Maven 3                                          |
| DB クライアント（NoSQL）     | AWS SDK for Java v1（`aws-java-sdk-dynamodb` / DynamoDBMapper） |
| ORM / JPA            | Spring Data JPA（JpaRepository）/ Hibernate 6      |
| DB（リレーショナル）    | MySQL 8.0（ローカル: Docker）/ Amazon Aurora MySQL（AWS） |
| コネクションプール      | HikariCP（Spring Boot 自動構成）                      |
| 外部 HTTP クライアント       | Spring Cloud OpenFeign（`@FeignClient`）            |
| バリデーション              | Jakarta Bean Validation（spring-boot-starter-validation） |
| ボイラープレート削減           | Lombok                                           |
| インフラストラクチャー          | AWS（ALB、ECS Fargate を中心に利用）                     |
| インフラコード管理            | CloudFormation Template                          |
| DB（NoSQL）            | DynamoDB                                         |
| 分散トレーシング SDK          | （後フェーズで追加）                                      |

## ディレクトリ構成
```
/
├── apps/
│   ├── user-company-api/
│   │   ├── src/
│   │   │   └── main/
│   │   │       ├── java/com/example/api/
│   │   │       │   ├── ApiApplication.java          # Spring Boot エントリポイント
│   │   │       │   ├── config/
│   │   │       │   │   ├── AppProperties.java       # @ConfigurationProperties バインド
│   │   │       │   │   ├── DynamoDbConfig.java      # AmazonDynamoDB / DynamoDBMapper Bean 定義
│   │   │       │   │   └── LogApiAsyncConfig.java   # RequestLoggingFilter 専用 ThreadPoolTaskExecutor
│   │   │       │   ├── client/
│   │   │       │   │   ├── IpifyClient.java         # @FeignClient（ipify 外部 HTTP 呼び出し）
│   │   │       │   │   └── LogApiClient.java        # @FeignClient（log-api への内部ログ送信。POST /logs）
│   │   │       │   ├── filter/
│   │   │       │   │   └── RequestLoggingFilter.java # 全リクエスト（/health 除く）を非同期で log-api に転送
│   │   │       │   ├── controller/
│   │   │       │   │   ├── HealthController.java        # GET /health
│   │   │       │   │   ├── UserController.java          # /users CRUD
│   │   │       │   │   ├── CompanyController.java       # /companies CRUD
│   │   │       │   │   └── ConfigurationController.java # GET /configuration
│   │   │       │   ├── service/
│   │   │       │   │   ├── UserService.java             # ユーザー CRUD ビジネスロジック
│   │   │       │   │   └── CompanyService.java          # 会社 CRUD ビジネスロジック
│   │   │       │   ├── repository/
│   │   │       │   │   ├── UserRepository.java          # DynamoDB アクセス層（DynamoDBMapper）
│   │   │       │   │   └── CompanyRepository.java       # JPA アクセス層（JpaRepository）
│   │   │       │   ├── model/
│   │   │       │   │   ├── UserItem.java                # DynamoDB テーブルマッピング（@DynamoDBTable）
│   │   │       │   │   ├── UserResponse.java            # API レスポンス（record）
│   │   │       │   │   ├── UserCreateRequest.java       # 新規作成リクエスト（record）
│   │   │       │   │   ├── UserUpdateRequest.java       # 更新リクエスト（record）
│   │   │       │   │   ├── ConfigurationResponse.java   # /configuration レスポンス（record）
│   │   │       │   │   ├── CompanyEntity.java           # JPA エンティティ（@Entity / @Table）
│   │   │       │   │   ├── CompanyResponse.java         # API レスポンス（record）
│   │   │       │   │   ├── CompanyCreateRequest.java    # 新規作成リクエスト（record）
│   │   │       │   │   └── CompanyUpdateRequest.java    # 更新リクエスト（record）
│   │   │       │   └── exception/
│   │   │       │       ├── UserAlreadyExistsException.java
│   │   │       │       ├── UserNotFoundException.java
│   │   │       │       ├── CompanyAlreadyExistsException.java
│   │   │       │       ├── CompanyNotFoundException.java
│   │   │       │       └── GlobalExceptionHandler.java  # @RestControllerAdvice
│   │   │       └── resources/
│   │   │           └── application.yml
│   │   ├── mysql/init/01_init.sql       # companies テーブル DDL（MySQL 初期化）
│   │   ├── docker-compose.yml           # ローカル開発用（MySQL 8.0 + DynamoDB Local）
│   │   ├── Dockerfile                   # ADOT Java Agent 同梱
│   │   ├── .dockerignore
│   │   └── pom.xml
│   └── log-api/
│       ├── src/
│       │   └── main/
│       │       ├── java/com/example/logapi/
│       │       │   ├── LogApiApplication.java       # Spring Boot エントリポイント
│       │       │   ├── config/AppProperties.java
│       │       │   └── controller/
│       │       │       ├── HealthController.java    # GET /health
│       │       │       └── LogController.java       # POST /logs（JSON ログを受け付けて標準出力）
│       │       └── resources/application.yml
│       ├── openapi.yaml                 # log-api の OpenAPI 3 仕様書（API Gateway Body import 用）
│       ├── Dockerfile                   # APM 計装なしの最小構成
│       ├── .dockerignore
│       └── pom.xml
├── cloudformation/
│   ├── 01-network.yaml
│   ├── 02-ecr.yaml                     # AppName パラメータ化。アプリごとにデプロイ
│   ├── 03-dynamodb.yaml                # user-company-api 専用
│   ├── 05-rds.yaml                     # user-company-api 専用
│   ├── 06-shared-cluster-lb.yaml       # 共有: ECS Cluster / ALB(+Listener) / NLB / SG群 / TaskExecutionRole / OpenAPI 用 S3 バケット
│   ├── 07-ecs-log-api.yaml             # log-api の ECS Service + API Gateway（REST API、公開）+ VPC Link
│   ├── 08-apigw-log-api-internal.yaml  # log-api を VPC 内限定で公開する Private API Gateway（user-company-api 専用の内部呼び出し口）
│   └── 09-ecs-user-company-api.yaml    # user-company-api の ECS Service（ALB 経由）
├── scripts/
│   ├── common.sh          # APPS 配列・app_ecr_stack()/app_ecs_stack() 等のヘルパー
│   ├── build.sh            # 第1引数 <app> 必須
│   ├── deploy-infra.sh     # 共有インフラ（01,02×アプリ数,03,05,06）をデプロイ
│   ├── deploy-service.sh   # 第1引数 <app> 必須
│   ├── deploy-all.sh
│   ├── delete-all.sh
│   ├── local-infra.sh
│   └── local-run.sh        # 第1引数 <app> 必須
├── .gitignore
└── CLAUDE.md
```

## バックエンド API の機能

### User API（DynamoDB）
Python 版と同等のエンドポイントを実装する。

| メソッド   | パス              | 説明                                   |
|--------|-----------------|--------------------------------------|
| GET    | /health         | ALB ヘルスチェック。`{"status":"ok","env":"..."}` を返す |
| POST   | /users          | ユーザー新規作成（201 Created）                |
| GET    | /users          | ユーザー一覧取得（クエリパラメータ `limit`、デフォルト 50、最大 100） |
| GET    | /users/{email}  | ユーザー詳細取得                             |
| PUT    | /users/{email}  | ユーザー更新（`name` のみ）                   |
| GET    | /configuration  | 外部 HTTP API（ipify）を呼び出してグローバル IP を返す |

### Company API（MySQL / Aurora MySQL）

| メソッド   | パス               | 説明                                      |
|--------|------------------|-----------------------------------------|
| POST   | /companies       | 会社新規作成（201 Created）                    |
| GET    | /companies       | 会社一覧取得（クエリパラメータ `limit`、デフォルト 50、最大 100） |
| GET    | /companies/{id}  | 会社詳細取得                                  |
| PUT    | /companies/{id}  | 会社更新（部分更新: null フィールドはスキップ）            |
| DELETE | /companies/{id}  | 会社削除（204 No Content）                    |

### /configuration の目的
外部 HTTP 呼び出し（ipify.org）のサンプルエンドポイント。ECS タスクが Private Subnet 経由で
インターネット接続できること（NAT Gateway → EIP）を副次的に確認するために用意している。
後フェーズで Feign Client の自動計装が X-Ray サービスマップに外部 HTTP ノードとして現れることを
確認するためにも使う。

### Log API（`apps/log-api/`。アプリケーション分離の最低限のサンプル）
DB・外部 API 依存を持たない、JSON 形式のログを受け付けて標準出力するだけの単機能アプリ。
Internet からの入口は ALB ではなく **API Gateway（REST API）**（詳細は「通信経路」節）。

| メソッド | パス | 説明 |
|--------|------|------|
| GET | /health | ALB/NLB ヘルスチェック。`{"status":"ok","env":"..."}` を返す |
| POST | /logs | JSON ボディを受け付けて SLF4J で標準出力する（202 Accepted） |

* `@RequestBody JsonNode` で受ける。フィールド構成は特定のスキーマに固定せず任意の JSON 構造を
  許容するが、`Content-Type: application/json` かつ構文として妥当な JSON であることは必須
  （Content-Type 不一致は 415、不正な JSON は 400 を Spring のデフォルトエラーハンドリングが返す）。
* 標準出力に流すだけなので ECS の awslogs ドライバがそのまま CloudWatch Logs に転送する。
* APM（ADOT/X-Ray）は組み込まない。TaskRole も付与しない（AWS API を呼ばないため）。
* API 仕様は `apps/log-api/openapi.yaml`（OpenAPI 3）で管理し、API Gateway REST API の
  `Body` に `Fn::Transform: AWS::Include` で読み込ませる。仕様書を変更してデプロイスクリプトを
  実行するだけで API Gateway の定義に反映される（詳細は「通信経路」節）。

## アプリケーション層の設計

> 以降のこの節（DynamoDB/MySQL モデル、DTO、エラーレスポンス、Feign Client 等）は
> `apps/user-company-api/` に関する内容。`log-api` は DB・外部依存を持たない最小構成のため対象外。

### 設定（application.yml / 環境変数）
```yaml
spring:
  application:
    name: java-api-apm-sample
  datasource:
    url: ${MYSQL_URL:jdbc:mysql://localhost:3306/sampledb?serverTimezone=UTC&characterEncoding=UTF-8}
    username: ${MYSQL_USER:appuser}
    password: ${MYSQL_PASSWORD:apppassword}
    driver-class-name: com.mysql.cj.jdbc.Driver
    hikari:
      maximum-pool-size: 10
      minimum-idle: 2
      connection-timeout: 30000
      idle-timeout: 600000
  jpa:
    hibernate:
      ddl-auto: validate          # スキーマは SQL 初期化スクリプトで管理。Hibernate は検証のみ
    open-in-view: false

app:
  env: ${APP_ENV:local}

aws:
  region: ${AWS_REGION:ap-northeast-1}
  dynamodb:
    table-name: ${DYNAMODB_USERS_TABLE:users}
    endpoint-url: ${DYNAMODB_ENDPOINT_URL:}   # ローカル DynamoDB Local 用。本番は空
```

**ローカル開発時の環境変数（IDE 向け）:**

| 変数名                  | 値                                                                             |
|-----------------------|-------------------------------------------------------------------------------|
| MYSQL_URL             | `jdbc:mysql://localhost:3306/sampledb?serverTimezone=UTC&characterEncoding=UTF-8` |
| MYSQL_USER            | `appuser`                                                                     |
| MYSQL_PASSWORD        | `apppassword`                                                                 |
| DYNAMODB_ENDPOINT_URL | `http://localhost:8000`                                                       |
| DYNAMODB_USERS_TABLE  | `users`                                                                       |
| AWS_ACCESS_KEY_ID     | `dummy`                                                                       |
| AWS_SECRET_ACCESS_KEY | `dummy`                                                                       |
| APP_ENV               | `local`                                                                       |

### DynamoDB モデル（UserItem）
AWS SDK v1 の `@DynamoDBTable` / `@DynamoDBHashKey` / `@DynamoDBAttribute` を使った DynamoDBMapper のマッピング。
Partition Key は `email`（メールアドレス）。

| DynamoDB 属性  | Java 型    | 説明             |
|--------------|----------|----------------|
| email（PK）   | String   | メールアドレス（一意キー） |
| name         | String   | 表示名            |
| created_at   | String   | ISO-8601 UTC 文字列 |
| updated_at   | String   | ISO-8601 UTC 文字列 |


### MySQL モデル（CompanyEntity）
`@Entity` / `@Table(name = "companies")` を使った Spring Data JPA のマッピング。PK は `id`（BIGINT AUTO_INCREMENT）。
スキーマは `mysql/init/01_init.sql` で定義し、Hibernate は `ddl-auto: validate` で検証のみ行う。

| カラム        | Java 型          | 説明                                |
|-------------|----------------|-----------------------------------|
| id（PK）     | Long           | 自動採番                              |
| name        | String         | 会社名（UNIQUE, NOT NULL, 200 字以内）   |
| industry    | String         | 業種（NULL 可）                        |
| email       | String         | 代表メールアドレス（NULL 可）                |
| phone       | String         | 電話番号（NULL 可）                      |
| address     | String         | 住所（NULL 可）                        |
| created_at  | LocalDateTime  | `@CreationTimestamp` で自動設定        |
| updated_at  | LocalDateTime  | `@UpdateTimestamp` で自動更新          |

MySQL の `DATETIME(6)` は Hibernate 6 が `LocalDateTime` をマッピングする型。init SQL と合わせる必要がある。

### Company API DTO（Java Records）
```java
// 新規作成リクエスト
public record CompanyCreateRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 100) String industry,
        @Email @Size(max = 255) String email,
        @Size(max = 50) String phone,
        @Size(max = 500) String address) {}

// 更新リクエスト（全フィールド null 可; null フィールドは既存値を保持）
public record CompanyUpdateRequest(
        @Size(max = 200) String name,
        @Size(max = 100) String industry,
        @Email @Size(max = 255) String email,
        @Size(max = 50) String phone,
        @Size(max = 500) String address) {}
```

### API DTO（Java Records）
```java

// レスポンス
public record UserResponse(String email, String name, Instant createdAt, Instant updatedAt) {}

// 新規作成リクエスト（@NotBlank / @Email で検証）
public record UserCreateRequest(@Email @NotBlank String email, @NotBlank @Size(max=100) String name) {}

// 更新リクエスト（name のみ）
public record UserUpdateRequest(@NotBlank @Size(max=100) String name) {}
```

### エラーレスポンス
`@RestControllerAdvice`（GlobalExceptionHandler）で統一するHTTP ステータスとボディを返す。

| 例外                             | HTTP ステータス            |
|--------------------------------|------------------------|
| UserAlreadyExistsException     | 409 Conflict           |
| UserNotFoundException          | 404 Not Found          |
| CompanyAlreadyExistsException  | 409 Conflict           |
| CompanyNotFoundException       | 404 Not Found          |
| MethodArgumentNotValidException | 400 Bad Request       |
| その他                           | 500 Internal Server Error |

### ポート
Spring Boot デフォルトの **8080** を使用する（Python 版の 8000 とは異なる）。

### DynamoDB クライアント初期化方針
* `AmazonDynamoDB` クライアントと `DynamoDBMapper` の Bean は `DynamoDbConfig` で定義し、アプリ起動時に 1 度だけ生成する。
* `DYNAMODB_ENDPOINT_URL` が空の場合はデフォルトのリージョンエンドポイントを使用する。
* リトライ設定: `ClientConfiguration` で `withMaxErrorRetry(5)` を指定する。
* 条件付き書き込み（重複防止）は `DynamoDBSaveExpression` に `attribute_not_exists(email)` を設定する。
* 条件付き更新（存在チェック）は `DynamoDBSaveExpression` / 低レベル UpdateItem 呼び出しで `attribute_exists(email)` を設定する。

### 外部 HTTP クライアント（Feign Client）
* Spring Cloud OpenFeign（`spring-cloud-starter-openfeign`）を使用する。
* Spring Cloud BOM バージョンは **`2025.0.0`**（Spring Boot 3.5.x と対応する Newport リリース）。
  2024.0.x (Moorgate) は Spring Boot 3.4.x までのサポートのため使用不可。
* `ApiApplication` に `@EnableFeignClients` を付与する。
* `IpifyClient` に `@FeignClient(name = "ipify", url = "${external.ipify.url}")` を定義する。
* ipify URL はデフォルト `https://api.ipify.org`（application.yml の `external.ipify.url`）。
* タイムアウトは application.yml の `spring.cloud.openfeign.client.config.ipify` で設定する（接続 3s、読み取り 5s）。
* 外部 API 呼び出し失敗時は `502 Bad Gateway` を返す。
* `LogApiClient` に `@FeignClient(name = "log-api", url = "${internal.log-api.url}")` を定義する
  （log-api の `POST /logs` を呼ぶ）。URL は環境変数 `LOG_API_URL`（08-apigw-log-api-internal.yaml
  の invoke URL）で上書きされる。`RequestLoggingFilter` から fire-and-forget で呼ばれるため、
  タイムアウトは短め（接続 2s、読み取り 3s、`spring.cloud.openfeign.client.config.log-api`）にし、
  失敗しても例外を呼び出し元に伝播させない（本来のリクエスト処理には影響させない）。

## Docker Image の方針

各アプリの `Dockerfile` は `apps/<app>/` 配下にあり、ビルドコンテキストも `apps/<app>/`
（`scripts/build.sh <app>` が `docker build apps/<app>` する）。イメージはアプリごとに別の
ECR リポジトリへプッシュする。

### マルチステージビルド
```
Stage 1（builder）: amazoncorretto:17-al2023 → Maven をインストールして mvn package でファット JAR を生成
Stage 2（runtime）: amazoncorretto:17-al2023 → JAR のみコピー、非 root ユーザーで起動
```

* ベースイメージは **Amazon Corretto 17**（`amazoncorretto:17-al2023`）を両ステージで統一する。
* builder ステージでは `dnf install -y maven` で Maven をインストールする。
* ビルドキャッシュ効率化のため、`pom.xml` を先にコピーして `mvn dependency:go-offline -q` を実行してから
  `src/` をコピーして `mvn package -DskipTests -q` を実行する。
* 非 root ユーザー（`app`）で動作させる（`useradd -r app`）。
* `EXPOSE 8080`、起動コマンドは `java -Dspring.output.ansi.enabled=NEVER -jar app.jar`
  （user-company-api は `-javaagent:/app/aws-opentelemetry-agent.jar` を追加、log-api には付けない）。
* ECS の awslogs ドライバへ stdout を流す前提。`-Dspring.output.ansi.enabled=NEVER` で
  ANSI カラーコードを除去してログを可読にする。

### .dockerignore
ビルドコンテキストがアプリディレクトリ単位になったため、`target/`、`.git/`、`*.md`、`.idea/`、
`*.iml`、`.last_image_uri` などビルド不要なものを除外する（`scripts/`/`cloudformation/` は
兄弟ディレクトリのためコンテキスト外。除外指定は不要）。

## インフラストラクチャー（AWS）の詳細

Python 版と共通のネットワーク設計を踏襲する。

### CloudFormation スタック構成
| ファイル           | スタック名                       | 作成リソース                                     |
|----------------|-----------------------------|--------------------------------------------|
| 01-network.yaml | `${ProjectName}-network`    | VPC、Public/Application/Private サブネット（2AZ、3層構成）、IGW、NAT GW（1AZ）、DynamoDB/S3 VPC Endpoint |
| 02-ecr.yaml    | `${ProjectName}-ecr-<app>`（アプリごと） | ECR リポジトリ（`AppName` パラメータでアプリごとにデプロイ） |
| 03-dynamodb.yaml | `${ProjectName}-dynamodb`   | DynamoDB テーブル（users、PK: email）。user-company-api 専用 |
| 05-rds.yaml    | `${ProjectName}-rds`        | Aurora MySQL。user-company-api 専用             |
| 06-shared-cluster-lb.yaml | `${ProjectName}-shared` | ECS クラスター、ALB（+デフォルトListener、`AlbCertificateArn` 設定時は HTTPS:443+mTLS Trust Store と HTTP→HTTPS リダイレクトも）、内部 NLB、API Gateway VPC Link、SG群、TaskExecutionRole、OpenAPI 仕様書ステージング用 S3 バケット。**全アプリ共有** |
| 07-ecs-log-api.yaml | `${ProjectName}-ecs-log-api` | タスク定義、Fargate サービス、NLB Listener(:8081)/TargetGroup、API Gateway（REST API、公開）+ Deployment/Stage（TaskRole なし、ALB は経由しない。VpcLink は06が所有するものを Import） |
| 08-apigw-log-api-internal.yaml | `${ProjectName}-apigw-log-api-internal` | Interface VPC Endpoint（execute-api）+ Private REST API Gateway（07 の ECS/NLB リソースには一切触れず、同じ NLB Listener(:8081) への別の入口を追加するだけ。VpcLink は06が所有するものを Import） |
| 09-ecs-user-company-api.yaml | `${ProjectName}-ecs-user-company-api` | TaskRole、タスク定義、Fargate サービス、NLB Listener(:80)/TargetGroup、ALB TargetGroup/ListenerRule |

### ネットワーク設計（01-network.yaml）
* リージョン: **ap-northeast-1**（東京）
* VPC CIDR: `10.20.0.0/16`
* 3 層構成（Public / Application / Private）、各層 2AZ（1a / 1c）、サブネットマスクは **/20**
  * Public: `10.20.0.0/20`（1a）、`10.20.16.0/20`（1c）— ALB を配置
  * Application: `10.20.32.0/20`（1a）、`10.20.48.0/20`（1c）— 内部 NLB を配置
  * Private: `10.20.64.0/20`（1a）、`10.20.80.0/20`（1c）— ECS Fargate タスク / Aurora MySQL を配置
* NAT Gateway: コスト削減のため **単一 AZ（1a）のみ**。Application / Private の各ルートテーブルから同じ NAT Gateway を利用
* DynamoDB / S3 へは Gateway 型 VPC Endpoint 経由で接続（NAT 課金回避）。Private・Application 両方のルートテーブルに関連付け済み

### ECS Fargate 設計（07-ecs-log-api.yaml / 09-ecs-user-company-api.yaml）
* CPU/Memory: user-company-api は **512/1024**（CloudWatch Agent サイドカー常駐分を含む）、
  log-api は **256/512**（サイドカーなしの最小構成）
* コンテナポート: **8080**（全アプリ共通）
* ヘルスチェックパス: `/health`
* サービス: PrivateSubnet に配置、PublicIP 無効

### 通信経路
Internet からの入口はアプリごとに異なる。**user-company-api は ALB 経由**、**log-api は API Gateway
（REST API）経由**で、いずれも共有 NLB を通って ECS へ到達する。

#### user-company-api（ALB → NLB → ECS）
* `Internet → ALB（Public） → NLB（Application、internal） → ECS（Private）` の 3 ホップ構成。
* **ALB**: Public Subnet に配置、internet-facing。デフォルトは HTTP:80 で受信し、パスごとにターゲット
  グループへ転送する（`06-shared-cluster-lb.yaml` が所有。DefaultAction は 404 固定応答、アプリ別の
  ListenerRule は各アプリのスタックが追加する。**log-api の ListenerRule は存在しない**＝ALB 経由では
  log-api に到達できない）。`AlbCertificateArn` パラメータ設定時は HTTPS:443 + mTLS(Verify) に切り替わり、
  HTTP:80 は 443 へのリダイレクト専用になる（詳細は「ALB（user-company-api）」節を参照）。ListenerRule は
  `ALBListenerArn` Export（論理 ID・Export 名は不変）に対して追加するため、この切り替えでアプリ別スタック
  側の変更は不要。
* **NLB**: Application Subnet に配置、**Scheme: internal**（インターネットからのアクセス不可、IGW ルートなし）。
  **全アプリで共有**（`06-shared-cluster-lb.yaml` が所有）だが、L4 でパスベースルーティングができないため
  **アプリごとに Listener のポートを分ける**（user-company-api: TCP:80、log-api: TCP:8081）。
  各 Listener は対応するアプリのスタック（log-api: 07、user-company-api: 09）が追加し、
  そのアプリの ECS タスク（コンテナポート 8080）へ転送する。
* **ALB → NLB の接続方法**: ALB のターゲットグループには NLB を直接指定する仕組み（"nlb" ターゲットタイプ）が
  存在しない。そのため NLB の `SubnetMappings` に `PrivateIPv4Address` で固定プライベート IP
  （`NlbPrivateIp1a` = `10.20.32.10`、`NlbPrivateIp1c` = `10.20.48.10`、いずれも Application Subnet の
  CIDR 内。`06-shared-cluster-lb.yaml` の Output として export）を割り当て、user-company-api の ALB
  ターゲットグループ（`AlbToNlbTargetGroup`、IP タイプ）にその固定 IP を**NLB Listener(:80)**で
  静的ターゲットとして登録することで疑似的に ALB → NLB の転送を実現する。
* **セキュリティグループ**: ALB SG（0.0.0.0/0:80 許可）→ NLB SG（ALB SG からの :80 のみ許可）→
  ECS SG（NLB SG からの :8080 のみ許可）の順に絞り込む。

#### log-api（API Gateway → VPC Link → NLB → ECS）
* `Internet → API Gateway（REST API） → VPC Link → NLB（Application、internal） → ECS（Private）` の
  4 ホップ構成。**ALB は経由しない**（`07-ecs-log-api.yaml` は ALB ListenerRule を持たない）。
* **VPC Link 採用理由（制約）**: REST API（v1）を使用する制約があるため、REST API 用の
  `AWS::ApiGateway::VpcLink` を利用する。HTTP API（v2）の VpcLink と異なり、Subnet/SecurityGroup では
  なく **NLB の ARN を直接 `TargetArns` に指定**するだけでよく、新規サブネット/ENI 管理は不要。
  NLB は VPC Endpoint Service（VpcLink の実体）を1つしか持てないため、VpcLink は NLB 自体の性質
  として **`06-shared-cluster-lb.yaml` が単一のものを所有**する（`07-ecs-log-api.yaml` や
  `08-apigw-log-api-internal.yaml` のようなアプリ別スタックには置かない）。
* **API 仕様と CloudFormation の連携**: `apps/log-api/openapi.yaml`（OpenAPI 3）を
  `AWS::ApiGateway::RestApi` の `Body` プロパティに `Fn::Transform: AWS::Include` で読み込ませる。
  Include は S3 上のファイル（`06-shared-cluster-lb.yaml` が所有する `OpenApiArtifactBucket` に
  デプロイスクリプトがアップロードする）をテンプレート展開時に差し込むため、openapi.yaml 内で
  `connectionId` を `Fn::ImportValue`（`${ProjectName}-vpclink-id`、06 の Export）で直接参照する。
  これにより 07 と 08 のどちらから Include されても、openapi.yaml は無変更のまま同じ VpcLink を
  指せる。
  * `AWS::ApiGateway::Deployment` は不変のスナップショットで、プロパティが変わらないと
    CloudFormation が更新を検知できない。そのため openapi.yaml の sha256 ハッシュ
    （デプロイスクリプトが計算）を `Deployment` の `Description` に埋め込み、**仕様書の内容が
    変わるたびに新しい Deployment が作られる**ようにしている（`OpenApiSpecHash` パラメータ）。
* **セキュリティグループ**: VPC Link は PrivateLink（VPC Endpoint Service）経由で NLB の ENI に
  直接接続する。この経路のトラフィックは送信元 IP が呼び出し元（API Gateway）側 VPC のアドレスに
  変換されるため、こちら側の VPC CIDR では絞り込めない（実測: `CidrIp: 10.20.0.0/16` では接続が
  サイレントにドロップされ、API Gateway 側で 500 "internal error" になることを確認済み）。
  そのため NLB SG の 8081 番ポートは `CidrIp: 0.0.0.0/0` としている。NLB は internal スキームで
  IGW ルートを持たないため、これによりインターネットから到達可能になるわけではない。

#### user-company-api → log-api（内部ログ送信専用。Private API Gateway → VPC Link → NLB → ECS）
* `user-company-api ECS（Private） → Interface VPC Endpoint（execute-api） → Private REST API Gateway
  → VPC Link → NLB（Application、internal、log-api の既存 Listener :8081/TargetGroup を再利用）
  → log-api ECS（Private）` の経路。Internet からは到達できない、VPC 内限定の経路。
* **用途**: `RequestLoggingFilter`（user-company-api）が全リクエスト（/health 除く）の
  method/path/body を非同期・fire-and-forget で log-api の `POST /logs` に転送するために使う。
  失敗しても user-company-api 本来のレスポンスには影響させない。
* **08-apigw-log-api-internal.yaml が単独で所有する**。log-api の ECS/NLB Listener(:8081)/TargetGroup
  （07 が所有）には一切変更を加えず、同じバックエンドに対する別の「入口」を追加するだけ。
  Body は `apps/log-api/openapi.yaml` を 07 と全く同じ内容のまま再利用する（論理名 `VpcLink` と
  パラメータ `NlbDnsName` を 09 内でも用意しているため、別ファイルを作る必要がない）。
* **VpcLink は 06 が単一のものを所有し、07/08 はどちらも Import するだけ**: NLB は VPC Endpoint
  Service（＝VpcLink）を1つしか持てず、同じ NLB を指す2つ目の `AWS::ApiGateway::VpcLink` を
  作成しようとすると `Failed to stabilize Vpc Link ... NLB is already associated with another VPC
  Endpoint Service` で失敗する（実際に遭遇済み）。当初は 07（旧 08）が VpcLink を所有し、08（旧 09）
  がそれを Parameter 経由で借用する設計にしていたが、この非対称な依存関係自体が不要な結合だった
  ため、VpcLink を NLB 自体の性質として 06 に移し、07/08 とも `apps/log-api/openapi.yaml` 内で
  `Fn::ImportValue`（`${ProjectName}-vpclink-id`）により直接参照する形に整理した。これにより
  08 は 07 の Output に一切依存しなくなり、07 と 08 は互いに独立してデプロイできる
  （`scripts/deploy-service.sh` は log-api ブランチの中で 07 → 08 の順に呼ぶが、それは操作上の
  都合であって CloudFormation 上の依存関係ではない）。
* **Private REST API（EndpointConfiguration: PRIVATE）**: Interface VPC Endpoint
  （`com.amazonaws.<region>.execute-api`、PrivateDnsEnabled、Private Subnet に配置）経由でのみ
  到達できる。認証は行わず、`Policy`（リソースポリシー）で `aws:sourceVpce` をこの VPC Endpoint に
  限定することでアクセス制御する（ALB/公開 API Gateway と同じ「認証不要」方針を踏襲しつつ、
  ネットワーク境界だけで内部限定にする）。
* **NLB SG**: 8081 番ポートは log-api の公開経路（VPC Link 経由の PrivateLink 接続）向けに既に
  `0.0.0.0/0` で開放済み（送信元 IP が呼び出し元 VPC のアドレスに変換されるため VPC CIDR で
  絞れないという既存の制約。「log-api（API Gateway → VPC Link → NLB → ECS）」節を参照）。
  この内部経路も同じ VPC Link/PrivateLink の仕組みを使うため、NLB SG の追加変更は不要。
* **user-company-api ECS タスクの環境変数**: `LOG_API_URL`（08 の `ApiInvokeUrl` Export を
  `Fn::ImportValue` で取り込む。09 が 08 の Export を Import するため、初回デプロイ時は
  08 を 09 より先にデプロイする必要がある。「デプロイスクリプトの方針」節を参照）。

#### 共通
* コンテナポート 8080 は全アプリ共通のため、ECS SG（NLB SG からの :8080 のみ許可）は全アプリで共有する。

#### ECS タスクの環境変数
| 変数名                  | 値                                |
|-----------------------|----------------------------------|
| APP_ENV               | prod                             |
| AWS_REGION            | `!Ref AWS::Region`               |
| DYNAMODB_USERS_TABLE  | `!Ref UsersTableName`（デフォルト: users）|
| DYNAMODB_ENDPOINT_URL | （未設定）                           |
| LOG_API_URL           | 08-apigw-log-api-internal.yaml の `ApiInvokeUrl` Export（user-company-api のみ） |

#### IAM
* **TaskExecutionRole**: `AmazonECSTaskExecutionRolePolicy`（イメージプル・ログ書き込み）
* **TaskRole**: DynamoDB CRUD ポリシー（GetItem / PutItem / UpdateItem / DeleteItem / Query / Scan）
  * APM 追加フェーズでは `AWSXRayDaemonWriteAccess` / `CloudWatchAgentServerPolicy` を追加する

#### ALB（user-company-api）
* スキーム: internet-facing
* 認証不要（mTLS 未設定時）
* ヘルスチェック: `GET /health`、200 OK（ALB → ターゲットの経路はリスナーを経由しないため mTLS の影響を受けない）

#### ALB の mTLS（クライアント証明書認証。オプトイン）
* **目的**: おれおれ Root CA を使ったクライアント証明書認証を ALB の HTTPS リスナーで検証する
  （`AWS::ElasticLoadBalancingV2::TrustStore` + Listener の `MutualAuthentication`）。
* **有効化フロー**（`06-shared-cluster-lb.yaml` の `AlbCertificateArn` パラメータがトリガー。
  空文字（デフォルト）の間は mTLS 無効・HTTP:80 のまま。ALB の DNS 名が確定してからでないと
  自己署名サーバー証明書の CN/SAN を決められないため、初回の `deploy-infra.sh` では必ず空）:
  1. `scripts/generate-mtls-certs.sh` — openssl で Root CA・ALB サーバー証明書（CN/SAN = ALB DNS 名）・
     クライアント証明書を生成し、ローカルの `certs/`（`.gitignore` 済み、秘密鍵を含むためコミットしない）
     に出力する。実行のたびに Root CA から作り直すため、既存の証明書は全て失効する。
  2. `scripts/deploy-mtls.sh` — Root CA を `OpenApiArtifactBucket`（`06` 所有の共有 S3 バケット、
     `mtls/root-ca-bundle.pem`）にアップロードし、ALB サーバー証明書を `aws acm import-certificate`
     で ACM にインポートしたうえで、その ARN を `AlbCertificateArn` に渡して `06` を再デプロイする。
     新規デプロイ専用の一度きりの手順として想定しており、既存環境での証明書ローテーション等は
     考慮していない（再度有効化し直す場合は generate → deploy を再実行すればよい）。
* **`06-shared-cluster-lb.yaml` 側の切り替え**（`Condition: HasAlbCertificate`）:
  * `ALBTrustStore`（`AWS::ElasticLoadBalancingV2::TrustStore`）を追加作成。CA バンドルは S3 の
    `mtls/root-ca-bundle.pem`（TrustStore は S3 オブジェクトが存在しないと作成できないため、
    Root CA のアップロードが CloudFormation デプロイより先に必要）。
  * 既存の `ALBListener`（論理 ID・Export 名は不変）が HTTP:80 → HTTPS:443 に切り替わり、
    `Certificates`（ACM 証明書）と `MutualAuthentication`（`Mode: verify`、`TrustStoreArn`）が付与される。
  * 新規 `ALBListenerHttpRedirect`（HTTP:80 → HTTPS:443 の 301 リダイレクト専用）を追加。
  * `ALBSecurityGroup` に 443 番ポート（`0.0.0.0/0`）のインバウンドを追加（80 番は維持）。
* **動作確認**: クライアント証明書を持たないアクセスは TLS ハンドシェイクで拒否される（Verify モード）。
  `curl https://<ALB DNS 名>/health --cacert certs/root-ca.crt --cert certs/client.crt --key certs/client.key`

#### API Gateway（log-api）
* REST API（EndpointConfiguration: REGIONAL）、認証不要
* バックエンドは VPC Link 経由の NLB（HTTP_PROXY 統合）。ヘルスチェック自体は NLB Listener の
  TargetGroup（`HealthCheckPath: /health`）が行う

### CloudFormation 命名規則
* `ProjectName` パラメータのデフォルト: **`java-apm-sample`**
* リソース名: `${ProjectName}-<resource>` 形式
* スタック間参照: `Fn::ImportValue` を使う（Export/Import で疎結合）

## デプロイスクリプトの方針

### スクリプト一覧
| ファイル                      | 役割                                          |
|---------------------------|---------------------------------------------|
| scripts/common.sh          | 共通環境変数・ヘルパ関数（他スクリプトから source）。`APPS` 配列、`app_ecr_stack()`/`app_ecs_stack()`/`app_ecs_template()`/`app_dir()`/`validate_app_name()` を提供 |
| scripts/build.sh `<app>`   | 指定アプリの Docker イメージビルド → そのアプリの ECR へプッシュ |
| scripts/deploy-infra.sh    | 共有インフラ（01, 02×アプリ数, 03, 05, 06）をデプロイ    |
| scripts/deploy-service.sh `<app>` | 指定アプリの ECS スタック（log-api: 07、user-company-api: 09）をデプロイ → 強制デプロイ |
| scripts/deploy-all.sh      | deploy-infra.sh の後、全アプリで build.sh/deploy-service.sh をループ実行 |
| scripts/generate-mtls-certs.sh | ALB mTLS 用のおれおれ Root CA・ALB サーバー証明書・クライアント証明書を openssl で生成（`certs/` へ出力。`06` デプロイ済みが前提） |
| scripts/deploy-mtls.sh     | Root CA を S3 へアップロード・サーバー証明書を ACM へインポートし、`AlbCertificateArn` 付きで `06` を再デプロイして ALB mTLS を有効化 |
| scripts/delete-all.sh      | 全スタックを逆順で削除（全アプリの ECS/ECR スタックを含む） |
| scripts/local-infra.sh     | user-company-api 用ローカルインフラ起動・停止（MySQL + DynamoDB Local） |
| scripts/local-run.sh `<app>` | ローカル開発用 Spring Boot 起動（user-company-api はインフラ起動も含む） |

`<app>` は `apps/` 配下のディレクトリ名（`user-company-api` または `log-api`）。アプリを追加する場合は
`common.sh` の `APPS` 配列と `app_ecs_template()` の case 分岐を更新する。

### 共通設定（common.sh）
```bash
export PROJECT_NAME="${PROJECT_NAME:-java-apm-sample}"
export AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION:-ap-northeast-1}"

# アプリ一覧（apps/ 配下のディレクトリ名と一致させる）。
# user-company-api（09）が log-api 用の内部 API Gateway スタック（08）の Export を Import するため、
# log-api を先に処理する必要があり、この順序が deploy-all.sh のループ順を決める。
APPS=(log-api user-company-api)

# Stack 名（アプリ非依存の共有スタックのみ。アプリ別スタック名は app_ecr_stack()/app_ecs_stack() で解決する）
export NETWORK_STACK="${PROJECT_NAME}-network"
export DYNAMODB_STACK="${PROJECT_NAME}-dynamodb"
export RDS_STACK="${PROJECT_NAME}-rds"
export SHARED_STACK="${PROJECT_NAME}-shared"
# log-api を内部（VPC 限定）に公開する Private API Gateway スタック（08-apigw-log-api-internal.yaml）。
# deploy-service.sh の log-api ブランチの中でまとめてデプロイする。
export INTERNAL_APIGW_STACK="${PROJECT_NAME}-apigw-log-api-internal"
export INTERNAL_APIGW_TEMPLATE="${CF_DIR}/08-apigw-log-api-internal.yaml"
```

### AWS 認証
**環境変数**から `AWS_ACCESS_KEY_ID` と `AWS_SECRET_ACCESS_KEY` を取得する。
スクリプト起動時に未設定の場合はエラー終了する（`: "${AWS_ACCESS_KEY_ID:?...}"` ガード）。

### ビルドスクリプト（build.sh）の方針
* 第1引数 `<app>` 必須（`validate_app_name` で検証）。ビルドコンテキストは `apps/<app>/`
* `IMAGE_TAG`: `git rev-parse --short HEAD` を使用（取得できない場合は `date +%Y%m%d%H%M%S`）
* `--platform linux/amd64` で明示的に AMD64 向けにビルドする（ECS Fargate X86_64 対応）
* ビルド完了後、イメージ URI を `apps/<app>/.last_image_uri` に保存する

### デプロイスクリプト（deploy-service.sh）の方針
* 第1引数 `<app>` 必須。`<app>` に応じてテンプレート（log-api: 07、user-company-api: 09）と
  追加パラメータ（user-company-api のみ `MySqlUsername`）を出し分ける
* `IMAGE_URI` が未指定の場合は `apps/<app>/.last_image_uri` から読み込む
* **log-api のみ**: CloudFormation デプロイの前に `apps/log-api/openapi.yaml` を
  共有スタックの `OpenApiBucketName`（S3）へアップロードし、そのファイルの sha256 を
  `OpenApiSpecHash` パラメータとして渡す（`AWS::ApiGateway::Deployment` の更新検知に使う）。
  NLB の DNS 名（`NlbDnsName`）も共有スタックの Output から取得して渡す
* **log-api のみ**: 07（公開 API Gateway）のデプロイ直後に、同じ openapi.yaml（S3 オブジェクト）・
  ハッシュ・NlbDnsName を再利用して 08（内部限定 Private API Gateway）もデプロイする。
  user-company-api（09）が 08 の Export（`LOG_API_URL` 用の invoke URL）を Import するため、
  user-company-api より前にここでデプロイし終えている必要がある（`common.sh` の `APPS` 配列順で
  log-api が先に処理される）
* CloudFormation デプロイ後、イメージのみ更新の場合に変更セットが空になる問題を
  `aws ecs update-service --force-new-deployment` で回避する
* ECS クラスター名・ALB DNS 名は共有スタック（`SHARED_STACK`）の Output から、
  ECS サービス名・（log-api の場合）API Gateway invoke URL はアプリ別スタックの Output から取得する

## APM 追加フェーズ（後日実装）
現フェーズでは以下を実装しない。後フェーズで追加予定。
* OpenTelemetry Java Agent（`-javaagent` による自動計装）
* AWS Distro for OpenTelemetry（ADOT）
* CloudWatch Agent サイドカー（Application Signals）
* ECS タスク定義への OTEL 関連環境変数追加
* TaskRole への `AWSXRayDaemonWriteAccess` / `CloudWatchAgentServerPolicy` 追加
* CPU/Memory: 512/1024 への引き上げ（サイドカー追加後）
