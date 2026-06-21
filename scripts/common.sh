#!/usr/bin/env bash
# 共通設定とヘルパー関数。他スクリプトから source して使う。
# 直接実行はしない。

export PROJECT_NAME="${PROJECT_NAME:-java-apm-sample}"
export AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION:-ap-northeast-1}"

# スタック名
export NETWORK_STACK="${PROJECT_NAME}-network"
export ECR_STACK="${PROJECT_NAME}-ecr"
export DYNAMODB_STACK="${PROJECT_NAME}-dynamodb"
export ECS_STACK="${PROJECT_NAME}-ecs"

# CloudFormation テンプレートのディレクトリ（このファイルの ../cloudformation/）
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
export CF_DIR="${SCRIPT_DIR}/../cloudformation"

# AWS 認証情報チェック（未設定の場合は即エラー終了）
: "${AWS_ACCESS_KEY_ID:?AWS_ACCESS_KEY_ID is not set. Export it before running deploy scripts.}"
: "${AWS_SECRET_ACCESS_KEY:?AWS_SECRET_ACCESS_KEY is not set. Export it before running deploy scripts.}"

log() {
  printf '\033[1;36m[%s]\033[0m %s\n' "$(date +%H:%M:%S)" "$*"
}

log_success() {
  printf '\033[1;32m[%s]\033[0m %s\n' "$(date +%H:%M:%S)" "$*"
}

log_error() {
  printf '\033[1;31m[%s] ERROR:\033[0m %s\n' "$(date +%H:%M:%S)" "$*" >&2
}

# CloudFormation スタックをデプロイする（存在しない場合は作成、存在する場合は更新）。
# 変更なしの場合は cloudformation deploy がエラーを返さず正常終了する。
#
# Usage: deploy_stack <stack-name> <template-file> [ParameterKey=Value ...]
deploy_stack() {
  local stack_name="$1"
  local template_file="$2"
  shift 2
  local parameters=("$@")

  log "Deploying stack: ${stack_name}"

  local cmd=(
    aws cloudformation deploy
    --stack-name "${stack_name}"
    --template-file "${template_file}"
    --capabilities CAPABILITY_IAM CAPABILITY_NAMED_IAM
    --region "${AWS_DEFAULT_REGION}"
  )

  if [[ ${#parameters[@]} -gt 0 ]]; then
    cmd+=(--parameter-overrides "${parameters[@]}")
  fi

  "${cmd[@]}"
  log_success "Stack deployed: ${stack_name}"
}
