#!/usr/bin/env bash
# CloudFormation の共有インフラスタック（ネットワーク、ECR x アプリ数、DynamoDB、RDS、
# ECS Cluster/ALB/NLB）をデプロイする。
# 各アプリの ECS サービス（log-api: 07、user-company-api: 09）は Docker イメージ URI が必要なため
# deploy-service.sh で行う。
#
# RDS MySQL マスターパスワードは RDS 管理シークレット（ManageMasterUserPassword）により
# AWS が自動生成・管理するため、デプロイ側でのパスワード指定は不要。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/common.sh
source "${SCRIPT_DIR}/common.sh"

log "=== Deploying infrastructure stacks ==="

# 01: ネットワーク（VPC / サブネット / IGW / NAT GW / DynamoDB・S3 VPC Endpoint）
deploy_stack "${NETWORK_STACK}" "${CF_DIR}/01-network.yaml" \
  "ProjectName=${PROJECT_NAME}"

# 02: ECR リポジトリ（アプリごとに1つ）
for app in "${APPS[@]}"; do
  deploy_stack "$(app_ecr_stack "${app}")" "${CF_DIR}/02-ecr.yaml" \
    "ProjectName=${PROJECT_NAME}" \
    "AppName=${app}"
done

# 03: DynamoDB users テーブル（user-company-api 用）
deploy_stack "${DYNAMODB_STACK}" "${CF_DIR}/03-dynamodb.yaml" \
  "ProjectName=${PROJECT_NAME}"

# 05: RDS MySQL 8.4（user-company-api の Company API 用）
deploy_stack "${RDS_STACK}" "${CF_DIR}/05-rds.yaml" \
  "ProjectName=${PROJECT_NAME}"

# 06: 共有 ECS Cluster / ALB / NLB / SG（全アプリ共有）
# AlbCertificateArn は空のまま（デフォルト、mTLS 無効）でデプロイする。
# ALB の DNS 名が確定してから scripts/generate-mtls-certs.sh + scripts/deploy-mtls.sh で
# 自己署名サーバー証明書を作成・ACM インポートし、AlbCertificateArn 付きで 06 を再デプロイして
# mTLS を有効化する（このスクリプトの後続ステップ）。
deploy_stack "${SHARED_STACK}" "${CF_DIR}/06-shared-cluster-lb.yaml" \
  "ProjectName=${PROJECT_NAME}"

log_success "=== Infrastructure stacks deployed ==="
log "Next step: bash scripts/build.sh <app>  (app: ${APPS[*]})"