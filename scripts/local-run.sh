#!/usr/bin/env bash
# ローカル開発用の起動スクリプト。
# DynamoDB Local（Docker）を起動してテーブルを作成し、Spring Boot を起動する。
#
# 前提条件:
#   - Docker が起動していること
#   - AWS CLI がインストールされていること（テーブル作成に使用）
#   - mvn コマンドが PATH に存在すること
#
# 使い方:
#   bash scripts/local-run.sh
#   bash scripts/local-run.sh --skip-dynamodb  # DynamoDB Local が既に起動済みの場合
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="${SCRIPT_DIR}/.."

# ----- 設定 -----
DYNAMODB_PORT=8000
DYNAMODB_CONTAINER_NAME="dynamodb-local"
TABLE_NAME="${DYNAMODB_USERS_TABLE:-users}"
DYNAMODB_ENDPOINT="http://localhost:${DYNAMODB_PORT}"

# Spring Boot 起動時に注入する環境変数
export AWS_ACCESS_KEY_ID="${AWS_ACCESS_KEY_ID:-dummy}"
export AWS_SECRET_ACCESS_KEY="${AWS_SECRET_ACCESS_KEY:-dummy}"
export AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION:-ap-northeast-1}"
export AWS_REGION="${AWS_DEFAULT_REGION}"
export DYNAMODB_ENDPOINT_URL="${DYNAMODB_ENDPOINT}"
export DYNAMODB_USERS_TABLE="${TABLE_NAME}"
export APP_ENV="local"

SKIP_DYNAMODB=false
for arg in "$@"; do
  [[ "$arg" == "--skip-dynamodb" ]] && SKIP_DYNAMODB=true
done

log() {
  printf '\033[1;36m[%s]\033[0m %s\n' "$(date +%H:%M:%S)" "$*"
}

# ----- DynamoDB Local の起動 -----
start_dynamodb() {
  if [[ "${SKIP_DYNAMODB}" == "true" ]]; then
    log "Skipping DynamoDB Local startup (--skip-dynamodb specified)"
    return
  fi

  # 既に起動中かどうか確認する
  if docker ps --filter "name=${DYNAMODB_CONTAINER_NAME}" --filter "status=running" \
       --format "{{.Names}}" | grep -q "${DYNAMODB_CONTAINER_NAME}"; then
    log "DynamoDB Local is already running"
    return
  fi

  # 停止中のコンテナが残っている場合は削除する
  if docker ps -a --filter "name=${DYNAMODB_CONTAINER_NAME}" --format "{{.Names}}" \
       | grep -q "${DYNAMODB_CONTAINER_NAME}"; then
    log "Removing stopped DynamoDB Local container"
    docker rm "${DYNAMODB_CONTAINER_NAME}" >/dev/null
  fi

  log "Starting DynamoDB Local on port ${DYNAMODB_PORT}"
  docker run -d \
    --name "${DYNAMODB_CONTAINER_NAME}" \
    -p "${DYNAMODB_PORT}:8000" \
    amazon/dynamodb-local \
    -jar DynamoDBLocal.jar -sharedDb >/dev/null

  # 起動完了まで待機する
  log "Waiting for DynamoDB Local to be ready..."
  for i in $(seq 1 20); do
    if aws dynamodb list-tables \
         --endpoint-url "${DYNAMODB_ENDPOINT}" \
         --region "${AWS_DEFAULT_REGION}" \
         --output text >/dev/null 2>&1; then
      log "DynamoDB Local is ready"
      return
    fi
    sleep 1
  done
  echo "ERROR: DynamoDB Local did not start within 20 seconds" >&2
  exit 1
}

# ----- テーブルの作成 -----
create_table_if_not_exists() {
  if [[ "${SKIP_DYNAMODB}" == "true" ]]; then
    return
  fi

  # テーブルが既に存在するか確認する
  if aws dynamodb describe-table \
       --table-name "${TABLE_NAME}" \
       --endpoint-url "${DYNAMODB_ENDPOINT}" \
       --region "${AWS_DEFAULT_REGION}" \
       --output text >/dev/null 2>&1; then
    log "Table '${TABLE_NAME}' already exists"
    return
  fi

  log "Creating DynamoDB table: ${TABLE_NAME}"
  aws dynamodb create-table \
    --table-name "${TABLE_NAME}" \
    --attribute-definitions AttributeName=email,AttributeType=S \
    --key-schema AttributeName=email,KeyType=HASH \
    --billing-mode PAY_PER_REQUEST \
    --endpoint-url "${DYNAMODB_ENDPOINT}" \
    --region "${AWS_DEFAULT_REGION}" \
    --output text >/dev/null

  log "Table '${TABLE_NAME}' created"
}

# ----- Spring Boot の起動 -----
start_spring_boot() {
  cd "${PROJECT_DIR}"
  log "Starting Spring Boot (port 8080)"
  log "  APP_ENV              = ${APP_ENV}"
  log "  DYNAMODB_ENDPOINT    = ${DYNAMODB_ENDPOINT_URL}"
  log "  DYNAMODB_USERS_TABLE = ${DYNAMODB_USERS_TABLE}"
  log ""
  log "Endpoints:"
  log "  GET  http://localhost:8080/health"
  log "  POST http://localhost:8080/users"
  log "  GET  http://localhost:8080/users"
  log "  GET  http://localhost:8080/users/{email}"
  log "  PUT  http://localhost:8080/users/{email}"
  log "  GET  http://localhost:8080/configuration"
  log ""

  # Ctrl+C で Spring Boot が終了した後、DynamoDB Local は起動したままにする
  # 再起動時は --skip-dynamodb で高速起動できる
  mvn spring-boot:run
}

# ----- クリーンアップ（Ctrl+C 時） -----
cleanup() {
  echo ""
  log "Spring Boot stopped."
  log "DynamoDB Local is still running. To stop it:"
  log "  docker stop ${DYNAMODB_CONTAINER_NAME}"
}
trap cleanup EXIT

# ----- メイン処理 -----
start_dynamodb
create_table_if_not_exists
start_spring_boot