#!/usr/bin/env bash
# おれおれ Root CA を ALB の mTLS Trust Store 用に S3 へアップロードし、ALB サーバー証明書を
# ACM にインポートしたうえで、06-shared-cluster-lb.yaml を AlbCertificateArn 付きで再デプロイして
# ALB の mTLS（HTTPS:443 + Verify モード）を有効化する。新規デプロイ専用（1回だけ実行する想定）。
#
# 事前に scripts/generate-mtls-certs.sh で certs/ 配下に証明書一式を生成しておくこと。
#
# 使い方: bash scripts/deploy-mtls.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/common.sh
source "${SCRIPT_DIR}/common.sh"

CERTS_DIR="${PROJECT_DIR}/certs"
ROOT_CA_CERT="${CERTS_DIR}/root-ca.crt"
SERVER_CERT="${CERTS_DIR}/alb-server.crt"
SERVER_KEY="${CERTS_DIR}/alb-server.key"

for f in "${ROOT_CA_CERT}" "${SERVER_CERT}" "${SERVER_KEY}"; do
  if [[ ! -f "${f}" ]]; then
    log_error "${f} not found. Run scripts/generate-mtls-certs.sh first."
    exit 1
  fi
done

# ── Root CA を Trust Store 用に S3 へアップロード ────────────────────────
# 06-shared-cluster-lb.yaml の ALBTrustStore（Condition: HasAlbCertificate）が
# このオブジェクトを参照するため、スタックデプロイより先にアップロードしておく必要がある。
OPENAPI_BUCKET=$(aws cloudformation describe-stacks \
  --stack-name "${SHARED_STACK}" \
  --region "${AWS_DEFAULT_REGION}" \
  --query 'Stacks[0].Outputs[?OutputKey==`OpenApiBucketName`].OutputValue' \
  --output text)

log "Uploading root CA to s3://${OPENAPI_BUCKET}/mtls/root-ca-bundle.pem"
aws s3 cp "${ROOT_CA_CERT}" "s3://${OPENAPI_BUCKET}/mtls/root-ca-bundle.pem" \
  --region "${AWS_DEFAULT_REGION}"

# ── ALB サーバー証明書を ACM にインポート（証明書チェーンに Root CA を含める） ─
log "Importing ALB server certificate into ACM"
CERT_ARN=$(aws acm import-certificate \
  --certificate "fileb://${SERVER_CERT}" \
  --private-key "fileb://${SERVER_KEY}" \
  --certificate-chain "fileb://${ROOT_CA_CERT}" \
  --region "${AWS_DEFAULT_REGION}" \
  --query CertificateArn --output text)
log_success "ACM certificate: ${CERT_ARN}"

# ── 06-shared-cluster-lb.yaml を AlbCertificateArn 付きで再デプロイ ─────────
# Condition (HasAlbCertificate) により ALBListener が HTTPS:443 + mTLS(Verify) に切り替わり、
# HTTP:80 は 443 へのリダイレクト専用リスナーになる。
deploy_stack "${SHARED_STACK}" "${CF_DIR}/06-shared-cluster-lb.yaml" \
  "ProjectName=${PROJECT_NAME}" \
  "AlbCertificateArn=${CERT_ARN}"

ALB_DNS=$(aws cloudformation describe-stacks \
  --stack-name "${SHARED_STACK}" \
  --region "${AWS_DEFAULT_REGION}" \
  --query 'Stacks[0].Outputs[?OutputKey==`ALBDnsName`].OutputValue' \
  --output text)

log_success "=== mTLS enabled on ALB ==="
log "Endpoint : https://${ALB_DNS}"
log "Try it   : curl https://${ALB_DNS}/health --cacert certs/root-ca.crt --cert certs/client.crt --key certs/client.key"
log "Without a client certificate the TLS handshake is rejected (mTLS Verify mode)."
