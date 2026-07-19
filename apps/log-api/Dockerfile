# Stage 1: builder
# amazoncorretto:17-al2023 を builder でも使うことでツールチェーンのバージョンを統一する。
FROM amazoncorretto:17-al2023 AS builder

RUN dnf install -y maven && dnf clean all

WORKDIR /build

# pom.xml を先にコピーして依存解決をキャッシュレイヤーとして分離する。
COPY pom.xml .
RUN mvn dependency:go-offline -q

COPY src/ src/
RUN mvn package -DskipTests -q

# Stage 2: runtime
# builder ステージのビルド成果物（JAR）のみをコピーし、最小イメージにする。
# log-api は最低限のサンプルのため APM（ADOT Java Agent）は組み込まない。
FROM amazoncorretto:17-al2023

# amazoncorretto:17-al2023 の最小イメージには shadow-utils（useradd を含む）が含まれていないため先にインストールする
RUN dnf install -y shadow-utils && dnf clean all && useradd -r -M -s /sbin/nologin app

WORKDIR /app
COPY --from=builder /build/target/*.jar app.jar
RUN chown app:app app.jar

USER app

EXPOSE 8080

# ECS の awslogs ドライバへ stdout を流す前提。
# -Dspring.output.ansi.enabled=NEVER: ANSI カラーコードを除去して CloudWatch Logs を可読にする。
CMD ["java", \
     "-Dspring.output.ansi.enabled=NEVER", \
     "-jar", "app.jar"]