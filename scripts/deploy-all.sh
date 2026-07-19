#!/usr/bin/env bash
# インフラ構築・全アプリの Docker ビルド・ECS サービスデプロイを一括実行する。
# 個別に実行する場合:
#   bash scripts/deploy-infra.sh              # ネットワーク / ECR(全アプリ) / DynamoDB / RDS / 共有ECS基盤
#   bash scripts/build.sh <app>                # Docker ビルド + ECR プッシュ（アプリ単位）
#   bash scripts/deploy-service.sh <app>       # ECS サービスデプロイ（アプリ単位）
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/common.sh
source "${SCRIPT_DIR}/common.sh"

log "=== Full deployment start ==="
log "Project : ${PROJECT_NAME}"
log "Region  : ${AWS_DEFAULT_REGION}"
log "Apps    : ${APPS[*]}"
log ""

bash "${SCRIPT_DIR}/deploy-infra.sh"

for app in "${APPS[@]}"; do
  bash "${SCRIPT_DIR}/build.sh" "${app}"
  bash "${SCRIPT_DIR}/deploy-service.sh" "${app}"
done

log_success "=== Full deployment completed ==="