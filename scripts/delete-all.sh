#!/usr/bin/env bash
# 全 CloudFormation スタックを逆順で削除する。
#
# 削除順: ECS(全アプリ) → 共有(ECS Cluster/ALB/NLB) → RDS → DynamoDB → ECR(全アプリ) → Network
# ※ DynamoDB テーブルのデータ・RDS インスタンスも削除される。
# ※ ECR のイメージは stack 削除前に一括削除する（残存イメージがあると DeleteRepository が失敗するため）。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/common.sh
source "${SCRIPT_DIR}/common.sh"

log "=== Deleting all stacks ==="
log "Project : ${PROJECT_NAME}"
log "Region  : ${AWS_DEFAULT_REGION}"
log ""
log "WARNING: This will permanently delete all resources including DynamoDB data."
read -r -p "Are you sure? [y/N] " confirm
if [[ "${confirm}" != "y" && "${confirm}" != "Y" ]]; then
  log "Aborted."
  exit 0
fi

# スタックを削除して完了を待つ。スタックが存在しない場合はスキップする。
delete_stack() {
  local stack_name="$1"

  if ! aws cloudformation describe-stacks \
       --stack-name "${stack_name}" \
       --region "${AWS_DEFAULT_REGION}" \
       --output text >/dev/null 2>&1; then
    log "Stack not found (skipping): ${stack_name}"
    return
  fi

  log "Deleting stack: ${stack_name}"
  aws cloudformation delete-stack \
    --stack-name "${stack_name}" \
    --region "${AWS_DEFAULT_REGION}"

  log "Waiting for deletion to complete: ${stack_name}"
  aws cloudformation wait stack-delete-complete \
    --stack-name "${stack_name}" \
    --region "${AWS_DEFAULT_REGION}"

  log_success "Stack deleted: ${stack_name}"
}

# ECR スタック削除前にイメージを全削除する。
# ECR リポジトリにイメージが残っていると CloudFormation の DeleteRepository が失敗する。
delete_ecr_images() {
  local ecr_stack_name="$1"
  local ecr_name
  ecr_name=$(aws cloudformation describe-stacks \
    --stack-name "${ecr_stack_name}" \
    --region "${AWS_DEFAULT_REGION}" \
    --query 'Stacks[0].Outputs[?OutputKey==`ECRRepositoryName`].OutputValue' \
    --output text 2>/dev/null || true)

  [[ -z "${ecr_name}" ]] && return

  local image_ids_json
  image_ids_json=$(aws ecr list-images \
    --repository-name "${ecr_name}" \
    --region "${AWS_DEFAULT_REGION}" \
    --query 'imageIds' \
    --output json 2>/dev/null || echo "[]")

  if [[ "${image_ids_json}" == "[]" ]]; then
    log "No images in ECR repository: ${ecr_name}"
    return
  fi

  log "Deleting all images from ECR: ${ecr_name}"
  # --cli-input-json で image_ids_json を JSON として渡す
  aws ecr batch-delete-image \
    --repository-name "${ecr_name}" \
    --region "${AWS_DEFAULT_REGION}" \
    --cli-input-json "{\"repositoryName\":\"${ecr_name}\",\"imageIds\":${image_ids_json}}" \
    --output text >/dev/null
  log_success "ECR images deleted"
}

# 逆順で削除する（依存関係の逆順）
# ECS(07/08) は 05 (RDS) / 03 (DynamoDB) / 06 (共有基盤) に依存するため最初に削除
for app in "${APPS[@]}"; do
  delete_stack "$(app_ecs_stack "${app}")"
done
# 06 (共有 ECS Cluster/ALB/NLB) は 01 (Network) に依存するため ECS 削除後に削除
delete_stack "${SHARED_STACK}"
# 05 (RDS) は 01 (Network) に依存するため ECS 削除後に削除
delete_stack "${RDS_STACK}"
delete_stack "${DYNAMODB_STACK}"
for app in "${APPS[@]}"; do
  delete_ecr_images "$(app_ecr_stack "${app}")"
  delete_stack "$(app_ecr_stack "${app}")"
done
delete_stack "${NETWORK_STACK}"

log_success "=== All stacks deleted ==="