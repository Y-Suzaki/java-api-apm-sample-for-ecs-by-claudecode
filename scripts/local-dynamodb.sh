#!/usr/bin/env bash
# DynamoDB Local の起動とテーブル作成のみを行うスクリプト。
# Spring Boot は IDE から別途起動すること。
#
# IDE から Spring Boot を起動する際の環境変数設定:
#   AWS_ACCESS_KEY_ID      = dummy
#   AWS_SECRET_ACCESS_KEY  = dummy
#   AWS_DEFAULT_REGION     = ap-northeast-1
#   DYNAMODB_ENDPOINT_URL  = http://localhost:8000
#   DYNAMODB_USERS_TABLE   = users
#   APP_ENV                = local
#
# 使い方:
#   bash scripts/local-dynamodb.sh          # 起動 + テーブル作成
#   bash scripts/local-dynamodb.sh --stop   # DynamoDB Local を停止
set -euo pipefail

DYNAMODB_PORT=8000
DYNAMODB_CONTAINER_NAME="dynamodb-local"
TABLE_NAME="${DYNAMODB_USERS_TABLE:-users}"
DYNAMODB_ENDPOINT="http://localhost:${DYNAMODB_PORT}"
AWS_REGION_LOCAL="${AWS_DEFAULT_REGION:-ap-northeast-1}"

# DynamoDB Local への接続に使うダミー認証情報
export AWS_ACCESS_KEY_ID="${AWS_ACCESS_KEY_ID:-dummy}"
export AWS_SECRET_ACCESS_KEY="${AWS_SECRET_ACCESS_KEY:-dummy}"

log() {
  printf '\033[1;36m[%s]\033[0m %s\n' "$(date +%H:%M:%S)" "$*"
}

# --stop オプションで DynamoDB Local を停止する
if [[ "${1:-}" == "--stop" ]]; then
  log "Stopping DynamoDB Local"
  docker stop "${DYNAMODB_CONTAINER_NAME}" && docker rm "${DYNAMODB_CONTAINER_NAME}"
  log "Done"
  exit 0
fi

# ----- DynamoDB Local の起動 -----
if docker ps --filter "name=${DYNAMODB_CONTAINER_NAME}" --filter "status=running" \
     --format "{{.Names}}" | grep -q "${DYNAMODB_CONTAINER_NAME}"; then
  log "DynamoDB Local is already running on port ${DYNAMODB_PORT}"
else
  # 停止中のコンテナが残っている場合は削除する
  if docker ps -a --filter "name=${DYNAMODB_CONTAINER_NAME}" --format "{{.Names}}" \
       | grep -q "${DYNAMODB_CONTAINER_NAME}"; then
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
         --region "${AWS_REGION_LOCAL}" \
         --output text >/dev/null 2>&1; then
      break
    fi
    sleep 1
  done
  log "DynamoDB Local is ready"
fi

# ----- テーブルの作成 -----
if aws dynamodb describe-table \
     --table-name "${TABLE_NAME}" \
     --endpoint-url "${DYNAMODB_ENDPOINT}" \
     --region "${AWS_REGION_LOCAL}" \
     --output text >/dev/null 2>&1; then
  log "Table '${TABLE_NAME}' already exists"
else
  log "Creating DynamoDB table: ${TABLE_NAME}"
  aws dynamodb create-table \
    --table-name "${TABLE_NAME}" \
    --attribute-definitions AttributeName=email,AttributeType=S \
    --key-schema AttributeName=email,KeyType=HASH \
    --billing-mode PAY_PER_REQUEST \
    --endpoint-url "${DYNAMODB_ENDPOINT}" \
    --region "${AWS_REGION_LOCAL}" \
    --output text >/dev/null
  log "Table '${TABLE_NAME}' created"
fi

# ----- IDE 向けの環境変数設定値を表示する -----
echo ""
log "DynamoDB Local is running. Set the following env vars in your IDE run configuration:"
echo ""
echo "  AWS_ACCESS_KEY_ID      = dummy"
echo "  AWS_SECRET_ACCESS_KEY  = dummy"
echo "  AWS_DEFAULT_REGION     = ${AWS_REGION_LOCAL}"
echo "  DYNAMODB_ENDPOINT_URL  = ${DYNAMODB_ENDPOINT}"
echo "  DYNAMODB_USERS_TABLE   = ${TABLE_NAME}"
echo "  APP_ENV                = local"
echo ""
log "To stop DynamoDB Local: bash scripts/local-dynamodb.sh --stop"
