# APM 実装設計方針 — Python vs Java 差分

## 概要

Python 版（FastAPI）と Java 版（Spring Boot）の APM（分散トレーシング）実装における
設計差分と変更スコープをまとめる。

---

## 最大の差分：計装アプローチ

| 項目 | Python | Java |
|------|--------|------|
| 計装方法 | `telemetry.py` に `setup_tracing()` を書き、`main.py` から明示的に呼ぶ（コード変更あり） | **ADOT Java Agent**（`-javaagent`）を JVM 起動オプションに追加するだけ。アプリコード変更なし |
| 依存ライブラリ | `pyproject.toml` に `aws-opentelemetry-distro`, `opentelemetry-instrumentation-fastapi/botocore/httpx` を追加 | **`pom.xml` 変更なし**。Agent JAR を Dockerfile でダウンロードして `/app/` に置くのみ |
| 計装対象の自動検出 | FastAPI / boto3 / httpx を Instrumentor 単位で明示的に登録 | Spring MVC / AWS SDK v1 / OpenFeign を Agent が JVM バイトコードを書き換えて自動計装 |

---

## 各ファイルの変更範囲

### `Dockerfile`（変更あり）

Builder ステージで ADOT Java Agent JAR をダウンロードし、runtime ステージにコピー。
起動コマンドに `-javaagent` を追加するだけ。

```dockerfile
# builder stage に追加
# バージョンは v2.x 系の最新を使用する（v1.x はメンテナンスモードのため非推奨）。
# 最新バージョンは以下で確認すること:
#   https://github.com/aws-observability/aws-otel-java-instrumentation/releases
ARG ADOT_AGENT_VERSION=2.28.1
RUN curl -L https://github.com/aws-observability/aws-otel-java-instrumentation/releases/download/v${ADOT_AGENT_VERSION}/aws-opentelemetry-agent.jar \
    -o /build/aws-opentelemetry-agent.jar

# runtime stage
COPY --from=builder /build/aws-opentelemetry-agent.jar aws-opentelemetry-agent.jar
CMD ["java", "-javaagent:/app/aws-opentelemetry-agent.jar", \
     "-Dspring.output.ansi.enabled=NEVER", "-jar", "app.jar"]
```

**バージョン選定方針:**

| 系統 | 内部 OTel Java | 状態 |
|------|---------------|------|
| v1.x | OTel Java 1.x | メンテナンスモード（新機能追加なし） |
| v2.x | OTel Java 2.x | **現在のメイン開発ライン。今から使うならこちら** |

`-javaagent` の使い方・`OTEL_AWS_APPLICATION_SIGNALS_*` 等の環境変数は v1.x / v2.x で変わらない。

### `cloudformation/04-ecs-alb.yaml`（変更あり）

Python 版と構造はほぼ同じ。変更点のみ:

| 項目 | Python | Java |
|------|--------|------|
| CPU/Memory | 512/1024 | 512/1024（同じ） |
| TaskRole | `AWSXRayDaemonWriteAccess` + `CloudWatchAgentServerPolicy` | 同じ |
| CloudWatch Agent サイドカー | あり | 同じ（完全に共通） |
| OTEL 環境変数 | `OTEL_PYTHON_DISTRO`, `OTEL_PYTHON_CONFIGURATOR` が必要 | 不要（Java Agent が自動処理） |
| その他 OTEL 環境変数 | `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT=http://localhost:4316/v1/traces` など | **同じ**（Agent が環境変数を読む） |

#### OTEL 環境変数（アプリコンテナ）

Python 版・Java 版で共通して設定する環境変数:

```yaml
- Name: OTEL_EXPORTER_OTLP_PROTOCOL
  Value: http/protobuf
- Name: OTEL_EXPORTER_OTLP_TRACES_ENDPOINT
  Value: http://localhost:4316/v1/traces
- Name: OTEL_AWS_APPLICATION_SIGNALS_ENABLED
  Value: "true"
- Name: OTEL_AWS_APPLICATION_SIGNALS_EXPORTER_ENDPOINT
  Value: http://localhost:4316/v1/metrics
- Name: OTEL_METRICS_EXPORTER
  Value: none
- Name: OTEL_LOGS_EXPORTER
  Value: none
- Name: OTEL_TRACES_SAMPLER
  Value: xray
- Name: OTEL_TRACES_SAMPLER_ARG
  Value: endpoint=http://localhost:2000
- Name: OTEL_SERVICE_NAME
  Value: java-apm-sample-api
- Name: OTEL_RESOURCE_ATTRIBUTES
  Value: deployment.environment=prod,service.namespace=java-apm-sample
- Name: OTEL_PROPAGATORS
  Value: tracecontext,baggage,b3,xray
```

Java 版のみ追加不要（Python 版にはある）:

```yaml
# 以下は Python 版固有。Java 版では設定しない。
- Name: OTEL_PYTHON_DISTRO
  Value: aws_distro
- Name: OTEL_PYTHON_CONFIGURATOR
  Value: aws_configurator
```

#### CloudWatch Agent サイドカー（Python/Java 共通）

```yaml
- Name: otel-collector
  Image: public.ecr.aws/cloudwatch-agent/cloudwatch-agent:latest
  Essential: true
  PortMappings:
    - ContainerPort: 4316   # OTLP/HTTP 受信ポート
      Protocol: tcp
  Environment:
    - Name: CW_CONFIG_CONTENT
      Value: |
        {
          "traces": {
            "traces_collected": { "application_signals": {} }
          },
          "logs": {
            "metrics_collected": { "application_signals": {} }
          }
        }
```

### アプリ Java コード（変更なし）

Python では `setup_tracing()` を書いたが、Java では何も書かない。
Spring MVC・AWS SDK v1・OpenFeign すべて Agent が自動計装する。

### `pom.xml`（変更なし）

ライブラリ追加は不要。Agent JAR は Dockerfile でダウンロードして `/app/` に置く。

---

## `/health` ノイズ対策の差分

Python では 2 段構えで対処していた:

1. `FastAPIInstrumentor(excluded_urls="/health$")` — FastAPI レベルで計装対象外に（スパン自体を生成しない）
2. `_NoiseFilteringSpanExporter` — ASGI 内部イベントスパン（`http.response.start` 等）を export 手前で drop

**ASGI 内部スパン問題は Java では発生しない**（Spring MVC はそういうスパンを生成しない）。

### X-Ray Sampling Rule だけでは Application Signals メトリクスは抑制されない

処理の順序の問題:

```
スパン終了
  │
  ├─► AwsSpanMetricsProcessor（Application Signals メトリクス派生）← ここで /health をカウント
  │
  └─► Sampler（X-Ray サンプリング判定）
        ├─► 採択 → X-Ray に送信
        └─► 棄却 → X-Ray には送信しない（メトリクスは既に記録済み）
```

Python の `excluded_urls` はスパン自体を生成しないため X-Ray・Application Signals 両方を抑制できていた。
Java Agent の Sampling Rule はスパン生成後の判定なので、X-Ray トレースは抑制できるがメトリクス側には届かない。

### 対処の選択肢

| 方法 | X-Ray | App Signals メトリクス | コード変更 | インフラ変更 |
|------|:-----:|:---------------------:|:----------:|:------------:|
| X-Ray Sampling Rule のみ | ✅ | ❌ | なし | なし |
| CloudWatch 側でフィルタリング（表示のみ） | ✅ | ✅（表示のみ） | なし | なし |
| Spring Boot management port 分離 | ✅ | ✅ | あり（`application.yml`） | あり（ALB/SG） |
| カスタム Filter でスパン drop | ✅ | ✅ | あり | なし |

**推奨:** まず X-Ray Sampling Rule のみで運用を開始する。`/health` は常に 200 OK のためエラー率を汚染しない。
レイテンシ・リクエスト数への影響（30秒に1回）が問題になった場合に CloudWatch 側フィルタリングで対処する。

---

## 実装スコープのまとめ

```
変更が必要なファイル:
  Dockerfile                      ← -javaagent オプション追加（Agent JAR ダウンロード含む）
  cloudformation/04-ecs-alb.yaml  ← サイドカー追加、CPU/Memory 変更、OTEL 環境変数追加

変更不要なファイル:
  pom.xml                         ← ライブラリ追加なし
  src/ 配下の全 Java コード        ← 一切変更なし
  cloudformation/01〜03-*.yaml    ← 変更なし
  scripts/                        ← 変更なし
```

---

## アーキテクチャ図（ECS タスク内）

```
┌─────────────────────────────────────────────────┐
│ ECS Task (awsvpc, 512 CPU / 1024 MB)            │
│                                                 │
│  ┌──────────────────────┐  ┌─────────────────┐  │
│  │  app コンテナ         │  │ otel-collector  │  │
│  │  (Spring Boot :8080)  │  │ (CW Agent)      │  │
│  │                      │  │  :4316 OTLP/HTTP│  │
│  │  ADOT Java Agent     │──▶  :2000 X-Ray    │  │
│  │  (-javaagent)        │  │  サンプリング    │  │
│  │                      │  └────────┬────────┘  │
│  │  自動計装:            │           │           │
│  │  - Spring MVC        │           │           │
│  │  - AWS SDK v1        │           ▼           │
│  │  - OpenFeign         │   AWS Application     │
│  └──────────────────────┘   Signals / X-Ray     │
└─────────────────────────────────────────────────┘
```