
# CLAUDE.md — Java バックエンド開発ガイド

## プロジェクト概要
* Spring Boot によるバックエンド REST API を開発する。
* Python 版（`python-api-apm-sample-for-ecs-by-claudecode`）と同等の API 機能を提供する。
* インフラは AWS を利用し、ALB と ECS を利用する。
* インフラコードの管理は CloudFormation Template を利用する。
* **リポジトリはマルチアプリ構成**。Spring Boot アプリケーション・Docker Image・ECS Service は
  アプリごとに分離し、それぞれ独立してビルド・デプロイできるようにする。
  ECS Cluster・ALB・NLB は全アプリで共有する（詳細は「リポジトリ構成（マルチアプリ）」節を参照）。

## リポジトリ構成（マルチアプリ）

`apps/` 配下にアプリごとの独立した Spring Boot プロジェクトを置く。各アプリは自前の
`pom.xml`（`spring-boot-starter-parent` を直接継承）・`Dockerfile` を持ち、コード共有はしない。

| アプリ | ディレクトリ | 役割 | APM |
|---|---|---|---|
| `user-company-api` | `apps/user-company-api/` | User API（DynamoDB）+ Company API（MySQL/Aurora） | あり（ADOT Java Agent + CloudWatch Agent サイドカー） |
| `log-api` | `apps/log-api/` | 任意のログを受け付けて標準出力するだけの最小サンプル（アプリケーション分離の実証用） | なし |

**NLB 共有の実現方法**: NLB は L4 でパスベースルーティングができないため、**アプリごとに NLB の
リスナーポートを分ける**（`user-company-api`=80、`log-api`=8081）。ALB 側は path-pattern の
ListenerRule で、アプリごとの NLB ポート宛の ALB TargetGroup に振り分ける。NLB オブジェクト自体
（`AWS::ElasticLoadBalancingV2::LoadBalancer`）は共有スタック（`06-shared-cluster-lb.yaml`）が
1つだけ所有し、Listener・TargetGroup はアプリ別スタック（`07-`/`08-`）がそれぞれ追加する。

アプリを追加する場合:
1. `apps/<new-app>/` に独立した Spring Boot プロジェクトを作成する。
2. `cloudformation/0X-ecs-<new-app>.yaml` を作成し、`06-shared-cluster-lb.yaml` の Export
   （ECS Cluster / SG / ALBListenerArn / NLBArn / NlbPrivateIp）を Import して、そのアプリ専用の
   NLB Listener（他アプリと重複しない新しいポート）・TargetGroup・ALB ListenerRule・
   TaskDefinition・ECS Service を定義する。
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
│   │   │       │   │   └── DynamoDbConfig.java      # AmazonDynamoDB / DynamoDBMapper Bean 定義
│   │   │       │   ├── client/
│   │   │       │   │   └── IpifyClient.java         # @FeignClient（ipify 外部 HTTP 呼び出し）
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
│       ├── Dockerfile                   # APM 計装なしの最小構成
│       ├── .dockerignore
│       └── pom.xml
├── cloudformation/
│   ├── 01-network.yaml
│   ├── 02-ecr.yaml                     # AppName パラメータ化。アプリごとにデプロイ
│   ├── 03-dynamodb.yaml                # user-company-api 専用
│   ├── 05-rds.yaml                     # user-company-api 専用
│   ├── 06-shared-cluster-lb.yaml       # 共有: ECS Cluster / ALB(+Listener) / NLB / SG群 / TaskExecutionRole
│   ├── 07-ecs-user-company-api.yaml    # user-company-api の ECS Service
│   └── 08-ecs-log-api.yaml             # log-api の ECS Service
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

| メソッド | パス | 説明 |
|--------|------|------|
| GET | /health | ALB/NLB ヘルスチェック。`{"status":"ok","env":"..."}` を返す |
| POST | /logs | JSON ボディを受け付けて SLF4J で標準出力する（202 Accepted） |

* `@RequestBody JsonNode` で受ける。フィールド構成は特定のスキーマに固定せず任意の JSON 構造を
  許容するが、`Content-Type: application/json` かつ構文として妥当な JSON であることは必須
  （Content-Type 不一致は 415、不正な JSON は 400 を Spring のデフォルトエラーハンドリングが返す）。
* 標準出力に流すだけなので ECS の awslogs ドライバがそのまま CloudWatch Logs に転送する。
* APM（ADOT/X-Ray）は組み込まない。TaskRole も付与しない（AWS API を呼ばないため）。

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
| 06-shared-cluster-lb.yaml | `${ProjectName}-shared` | ECS クラスター、ALB（+デフォルトListener）、内部 NLB、SG群、TaskExecutionRole。**全アプリ共有** |
| 07-ecs-user-company-api.yaml | `${ProjectName}-ecs-user-company-api` | TaskRole、タスク定義、Fargate サービス、NLB Listener(:80)/TargetGroup、ALB TargetGroup/ListenerRule |
| 08-ecs-log-api.yaml | `${ProjectName}-ecs-log-api` | タスク定義、Fargate サービス、NLB Listener(:8081)/TargetGroup、ALB TargetGroup/ListenerRule（TaskRole なし） |

### ネットワーク設計（01-network.yaml）
* リージョン: **ap-northeast-1**（東京）
* VPC CIDR: `10.20.0.0/16`
* 3 層構成（Public / Application / Private）、各層 2AZ（1a / 1c）、サブネットマスクは **/20**
  * Public: `10.20.0.0/20`（1a）、`10.20.16.0/20`（1c）— ALB を配置
  * Application: `10.20.32.0/20`（1a）、`10.20.48.0/20`（1c）— 内部 NLB を配置
  * Private: `10.20.64.0/20`（1a）、`10.20.80.0/20`（1c）— ECS Fargate タスク / Aurora MySQL を配置
* NAT Gateway: コスト削減のため **単一 AZ（1a）のみ**。Application / Private の各ルートテーブルから同じ NAT Gateway を利用
* DynamoDB / S3 へは Gateway 型 VPC Endpoint 経由で接続（NAT 課金回避）。Private・Application 両方のルートテーブルに関連付け済み

### ECS Fargate 設計（07-ecs-user-company-api.yaml / 08-ecs-log-api.yaml）
* CPU/Memory: user-company-api は **512/1024**（CloudWatch Agent サイドカー常駐分を含む）、
  log-api は **256/512**（サイドカーなしの最小構成）
* コンテナポート: **8080**（全アプリ共通）
* ヘルスチェックパス: `/health`
* サービス: PrivateSubnet に配置、PublicIP 無効

### 通信経路（ALB → NLB → ECS）
* `Internet → ALB（Public） → NLB（Application、internal） → ECS（Private）` の 3 ホップ構成。
* **ALB**: Public Subnet に配置、internet-facing。HTTP:80 で受信し、パスごとにターゲットグループへ転送する
  （`06-shared-cluster-lb.yaml` が所有。DefaultAction は 404 固定応答、アプリ別の ListenerRule は各アプリの
  スタックが追加する）。
* **NLB**: Application Subnet に配置、**Scheme: internal**（インターネットからのアクセス不可、IGW ルートなし）。
  **全アプリで共有**（`06-shared-cluster-lb.yaml` が所有）だが、L4 でパスベースルーティングができないため
  **アプリごとに Listener のポートを分ける**（user-company-api: TCP:80、log-api: TCP:8081）。
  各 Listener は対応するアプリのスタック（07/08）が追加し、そのアプリの ECS タスク
  （コンテナポート 8080）へ転送する。
* **ALB → NLB の接続方法**: ALB のターゲットグループには NLB を直接指定する仕組み（"nlb" ターゲットタイプ）が
  存在しない。そのため NLB の `SubnetMappings` に `PrivateIPv4Address` で固定プライベート IP
  （`NlbPrivateIp1a` = `10.20.32.10`、`NlbPrivateIp1c` = `10.20.48.10`、いずれも Application Subnet の
  CIDR 内。`06-shared-cluster-lb.yaml` の Output として export）を割り当て、アプリごとの ALB
  ターゲットグループ（`AlbToNlbTargetGroup`、IP タイプ）にその固定 IP を**そのアプリの NLB Listener
  ポート**で静的ターゲットとして登録することで疑似的に ALB → NLB（該当アプリ宛）の転送を実現する。
* **セキュリティグループ**: ALB SG（0.0.0.0/0:80 許可）→ NLB SG（ALB SG からの :80 のみ許可）→
  ECS SG（NLB SG からの :8080 のみ許可）の順に絞り込む。全アプリで SG は共有（コンテナポートが
  8080 で統一されているため）。

#### ECS タスクの環境変数
| 変数名                  | 値                                |
|-----------------------|----------------------------------|
| APP_ENV               | prod                             |
| AWS_REGION            | `!Ref AWS::Region`               |
| DYNAMODB_USERS_TABLE  | `!Ref UsersTableName`（デフォルト: users）|
| DYNAMODB_ENDPOINT_URL | （未設定）                           |

#### IAM
* **TaskExecutionRole**: `AmazonECSTaskExecutionRolePolicy`（イメージプル・ログ書き込み）
* **TaskRole**: DynamoDB CRUD ポリシー（GetItem / PutItem / UpdateItem / DeleteItem / Query / Scan）
  * APM 追加フェーズでは `AWSXRayDaemonWriteAccess` / `CloudWatchAgentServerPolicy` を追加する

#### ALB
* スキーム: internet-facing
* 認証不要
* ヘルスチェック: `GET /health`、200 OK

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
| scripts/deploy-service.sh `<app>` | 指定アプリの ECS スタック（07 or 08）をデプロイ → 強制デプロイ |
| scripts/deploy-all.sh      | deploy-infra.sh の後、全アプリで build.sh/deploy-service.sh をループ実行 |
| scripts/delete-all.sh      | 全スタックを逆順で削除（全アプリの ECS/ECR スタックを含む） |
| scripts/local-infra.sh     | user-company-api 用ローカルインフラ起動・停止（MySQL + DynamoDB Local） |
| scripts/local-run.sh `<app>` | ローカル開発用 Spring Boot 起動（user-company-api はインフラ起動も含む） |

`<app>` は `apps/` 配下のディレクトリ名（`user-company-api` または `log-api`）。アプリを追加する場合は
`common.sh` の `APPS` 配列と `app_ecs_template()` の case 分岐を更新する。

### 共通設定（common.sh）
```bash
export PROJECT_NAME="${PROJECT_NAME:-java-apm-sample}"
export AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION:-ap-northeast-1}"

# アプリ一覧（apps/ 配下のディレクトリ名と一致させる）
APPS=(user-company-api log-api)

# Stack 名（アプリ非依存の共有スタックのみ。アプリ別スタック名は app_ecr_stack()/app_ecs_stack() で解決する）
export NETWORK_STACK="${PROJECT_NAME}-network"
export DYNAMODB_STACK="${PROJECT_NAME}-dynamodb"
export RDS_STACK="${PROJECT_NAME}-rds"
export SHARED_STACK="${PROJECT_NAME}-shared"
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
* 第1引数 `<app>` 必須。`<app>` に応じてテンプレート（07/08）と追加パラメータ
  （user-company-api のみ `MySqlUsername`）を出し分ける
* `IMAGE_URI` が未指定の場合は `apps/<app>/.last_image_uri` から読み込む
* CloudFormation デプロイ後、イメージのみ更新の場合に変更セットが空になる問題を
  `aws ecs update-service --force-new-deployment` で回避する
* ECS クラスター名・ALB DNS 名は共有スタック（`SHARED_STACK`）の Output から、
  ECS サービス名はアプリ別スタックの Output から取得する

## APM 追加フェーズ（後日実装）
現フェーズでは以下を実装しない。後フェーズで追加予定。
* OpenTelemetry Java Agent（`-javaagent` による自動計装）
* AWS Distro for OpenTelemetry（ADOT）
* CloudWatch Agent サイドカー（Application Signals）
* ECS タスク定義への OTEL 関連環境変数追加
* TaskRole への `AWSXRayDaemonWriteAccess` / `CloudWatchAgentServerPolicy` 追加
* CPU/Memory: 512/1024 への引き上げ（サイドカー追加後）
