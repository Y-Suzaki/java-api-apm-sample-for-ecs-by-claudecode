#!/usr/bin/env bash
# 指定したアプリの ECS サービススタック（07-ecs-user-company-api.yaml / 08-ecs-log-api.yaml）を
# デプロイし、ECS を強制再デプロイする。
#
# IMAGE_URI が未指定の場合は apps/<app>/.last_image_uri から読み込む（build.sh が生成する）。
#
# RDS MySQL マスターパスワードは RDS 管理シークレット（ManageMasterUserPassword）を
# ImportValue で参照するため、デプロイ側でのパスワード指定は不要。
#
# 【強制デプロイが必要な理由】
# タスク定義の ImageUri が変わらない場合（再ビルドでも同タグを使った等）、
# CloudFormation は変更なしと判断して新しいイメージを反映しない。
# aws ecs update-service --force-new-deployment で常に最新イメージを起動させる。
#
# 使い方: bash scripts/deploy-service.sh <app>   (app: apps/ 配下のディレクトリ名。例: user-company-api, log-api)
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/common.sh
source "${SCRIPT_DIR}/common.sh"

APP_NAME="${1:-}"
if [[ -z "${APP_NAME}" ]]; then
  log_error "Usage: bash scripts/deploy-service.sh <app>  (app: ${APPS[*]})"
  exit 1
fi
validate_app_name "${APP_NAME}"

APP_DIR="$(app_dir "${APP_NAME}")"
ECS_STACK="$(app_ecs_stack "${APP_NAME}")"
ECS_TEMPLATE="$(app_ecs_template "${APP_NAME}")"

# IMAGE_URI の解決
if [[ -z "${IMAGE_URI:-}" ]]; then
  LAST_IMAGE_FILE="${APP_DIR}/.last_image_uri"
  if [[ -f "${LAST_IMAGE_FILE}" ]]; then
    IMAGE_URI=$(cat "${LAST_IMAGE_FILE}")
    log "Using image URI from apps/${APP_NAME}/.last_image_uri: ${IMAGE_URI}"
  else
    log_error "IMAGE_URI is not set and apps/${APP_NAME}/.last_image_uri not found. Run build.sh ${APP_NAME} first."
    exit 1
  fi
fi

log "=== Deploying ECS service stack: ${APP_NAME} ==="
log "Image URI: ${IMAGE_URI}"

# アプリごとに追加パラメータが異なる（user-company-api のみ MySqlUsername を持つ）
PARAMS=("ProjectName=${PROJECT_NAME}" "ImageUri=${IMAGE_URI}")
if [[ "${APP_NAME}" == "user-company-api" ]]; then
  PARAMS+=("MySqlUsername=${MYSQL_DB_USERNAME:-appuser}")
fi

deploy_stack "${ECS_STACK}" "${ECS_TEMPLATE}" "${PARAMS[@]}"

# ECS クラスター名は共有スタック（06）の Output から取得する
ECS_CLUSTER=$(aws cloudformation describe-stacks \
  --stack-name "${SHARED_STACK}" \
  --region "${AWS_DEFAULT_REGION}" \
  --query 'Stacks[0].Outputs[?OutputKey==`ECSClusterName`].OutputValue' \
  --output text)

# ECS サービス名はこのアプリのスタック（07/08）の Output から取得する
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

# ALB DNS 名は共有スタック（06）の Output から取得する
ALB_DNS=$(aws cloudformation describe-stacks \
  --stack-name "${SHARED_STACK}" \
  --region "${AWS_DEFAULT_REGION}" \
  --query 'Stacks[0].Outputs[?OutputKey==`ALBDnsName`].OutputValue' \
  --output text)

log_success "=== ECS service deployed: ${APP_NAME} ==="
log "ALB endpoint : http://${ALB_DNS}"
if [[ "${APP_NAME}" == "log-api" ]]; then
  log "Try it       : curl -X POST http://${ALB_DNS}/logs -d 'hello'"
else
  log "Health check : curl http://${ALB_DNS}/health"
fi
log ""
log "Note: ECS task startup takes 1-2 minutes. Wait before testing."
