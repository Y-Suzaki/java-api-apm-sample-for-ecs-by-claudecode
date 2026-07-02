#!/usr/bin/env bash
# ローカル開発用の起動スクリプト。
# MySQL + DynamoDB Local（Docker）を起動し、Spring Boot を起動する。
#
# 前提条件:
#   - Docker が起動していること
#   - AWS CLI がインストールされていること（DynamoDB テーブル作成に使用）
#   - mvn コマンドが PATH に存在すること
#
# 使い方:
#   bash scripts/local-run.sh                # インフラ起動 + Spring Boot 起動
#   bash scripts/local-run.sh --skip-infra   # インフラが起動済みの場合にスキップ
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="${SCRIPT_DIR}/.."

# Spring Boot 起動時に注入する環境変数
export AWS_ACCESS_KEY_ID="${AWS_ACCESS_KEY_ID:-dummy}"
export AWS_SECRET_ACCESS_KEY="${AWS_SECRET_ACCESS_KEY:-dummy}"
export AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION:-ap-northeast-1}"
export AWS_REGION="${AWS_DEFAULT_REGION}"
export DYNAMODB_ENDPOINT_URL="http://localhost:8000"
export DYNAMODB_USERS_TABLE="${DYNAMODB_USERS_TABLE:-users}"
export MYSQL_URL="${MYSQL_URL:-jdbc:mysql://localhost:3306/sampledb?serverTimezone=UTC&characterEncoding=UTF-8}"
export MYSQL_USER="${MYSQL_USER:-appuser}"
export MYSQL_PASSWORD="${MYSQL_PASSWORD:-apppassword}"
export APP_ENV="local"

SKIP_INFRA=false
for arg in "$@"; do
  [[ "$arg" == "--skip-infra" ]] && SKIP_INFRA=true
done

log() {
  printf '\033[1;36m[%s]\033[0m %s\n' "$(date +%H:%M:%S)" "$*"
}

# ----- インフラの起動 -----
if [[ "${SKIP_INFRA}" == "false" ]]; then
  bash "${SCRIPT_DIR}/local-infra.sh"
fi

# ----- Spring Boot の起動 -----
cd "${PROJECT_DIR}"
log "Starting Spring Boot (port 8080)"
log "  APP_ENV              = ${APP_ENV}"
log "  DYNAMODB_ENDPOINT    = ${DYNAMODB_ENDPOINT_URL}"
log "  DYNAMODB_USERS_TABLE = ${DYNAMODB_USERS_TABLE}"
log "  MYSQL_URL            = ${MYSQL_URL}"
log ""
log "Endpoints:"
log "  GET  http://localhost:8080/health"
log "  POST http://localhost:8080/users"
log "  GET  http://localhost:8080/users"
log "  GET  http://localhost:8080/users/{email}"
log "  PUT  http://localhost:8080/users/{email}"
log "  GET  http://localhost:8080/configuration"
log "  POST http://localhost:8080/companies"
log "  GET  http://localhost:8080/companies"
log "  GET  http://localhost:8080/companies/{id}"
log "  PUT  http://localhost:8080/companies/{id}"
log "  DEL  http://localhost:8080/companies/{id}"
log ""

# Ctrl+C で Spring Boot が終了してもコンテナは起動したままにする
# 停止する場合: bash scripts/local-infra.sh --stop
cleanup() {
  echo ""
  log "Spring Boot stopped."
  log "Local infra containers are still running."
  log "To stop them: bash scripts/local-infra.sh --stop"
}
trap cleanup EXIT

mvn spring-boot:run
