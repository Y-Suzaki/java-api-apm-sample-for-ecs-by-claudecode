#!/usr/bin/env bash
# CloudFormation スタック 01〜03 をデプロイする（ネットワーク、ECR、DynamoDB）。
# ECS サービス（04）は Docker イメージ URI が必要なため deploy-service.sh で行う。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/common.sh
source "${SCRIPT_DIR}/common.sh"

log "=== Deploying infrastructure stacks ==="

# 01: ネットワーク（VPC / サブネット / IGW / NAT GW / DynamoDB VPC Endpoint）
deploy_stack "${NETWORK_STACK}" "${CF_DIR}/01-network.yaml" \
  "ProjectName=${PROJECT_NAME}"

# 02: ECR リポジトリ
deploy_stack "${ECR_STACK}" "${CF_DIR}/02-ecr.yaml" \
  "ProjectName=${PROJECT_NAME}"

# 03: DynamoDB users テーブル
deploy_stack "${DYNAMODB_STACK}" "${CF_DIR}/03-dynamodb.yaml" \
  "ProjectName=${PROJECT_NAME}"

log_success "=== Infrastructure stacks deployed ==="
log "Next step: bash scripts/build.sh"
