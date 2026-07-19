# Stage 1: builder
# amazoncorretto:17-al2023 を builder でも使うことでツールチェーンのバージョンを統一する。
# dnf で Maven をインストールし、mvn package でファット JAR を生成する。
FROM amazoncorretto:17-al2023 AS builder

RUN dnf install -y maven && dnf clean all

WORKDIR /build

# pom.xml を先にコピーして依存解決をキャッシュレイヤーとして分離する。
# src/ を変更しても依存解決レイヤーは再利用されるためビルドが高速になる。
COPY pom.xml .
RUN mvn dependency:go-offline -q

COPY src/ src/
RUN mvn package -DskipTests -q

# ADOT Java Agent をダウンロードする。
# v2.x 系を使用する（v1.x はメンテナンスモードのため非推奨）。
# 最新バージョンは https://github.com/aws-observability/aws-otel-java-instrumentation/releases で確認すること。
ARG ADOT_AGENT_VERSION=2.28.1
RUN curl -fSL \
    https://github.com/aws-observability/aws-otel-java-instrumentation/releases/download/v${ADOT_AGENT_VERSION}/aws-opentelemetry-agent.jar \
    -o /build/aws-opentelemetry-agent.jar

# Stage 2: runtime
# builder ステージのビルド成果物（JAR と Agent）のみをコピーし、最小イメージにする。
FROM amazoncorretto:17-al2023

# amazoncorretto:17-al2023 の最小イメージには shadow-utils（useradd を含む）が含まれていないため先にインストールする
RUN dnf install -y shadow-utils && dnf clean all && useradd -r -M -s /sbin/nologin app

WORKDIR /app
COPY --from=builder /build/target/*.jar app.jar
COPY --from=builder /build/aws-opentelemetry-agent.jar aws-opentelemetry-agent.jar
RUN chown app:app app.jar aws-opentelemetry-agent.jar

USER app

EXPOSE 8080

# -javaagent: ADOT Java Agent によるバイトコード計装（Spring MVC / AWS SDK v1 / OpenFeign を自動計装）。
# アプリコードの変更なしにトレースが有効になる。
# OTEL_* 環境変数は ECS タスク定義の Environment で注入する。
# -Dspring.output.ansi.enabled=NEVER: ANSI カラーコードを除去して CloudWatch Logs を可読にする。
CMD ["java", \
     "-javaagent:/app/aws-opentelemetry-agent.jar", \
     "-Dspring.output.ansi.enabled=NEVER", \
     "-jar", "app.jar"]