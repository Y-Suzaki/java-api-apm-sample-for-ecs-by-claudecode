#!/usr/bin/env bash
# ALB の mTLS 用に、おれおれ Root CA・ALB サーバー証明書・クライアント証明書を openssl で生成する。
# 生成物は certs/（.gitignore 済み、リポジトリにはコミットしない）に出力する。
#
# サーバー証明書の CN/SAN には共有スタック（06）の ALB DNS 名を使うため、
# 06-shared-cluster-lb.yaml が先にデプロイ済み（scripts/deploy-infra.sh 実行済み）である必要がある。
# 実行するたびに Root CA から作り直すため、既存の証明書はすべて無効になる
# （ALB に反映するには続けて scripts/deploy-mtls.sh を実行すること）。
#
# 使い方: bash scripts/generate-mtls-certs.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=scripts/common.sh
source "${SCRIPT_DIR}/common.sh"

CERTS_DIR="${PROJECT_DIR}/certs"
mkdir -p "${CERTS_DIR}"

ALB_DNS=$(aws cloudformation describe-stacks \
  --stack-name "${SHARED_STACK}" \
  --region "${AWS_DEFAULT_REGION}" \
  --query 'Stacks[0].Outputs[?OutputKey==`ALBDnsName`].OutputValue' \
  --output text 2>/dev/null || true)

if [[ -z "${ALB_DNS}" || "${ALB_DNS}" == "None" ]]; then
  log_error "ALB DNS name not found. Deploy ${SHARED_STACK} first: bash scripts/deploy-infra.sh"
  exit 1
fi

log "ALB DNS name: ${ALB_DNS}"

# ── Root CA（おれおれ CA） ──────────────────────────────────────────────
log "Generating root CA..."
openssl genrsa -out "${CERTS_DIR}/root-ca.key" 4096
openssl req -x509 -new -nodes \
  -key "${CERTS_DIR}/root-ca.key" \
  -sha256 -days 3650 \
  -subj "/CN=${PROJECT_NAME} Root CA/O=${PROJECT_NAME}" \
  -out "${CERTS_DIR}/root-ca.crt"

# ── ALB サーバー証明書（CN/SAN = ALB DNS 名） ───────────────────────────
log "Generating ALB server certificate for ${ALB_DNS}..."
openssl genrsa -out "${CERTS_DIR}/alb-server.key" 2048
openssl req -new \
  -key "${CERTS_DIR}/alb-server.key" \
  -subj "/CN=${ALB_DNS}" \
  -out "${CERTS_DIR}/alb-server.csr"
openssl x509 -req \
  -in "${CERTS_DIR}/alb-server.csr" \
  -CA "${CERTS_DIR}/root-ca.crt" -CAkey "${CERTS_DIR}/root-ca.key" -CAcreateserial \
  -days 825 -sha256 \
  -extfile <(printf "subjectAltName=DNS:%s" "${ALB_DNS}") \
  -out "${CERTS_DIR}/alb-server.crt"

# ── クライアント証明書（mTLS 動作確認用） ─────────────────────────────────
log "Generating client certificate..."
openssl genrsa -out "${CERTS_DIR}/client.key" 2048
openssl req -new \
  -key "${CERTS_DIR}/client.key" \
  -subj "/CN=${PROJECT_NAME}-client" \
  -out "${CERTS_DIR}/client.csr"
openssl x509 -req \
  -in "${CERTS_DIR}/client.csr" \
  -CA "${CERTS_DIR}/root-ca.crt" -CAkey "${CERTS_DIR}/root-ca.key" -CAcreateserial \
  -days 825 -sha256 \
  -out "${CERTS_DIR}/client.crt"

rm -f "${CERTS_DIR}"/*.csr "${CERTS_DIR}"/*.srl

log_success "Certificates generated under ${CERTS_DIR}/"
log "  Root CA          : certs/root-ca.crt (+ certs/root-ca.key)"
log "  ALB server cert  : certs/alb-server.crt (+ certs/alb-server.key)"
log "  Client cert      : certs/client.crt (+ certs/client.key)"
log ""
log "Next step: bash scripts/deploy-mtls.sh"
