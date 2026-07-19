#!/usr/bin/env bash
# 指定したアプリの Docker イメージをビルドして、そのアプリ専用の ECR リポジトリにプッシュする。
# ビルド完了後、イメージ URI を apps/<app>/.last_image_uri に保存する。
# deploy-service.sh はこのファイルを自動で読み込む。
#
# 使い方: bash scripts/build.sh <app>   (app: apps/ 配下のディレクトリ名。例: user-company-api, log-api)
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/common.sh
source "${SCRIPT_DIR}/common.sh"

APP_NAME="${1:-}"
if [[ -z "${APP_NAME}" ]]; then
  log_error "Usage: bash scripts/build.sh <app>  (app: ${APPS[*]})"
  exit 1
fi
validate_app_name "${APP_NAME}"

APP_DIR="$(app_dir "${APP_NAME}")"

# イメージタグ: git の短縮コミットハッシュ（git が使えない場合はタイムスタンプ）
IMAGE_TAG="${IMAGE_TAG:-$(git -C "${PROJECT_DIR}" rev-parse --short HEAD 2>/dev/null || date +%Y%m%d%H%M%S)}"

# ECR URI を CloudFormation Output から取得する
ECR_STACK="$(app_ecr_stack "${APP_NAME}")"
log "Getting ECR repository URI from stack: ${ECR_STACK}"
ECR_URI=$(aws cloudformation describe-stacks \
  --stack-name "${ECR_STACK}" \
  --region "${AWS_DEFAULT_REGION}" \
  --query 'Stacks[0].Outputs[?OutputKey==`ECRRepositoryUri`].OutputValue' \
  --output text)

if [[ -z "${ECR_URI}" ]]; then
  log_error "ECR repository URI not found. Run deploy-infra.sh first."
  exit 1
fi

IMAGE_URI="${ECR_URI}:${IMAGE_TAG}"
log "App        : ${APP_NAME}"
log "Image URI  : ${IMAGE_URI}"

# ECR ログイン（認証トークンは 12 時間有効）
log "Logging in to ECR"
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
aws ecr get-login-password --region "${AWS_DEFAULT_REGION}" \
  | docker login --username AWS --password-stdin \
      "${ACCOUNT_ID}.dkr.ecr.${AWS_DEFAULT_REGION}.amazonaws.com"

# Docker ビルド（ビルドコンテキストは apps/<app>/）
# --platform linux/amd64: ECS Fargate の X86_64 向けに明示指定（Apple Silicon Mac 等でも正しく動く）
log "Building Docker image (platform: linux/amd64)"
docker build \
  --platform linux/amd64 \
  -t "${IMAGE_URI}" \
  "${APP_DIR}"

# ECR プッシュ
log "Pushing image to ECR"
docker push "${IMAGE_URI}"

# イメージ URI を保存しておき deploy-service.sh で自動参照できるようにする
echo "${IMAGE_URI}" > "${APP_DIR}/.last_image_uri"
log_success "Image pushed: ${IMAGE_URI}"
log "Saved to apps/${APP_NAME}/.last_image_uri"
