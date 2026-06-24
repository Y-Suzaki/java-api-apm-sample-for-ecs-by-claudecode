# java-api-apm-sample-for-ecs-by-claudecode

Spring Boot 3.5 (Java) で実装したユーザー管理 API を、AWS ALB + ECS Fargate + DynamoDB 上で動かすサンプル。  
OpenTelemetry を活用し、Application Signals と X-Ray と連携する。

## 構成図（論理）

```
[Internet] → ALB (public subnet) → ECS Fargate Task (private subnet) → DynamoDB (Gateway VPC Endpoint)
                                                             ↘ NAT Gateway (single AZ) → Internet
```

- リージョン: `ap-northeast-1`（東京）
- ネットワーク: 自前の VPC（Public/Private 2 AZ 構成）。コスト削減のため NAT Gateway は単一 AZ。
- DB: DynamoDB（Partition Key = `email`）。VPC からは Gateway 型エンドポイント経由。
- 認証: なし（サンプル用途）。

## ディレクトリ

| パス | 説明 |
| --- | --- |
| `src/main/java/com/example/api/` | Spring Boot アプリケーション本体 |
| `controller/` | リクエスト受け付け層（`/users`, `/health`, `/configuration`） |
| `service/` | ドメインロジック（DynamoDB 入出力） |
| `repository/` | DynamoDB アクセス層（DynamoDBMapper） |
| `model/` | DTO／DynamoDB マッピングモデル |
| `client/` | Feign Client（`IpifyClient`、外部 HTTP 呼び出し） |
| `config/` | Bean 定義（`DynamoDbConfig`, `AppProperties`） |
| `exception/` | 例外クラス + `GlobalExceptionHandler` |
| `Dockerfile` | Amazon Corretto 17 マルチステージビルド → 非 root で起動 |
| `cloudformation/` | スタック分割した CFN テンプレート |
| `scripts/` | ビルド・デプロイ用シェルスクリプト |

## ローカル開発

```bash
mvn spring-boot:run
# http://localhost:8080/health で疎通確認
```

DynamoDB Local を使う場合は `DYNAMODB_ENDPOINT_URL=http://localhost:8000` を設定。

## API 一覧

| Method | Path | 概要 |
| --- | --- | --- |
| GET | `/health` | ALB ヘルスチェック |
| POST | `/users` | ユーザー新規作成 |
| GET | `/users` | ユーザー一覧取得（最大 100 件） |
| GET | `/users/{email}` | ユーザー詳細取得 |
| PUT | `/users/{email}` | ユーザー名更新 |
| GET | `/configuration` | サンプル設定取得（外部 HTTP 呼び出しのトレース確認用） |

## デプロイ

AWS 認証情報（環境変数で渡す）と Docker / AWS CLI が必要。

```bash
export AWS_ACCESS_KEY_ID=...
export AWS_SECRET_ACCESS_KEY=...
# 必要に応じて
# export AWS_SESSION_TOKEN=...

# 1) VPC / ECR / DynamoDB を作成
./scripts/deploy-infra.sh

# 2) Docker イメージをビルドして ECR にプッシュ
./scripts/build.sh

# 3) ALB + ECS サービスをデプロイ（既存なら新タスクへロールアウト）
./scripts/deploy-service.sh

# まとめて：
./scripts/deploy-all.sh
```

完了後、`scripts/deploy-service.sh` の出力にある `http://<alb-dns>/health` から疎通確認できる。

## トレーシング（AWS Application Signals）

ALB 経由のリクエストを受けた Spring Boot から DynamoDB 呼び出しまでを **1 本のトレース** として
AWS Application Signals（X-Ray トレース詳細 + CloudWatch メトリクス）で可視化できるようにしている。
**アプリコードを一切変更せず**、**ADOT Java Agent**（`-javaagent`）による JVM バイトコード書き換えと
**CloudWatch Agent サイドカー（Application Signals モード）** の 2 段構成で実現しているのがポイント。

### Python 版との最大の差分

| 項目 | Python 版 | Java 版 |
| --- | --- | --- |
| 計装方法 | `telemetry.py` に `setup_tracing()` を書いてアプリから呼ぶ（コード変更あり） | `-javaagent` を JVM 起動オプションに追加するだけ。**アプリコード変更なし** |
| 依存ライブラリ | `pyproject.toml` に複数の OTel パッケージを追加 | **`pom.xml` 変更なし**。Agent JAR を Dockerfile でダウンロードするのみ |
| 計装対象の自動検出 | FastAPI / boto3 / httpx を Instrumentor 単位で明示登録 | Spring MVC / AWS SDK v1 / OpenFeign を Agent が JVM バイトコードを書き換えて自動計装 |

### データフロー

```
[Client]
    │ HTTP
    ▼
[ALB] ─── X-Amzn-Trace-Id 採番／伝播
    │
    ▼
┌─ ECS Fargate Task ────────────────────────────────────────────┐
│   ┌──────────────────────┐  OTLP/HTTP  ┌────────────────────────┐ │
│   │ api (Spring Boot)    │ ──────────▶ │ otel-collector         │ │
│   │ :8080                │   :4316      │ (CloudWatch Agent      │ │
│   │ ADOT Java Agent      │              │  Application Signals)  │ │
│   │ (-javaagent)         │              └──────────┬─────────────┘ │
│   │                      │                         │               │
│   │ 自動計装:             │                         │               │
│   │  • Spring MVC        │                         │               │
│   │  • AWS SDK v1        │                         │               │
│   │  • OpenFeign         │                         │               │
│   └──┬──────────┬────────┘                         │               │
└──────┼──────────┼──────────────────────────────────┼───────────────┘
       │ AWS API  │ HTTPS                             │
       │(SDK v1)  │(Feign)               ┌────────────┴───────────┐
       ▼          ▼                      ▼                        ▼
   [DynamoDB]  [NAT GW] ──▶ Internet  [AWS X-Ray]     [CloudWatch Metrics]
   (Gateway               ──▶ [外部 API]  (トレース詳細)  (RED メトリクス)
    VPCE)                    (api.ipify.org)
```

### 構成要素

| レイヤ | 採用したもの | 役割 |
| --- | --- | --- |
| トレース ID | ADOT Java Agent 組み込み `AwsXRayIdGenerator` | `1-{8桁epoch}-{96bitランダム}` 形式で採番し X-Ray にそのまま投入できる |
| 伝播ヘッダ | `AwsXRayPropagator`（`X-Amzn-Trace-Id`） | ALB が付ける `X-Amzn-Trace-Id` を親コンテキストとして引き継ぎ、ALB→ECS でトレースが分断されない |
| アプリ計装 | Spring MVC 自動計装（Agent 組み込み） | 受信リクエストを HTTP サーバースパンに変換。コード変更不要 |
| AWS SDK 計装 | AWS SDK v1 自動計装（Agent 組み込み） | DynamoDB API 呼び出しを AWS スパン（`AWS::DynamoDB::Table` ノード）に変換 |
| 外部 HTTP 計装 | OpenFeign 自動計装（Agent 組み込み） | `IpifyClient` 経由の外部 API 呼び出しを HTTP クライアントスパンに変換（`/configuration` で確認可） |
| 送信 | OTLP/HTTP | `localhost:4316/v1/traces`（CloudWatch Agent Application Signals ポート）にバッチ送信 |
| 集約／Application Signals 送信 | CloudWatch Agent（`public.ecr.aws/cloudwatch-agent/cloudwatch-agent`）サイドカー | OTLP を受け取り X-Ray にトレースを PUT、CloudWatch に RED メトリクスを書き込む |

### Fargate サイドカー構成（`cloudformation/04-ecs-alb.yaml`）

- 同一タスク内に `${ProjectName}-container`（Spring Boot）と `otel-collector`（CloudWatch Agent）を定義。
  `awsvpc` モードのため両者は `localhost` で疎通する
  （`OTEL_EXPORTER_OTLP_TRACES_ENDPOINT=http://localhost:4316/v1/traces`）。
- CloudWatch Agent は `CW_CONFIG_CONTENT` 環境変数に JSON で設定を流し込む。
  `traces.traces_collected.application_signals` と `logs.metrics_collected.application_signals`
  を有効化するだけで OTLP receiver（ポート 4316）と X-Ray サンプリングプロキシ（UDP/2000）
  が起動する。外部設定ファイルは不要。
- アプリコンテナには `DependsOn: [{otel-collector, START}]` を付け、初回スパン送信時の接続失敗を回避。
- タスクロールに以下のマネージドポリシーを付与し、サイドカーから X-Ray / CloudWatch に
  書き込みできるようにしている。
    - `AWSXRayDaemonWriteAccess`（`xray:PutTraceSegments`, `xray:PutTelemetryRecords`）
    - `CloudWatchAgentServerPolicy`（メトリクス／ログ送信 + Application Signals API 呼び出し）
- サイドカー追加に伴い、タスクサイズは `256/512` から `512/1024` に引き上げた。
  Agent の常駐メモリ（〜50MiB）を安定して扱うため。

### ADOT Java Agent（`Dockerfile`）

Agent JAR は builder ステージで GitHub Releases からダウンロードし、runtime ステージにコピーする。

```dockerfile
# builder stage
ARG ADOT_AGENT_VERSION=2.28.1
RUN curl -fSL \
    https://github.com/aws-observability/aws-otel-java-instrumentation/releases/download/v${ADOT_AGENT_VERSION}/aws-opentelemetry-agent.jar \
    -o /build/aws-opentelemetry-agent.jar

# runtime stage
CMD ["java", "-javaagent:/app/aws-opentelemetry-agent.jar", \
     "-Dspring.output.ansi.enabled=NEVER", "-jar", "app.jar"]
```

**バージョン選定方針:**

| 系統 | 内部 OTel Java | 状態 |
| --- | --- | --- |
| v1.x | OTel Java 1.x | メンテナンスモード（新機能追加なし） |
| v2.x | OTel Java 2.x | **現在のメイン開発ライン。今から使うならこちら** |

### ECS タスク定義で渡している環境変数

| 変数 | 値 | 効果 |
| --- | --- | --- |
| `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT` | `http://localhost:4316/v1/traces` | CloudWatch Agent Application Signals ポート宛 OTLP/HTTP |
| `OTEL_EXPORTER_OTLP_PROTOCOL` | `http/protobuf` | gRPC ではなく HTTP/Protobuf |
| `OTEL_AWS_APPLICATION_SIGNALS_ENABLED` | `true` | Application Signals のメトリクス派生を有効化 |
| `OTEL_AWS_APPLICATION_SIGNALS_EXPORTER_ENDPOINT` | `http://localhost:4316/v1/metrics` | メトリクス送信先 |
| `OTEL_METRICS_EXPORTER` | `none` | OTLP の metrics 直送を無効化（Application Signals 経由で送るため） |
| `OTEL_LOGS_EXPORTER` | `none` | 同上（logs） |
| `OTEL_TRACES_SAMPLER` | `xray` | X-Ray セントラルサンプリングを使用 |
| `OTEL_TRACES_SAMPLER_ARG` | `endpoint=http://localhost:2000` | CW Agent が UDP/2000 で X-Ray と橋渡し |
| `OTEL_SERVICE_NAME` | `java-apm-sample-api` | Application Signals サービスマップのノード名 |
| `OTEL_PROPAGATORS` | `tracecontext,baggage,b3,xray` | Application Signals 推奨の伝播形式 |
| `OTEL_RESOURCE_ATTRIBUTES` | `deployment.environment=prod,service.namespace=java-apm-sample` | サービスマップのフィルタタグ |

Python 版にある `OTEL_PYTHON_DISTRO` / `OTEL_PYTHON_CONFIGURATOR` は Java では不要（Agent が自動処理）。

### `/health` ノイズ対策

Python 版と Java 版では `/health` ヘルスチェックのノイズ抑制アプローチが異なる。

| アプローチ | Python 版 | Java 版 |
| --- | --- | --- |
| X-Ray トレース | `FastAPIInstrumentor(excluded_urls="/health$")` でスパン自体を生成しない | X-Ray Sampling Rule で採択率 0% に設定する |
| Application Signals メトリクス | スパン未生成のためメトリクスにも混入しない | **X-Ray Sampling Rule では抑制不可**（後述） |

#### Java 版で Application Signals メトリクスが抑制されない理由

```
スパン終了
  │
  ├─► AwsSpanMetricsProcessor（Application Signals メトリクス派生）← ここで /health をカウント
  │
  └─► Sampler（X-Ray サンプリング判定）
        ├─► 採択 → X-Ray に送信
        └─► 棄却 → X-Ray には送信しない（メトリクスは既に記録済み）
```

Python の `excluded_urls` はスパン自体を生成しないため両方抑制できるが、
Java Agent の Sampling Rule はスパン生成後の判定なのでメトリクス側には届かない。

**推奨対処:** まず X-Ray Sampling Rule のみで運用を開始する。`/health` は常に 200 OK の
ためエラー率を汚染しない。レイテンシ・リクエスト数への影響が問題になった場合は
CloudWatch 側のメトリクスフィルタで対処する。

### 外部 HTTP 呼び出しの計装（OpenFeign）

`IpifyClient`（`@FeignClient`）から発行される HTTP リクエストは、ADOT Java Agent が
OpenFeign を自動計装するため、**アプリコードへの変更は不要**でそのまま観測できる。

#### スパンの中身

| 観点 | 内容 |
| --- | --- |
| Span Kind | `CLIENT` |
| Span 名 | HTTP メソッド名（`GET` など） |
| 主な属性 | `http.url`, `http.method`, `http.status_code`, `server.address`, `server.port` |
| 親スパン | 同リクエスト内の `SERVER` スパン（例: `GET /configuration`） |
| サービスマップ | 接続先ホスト名（`api.ipify.org` 等）が外部ノードとして可視化 |

#### サンプル：`GET /configuration`

`ConfigurationController` で `IpifyClient` から `https://api.ipify.org` を呼び出す薄いエンドポイント。
**OpenFeign 計装の動作確認専用**として用意している。

```bash
curl "http://${ALB_DNS}/configuration"
# => {"serviceName":"java-apm-sample","environment":"prod","outboundIp":"<NAT GW の EIP>"}
```

X-Ray 上で見えるべきもの：

- **トレース詳細**: ルートの `GET /configuration`（SERVER スパン）配下に、
  `GET`（CLIENT スパン、`http.url=https://api.ipify.org`）が表示される。
- **サービスマップ**: `java-apm-sample-api → api.ipify.org` のエッジが追加される。
- **副次効果**: レスポンスの `outboundIp` は NAT Gateway に紐付いた Elastic IP と
  一致するので、**Private Subnet → NAT → Internet** の経路まで合わせて検証できる。

### 動作確認

デプロイ後、いくつかリクエストを投げてから X-Ray コンソールを開く：

```bash
curl -X POST "http://${ALB_DNS}/users" -H 'content-type: application/json' \
  -d '{"email":"alice@example.com","name":"Alice"}'
curl "http://${ALB_DNS}/users/alice@example.com"
```

- **Service map**: `java-apm-sample-api` ノードと `DynamoDB` ノードがエッジで接続される。
- **Traces**: 1 リクエストにつき `GET /users/{email}` のサーバースパンと、その配下に
  `DynamoDB.GetItem` の AWS スパンがぶら下がる。
- ALB 自体は X-Ray に独立スパンを発行しないが、ALB が採番した `X-Amzn-Trace-Id` を
  アプリ側で親として継承するため、トレース ID は ALB から DynamoDB まで一貫する。

### 既知のはまりどころ

- ECR Public からの `cloudwatch-agent` イメージ pull は、Private サブネットからは
  NAT Gateway 経由で外部に出る必要がある。本サンプルは NAT Gateway を構築済みなので OK。
- アプリコンテナが CloudWatch Agent より先に立ち上がると初回エクスポートが失敗するため、
  `DependsOn: [{otel-collector, START}]` を必ず指定する（再起動時のレース対策）。
- ADOT Java Agent は JVM 起動時にバイトコードを書き換えるため、`-javaagent` を付けずに
  起動するとトレースが一切取れない。Dockerfile の CMD に必ず含めること。
- ローカル実行（`mvn spring-boot:run`）では `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT` を未設定にしておけば
  Agent がエクスポーターを初期化せず、起動エラーにならない。
- Agent による AWS SDK v1 の自動計装には、SDK が内部で生成する `AmazonDynamoDB` クライアントが
  Spring コンテキスト初期化前に作られないことが前提。`DynamoDbConfig` で Bean 定義しているため問題ない。

## クリーンアップ

```bash
./scripts/delete-all.sh
```

スクリプトが確認プロンプトを表示した後、ECR イメージを削除してからスタックを逆順（ECS → DynamoDB → ECR → Network）で削除する。

NAT Gateway / EIP は時間課金のため、不要時は削除すること。
