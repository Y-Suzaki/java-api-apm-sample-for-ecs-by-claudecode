#!/usr/bin/env bash
# インフラ構築・Docker ビルド・ECS サービスデプロイを一括実行する。
# 個別に実行する場合:
#   bash scripts/deploy-infra.sh   # ネットワーク / ECR / DynamoDB
#   bash scripts/build.sh          # Docker ビルド + ECR プッシュ
#   bash scripts/deploy-service.sh # ECS + ALB デプロイ
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/common.sh
source "${SCRIPT_DIR}/common.sh"

log "=== Full deployment start ==="
log "Project : ${PROJECT_NAME}"
log "Region  : ${AWS_DEFAULT_REGION}"
log ""

bash "${SCRIPT_DIR}/deploy-infra.sh"
bash "${SCRIPT_DIR}/build.sh"
bash "${SCRIPT_DIR}/deploy-service.sh"

log_success "=== Full deployment completed ==="