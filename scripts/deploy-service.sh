#!/usr/bin/env bash
# CloudFormation スタック 04（ECS + ALB）をデプロイし、ECS を強制再デプロイする。
#
# IMAGE_URI が未指定の場合は .last_image_uri から読み込む（build.sh が生成する）。
#
# RDS MySQL マスターパスワードは RDS 管理シークレット（ManageMasterUserPassword）を
# ImportValue で参照するため、デプロイ側でのパスワード指定は不要。
#
# 【強制デプロイが必要な理由】
# タスク定義の ImageUri が変わらない場合（再ビルドでも同タグを使った等）、
# CloudFormation は変更なしと判断して新しいイメージを反映しない。
# aws ecs update-service --force-new-deployment で常に最新イメージを起動させる。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="${SCRIPT_DIR}/.."
# shellcheck source=scripts/common.sh
source "${SCRIPT_DIR}/common.sh"

# IMAGE_URI の解決
if [[ -z "${IMAGE_URI:-}" ]]; then
  LAST_IMAGE_FILE="${PROJECT_DIR}/.last_image_uri"
  if [[ -f "${LAST_IMAGE_FILE}" ]]; then
    IMAGE_URI=$(cat "${LAST_IMAGE_FILE}")
    log "Using image URI from .last_image_uri: ${IMAGE_URI}"
  else
    log_error "IMAGE_URI is not set and .last_image_uri not found. Run build.sh first."
    exit 1
  fi
fi

log "=== Deploying ECS service stack ==="
log "Image URI: ${IMAGE_URI}"

deploy_stack "${ECS_STACK}" "${CF_DIR}/04-ecs-alb.yaml" \
  "ProjectName=${PROJECT_NAME}" \
  "ImageUri=${IMAGE_URI}" \
  "MySqlUsername=${MYSQL_DB_USERNAME:-appuser}"

# ECS クラスター名とサービス名を CloudFormation Output から取得する
ECS_CLUSTER=$(aws cloudformation describe-stacks \
  --stack-name "${ECS_STACK}" \
  --region "${AWS_DEFAULT_REGION}" \
  --query 'Stacks[0].Outputs[?OutputKey==`ECSClusterName`].OutputValue' \
  --output text)

ECS_SERVICE=$(aws cloudformation describe-stacks \
  --stack-name "${ECS_STACK}" \
  --region "${AWS_DEFAULT_REGION}" \
  --query 'Stacks[0].Outputs[?OutputKey==`ECSServiceName`].OutputValue' \
  --output text)

# CloudFormation に変更がなかった場合のみ force-new-deployment を実行する。
# 変更があった場合は CloudFormation が ECS デプロイを完了させているため不要。
# （force-new-deployment を重複実行すると正常稼働中のタスクが再置換されてしまう）
if [[ "${STACK_CHANGED}" == "false" ]]; then
  log "CloudFormation に変更なし。force-new-deployment で最新イメージを反映します: cluster=${ECS_CLUSTER} service=${ECS_SERVICE}"
  aws ecs update-service \
    --cluster "${ECS_CLUSTER}" \
    --service "${ECS_SERVICE}" \
    --force-new-deployment \
    --region "${AWS_DEFAULT_REGION}" \
    --output text >/dev/null
else
  log "CloudFormation がデプロイを完了済み。force-new-deployment はスキップします。"
fi

# ALB DNS 名を表示する
ALB_DNS=$(aws cloudformation describe-stacks \
  --stack-name "${ECS_STACK}" \
  --region "${AWS_DEFAULT_REGION}" \
  --query 'Stacks[0].Outputs[?OutputKey==`ALBDnsName`].OutputValue' \
  --output text)

log_success "=== ECS service deployed ==="
log "ALB endpoint : http://${ALB_DNS}"
log "Health check : curl http://${ALB_DNS}/health"
log ""
log "Note: ECS task startup takes 1-2 minutes. Wait before testing."
