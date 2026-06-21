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

# Stage 2: runtime
# builder ステージのビルド成果物（JAR）のみをコピーし、最小イメージにする。
FROM amazoncorretto:17-al2023

# amazoncorretto:17-al2023 の最小イメージには shadow-utils（useradd を含む）が含まれていないため先にインストールする
RUN dnf install -y shadow-utils && dnf clean all && useradd -r -M -s /sbin/nologin app

WORKDIR /app
COPY --from=builder /build/target/*.jar app.jar
RUN chown app:app app.jar

USER app

EXPOSE 8080

# -Dspring.output.ansi.enabled=NEVER: ANSI カラーコードを除去して CloudWatch Logs を可読にする
CMD ["java", "-Dspring.output.ansi.enabled=NEVER", "-jar", "app.jar"]