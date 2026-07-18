#!/usr/bin/env bash
# Docker イメージをビルドして ECR にプッシュする。
# ビルド完了後、イメージ URI を .last_image_uri に保存する。
# deploy-service.sh は .last_image_uri を自動で読み込む。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="${SCRIPT_DIR}/.."
# shellcheck source=scripts/common.sh
source "${SCRIPT_DIR}/common.sh"

# イメージタグ: git の短縮コミットハッシュ（git が使えない場合はタイムスタンプ）
IMAGE_TAG="${IMAGE_TAG:-$(git -C "${PROJECT_DIR}" rev-parse --short HEAD 2>/dev/null || date +%Y%m%d%H%M%S)}"

# ECR URI を CloudFormation Output から取得する
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
log "Image URI: ${IMAGE_URI}"

# ECR ログイン（認証トークンは 12 時間有効）
log "Logging in to ECR"
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
aws ecr get-login-password --region "${AWS_DEFAULT_REGION}" \
  | docker login --username AWS --password-stdin \
      "${ACCOUNT_ID}.dkr.ecr.${AWS_DEFAULT_REGION}.amazonaws.com"

# Docker ビルド
# --platform linux/amd64: ECS Fargate の X86_64 向けに明示指定（Apple Silicon Mac 等でも正しく動く）
log "Building Docker image (platform: linux/amd64)"
docker build \
  --platform linux/amd64 \
  -t "${IMAGE_URI}" \
  "${PROJECT_DIR}"

# ECR プッシュ
log "Pushing image to ECR"
docker push "${IMAGE_URI}"

# イメージ URI を保存しておき deploy-service.sh で自動参照できるようにする
echo "${IMAGE_URI}" > "${PROJECT_DIR}/.last_image_uri"
log_success "Image pushed: ${IMAGE_URI}"
log "Saved to .last_image_uri"
