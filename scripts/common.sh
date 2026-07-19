#!/usr/bin/env bash
# 共通設定とヘルパー関数。他スクリプトから source して使う。
# 直接実行はしない。

export PROJECT_NAME="${PROJECT_NAME:-java-apm-sample}"
export AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION:-ap-northeast-1}"

# リポジトリ内のアプリ一覧（apps/ 配下のディレクトリ名と一致させる）。
# deploy-infra.sh / deploy-all.sh / delete-all.sh がこの一覧をループしてアプリ別スタックを扱う。
APPS=(user-company-api log-api)

# スタック名（アプリ非依存の共有スタックのみ）
export NETWORK_STACK="${PROJECT_NAME}-network"
export DYNAMODB_STACK="${PROJECT_NAME}-dynamodb"
export RDS_STACK="${PROJECT_NAME}-rds"
export SHARED_STACK="${PROJECT_NAME}-shared"

# プロジェクトルート・CloudFormation テンプレートのディレクトリ（このファイルの ../）
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
export PROJECT_DIR="${SCRIPT_DIR}/.."
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

# アプリ名から各種パスやスタック名を解決するヘルパー。
# アプリを追加する場合は APPS 配列と app_ecs_template() の case を更新する。
app_dir() {
  echo "${PROJECT_DIR}/apps/$1"
}

app_ecr_stack() {
  echo "${PROJECT_NAME}-ecr-$1"
}

app_ecs_stack() {
  echo "${PROJECT_NAME}-ecs-$1"
}

# アプリ名 → ECS サービススタックのテンプレートファイルを解決する
app_ecs_template() {
  case "$1" in
    user-company-api) echo "${CF_DIR}/07-ecs-user-company-api.yaml" ;;
    log-api) echo "${CF_DIR}/08-ecs-log-api.yaml" ;;
    *) log_error "Unknown app: $1 (expected one of: ${APPS[*]})"; exit 1 ;;
  esac
}

# アプリ名が APPS 配列に含まれているか検証する
validate_app_name() {
  local app_name="$1"
  local a
  for a in "${APPS[@]}"; do
    [[ "${a}" == "${app_name}" ]] && return 0
  done
  log_error "Unknown app: '${app_name}' (expected one of: ${APPS[*]})"
  exit 1
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

  local output
  output=$("${cmd[@]}" 2>&1)
  local exit_code=$?
  echo "${output}"

  # "No changes to deploy" が出力に含まれる場合は変更なし
  if echo "${output}" | grep -q "No changes to deploy"; then
    STACK_CHANGED=false
  else
    STACK_CHANGED=true
  fi

  log_success "Stack deployed: ${stack_name}"
  return "${exit_code}"
}