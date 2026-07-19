#!/usr/bin/env bash
# ローカル開発用インフラ（MySQL + DynamoDB Local）を docker compose で管理するスクリプト。
# Spring Boot は IDE または local-run.sh から別途起動すること。
#
# IDE から Spring Boot を起動する際の環境変数設定:
#   AWS_ACCESS_KEY_ID      = dummy
#   AWS_SECRET_ACCESS_KEY  = dummy
#   AWS_DEFAULT_REGION     = ap-northeast-1
#   DYNAMODB_ENDPOINT_URL  = http://localhost:8000
#   DYNAMODB_USERS_TABLE   = users
#   MYSQL_URL              = jdbc:mysql://localhost:3306/sampledb?serverTimezone=UTC&characterEncoding=UTF-8
#   MYSQL_USER             = appuser
#   MYSQL_PASSWORD         = apppassword
#   APP_ENV                = local
#
# 使い方:
#   bash scripts/local-infra.sh          # 起動 + DynamoDB テーブル作成
#   bash scripts/local-infra.sh --stop   # 全コンテナを停止・削除
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="${SCRIPT_DIR}/.."

DYNAMODB_PORT=8000
DYNAMODB_ENDPOINT="http://localhost:${DYNAMODB_PORT}"
TABLE_NAME="${DYNAMODB_USERS_TABLE:-users}"
AWS_REGION_LOCAL="${AWS_DEFAULT_REGION:-ap-northeast-1}"

# DynamoDB Local への接続に使うダミー認証情報
export AWS_ACCESS_KEY_ID="${AWS_ACCESS_KEY_ID:-dummy}"
export AWS_SECRET_ACCESS_KEY="${AWS_SECRET_ACCESS_KEY:-dummy}"

log() {
  printf '\033[1;36m[%s]\033[0m %s\n' "$(date +%H:%M:%S)" "$*"
}

# user-company-api 専用のローカルインフラ（MySQL + DynamoDB Local）。log-api は外部依存がないため対象外。
COMPOSE_FILE="${PROJECT_DIR}/apps/user-company-api/docker-compose.yml"

# --stop オプションで全コンテナを停止・削除する
if [[ "${1:-}" == "--stop" ]]; then
  log "Stopping all local infra containers"
  docker compose -f "${COMPOSE_FILE}" down
  log "Done"
  exit 0
fi

# ----- コンテナの起動 -----
log "Starting local infra (MySQL + DynamoDB Local)"
# --wait: MySQL の healthcheck が通過するまで待機する
docker compose -f "${COMPOSE_FILE}" up -d --wait

# ----- DynamoDB Local の疎通確認 -----
# dynamodb-local は healthcheck 未設定のため、AWS CLI でポーリングする
log "Waiting for DynamoDB Local to be ready..."
for i in $(seq 1 20); do
  if aws dynamodb list-tables \
       --endpoint-url "${DYNAMODB_ENDPOINT}" \
       --region "${AWS_REGION_LOCAL}" \
       --output text >/dev/null 2>&1; then
    log "DynamoDB Local is ready"
    break
  fi
  if [[ "${i}" -eq 20 ]]; then
    echo "ERROR: DynamoDB Local did not start within 20 seconds" >&2
    exit 1
  fi
  sleep 1
done

# ----- DynamoDB テーブルの作成 -----
if aws dynamodb describe-table \
     --table-name "${TABLE_NAME}" \
     --endpoint-url "${DYNAMODB_ENDPOINT}" \
     --region "${AWS_REGION_LOCAL}" \
     --output text >/dev/null 2>&1; then
  log "DynamoDB table '${TABLE_NAME}' already exists"
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
  log "DynamoDB table '${TABLE_NAME}' created"
fi

# ----- IDE 向けの環境変数設定値を表示する -----
echo ""
log "Local infra is ready. Set the following env vars in your IDE run configuration:"
echo ""
echo "  AWS_ACCESS_KEY_ID      = dummy"
echo "  AWS_SECRET_ACCESS_KEY  = dummy"
echo "  AWS_DEFAULT_REGION     = ${AWS_REGION_LOCAL}"
echo "  DYNAMODB_ENDPOINT_URL  = ${DYNAMODB_ENDPOINT}"
echo "  DYNAMODB_USERS_TABLE   = ${TABLE_NAME}"
echo "  MYSQL_URL              = jdbc:mysql://localhost:3306/sampledb?serverTimezone=UTC&characterEncoding=UTF-8"
echo "  MYSQL_USER             = appuser"
echo "  MYSQL_PASSWORD         = apppassword"
echo "  APP_ENV                = local"
echo ""
log "To stop: bash scripts/local-infra.sh --stop"
