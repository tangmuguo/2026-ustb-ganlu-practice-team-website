#!/usr/bin/env bash
# Safely install a renewed Tencent Cloud certificate for ganlu.site.
#
# Run this script as root, for example:
#   sudo /usr/local/sbin/renew-ganlu-tls
#
# By default it reads the two Nginx-format files uploaded to:
#   /home/ubuntu/ssl-upload/ganlu.site_bundle.crt
#   /home/ubuntu/ssl-upload/ganlu.site.key
# Different file names can be supplied as two positional arguments.

set -euo pipefail

readonly DOMAIN="ganlu.site"
readonly CERT_DIR="/etc/nginx/ssl/${DOMAIN}"
readonly DEFAULT_UPLOAD_DIR="/home/ubuntu/ssl-upload"
readonly DEFAULT_CERT="${DEFAULT_UPLOAD_DIR}/${DOMAIN}_bundle.crt"
readonly DEFAULT_KEY="${DEFAULT_UPLOAD_DIR}/${DOMAIN}.key"

usage() {
  cat <<'EOF'
Usage:
  sudo renew-ganlu-tls [CERTIFICATE_BUNDLE PRIVATE_KEY]

Without arguments, the script uses:
  /home/ubuntu/ssl-upload/ganlu.site_bundle.crt
  /home/ubuntu/ssl-upload/ganlu.site.key

It validates the certificate/key pair, backs up the currently installed
certificate, runs nginx -t, then reloads only Nginx. It never changes the
database, application WAR, frontend files, or systemd service.
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

if [[ "$#" -eq 0 ]]; then
  certificate_file="$DEFAULT_CERT"
  private_key_file="$DEFAULT_KEY"
elif [[ "$#" -eq 2 ]]; then
  certificate_file="$1"
  private_key_file="$2"
else
  usage >&2
  exit 2
fi

if [[ "$EUID" -ne 0 ]]; then
  echo "请使用 sudo 运行此脚本。" >&2
  exit 2
fi

if [[ ! -r "$certificate_file" ]]; then
  echo "找不到或无法读取证书文件：$certificate_file" >&2
  exit 1
fi

if [[ ! -r "$private_key_file" ]]; then
  echo "找不到或无法读取私钥文件：$private_key_file" >&2
  exit 1
fi

if ! openssl x509 -in "$certificate_file" -noout >/dev/null 2>&1; then
  echo "证书文件不是有效的 X.509 PEM 证书：$certificate_file" >&2
  exit 1
fi

if ! openssl pkey -in "$private_key_file" -noout >/dev/null 2>&1; then
  echo "私钥文件无效、已加密或无法读取：$private_key_file" >&2
  exit 1
fi

if ! openssl x509 -in "$certificate_file" -noout -ext subjectAltName 2>/dev/null | grep -Fq "DNS:${DOMAIN}"; then
  echo "证书未包含域名 ${DOMAIN}，已停止，未替换现有证书。" >&2
  exit 1
fi

certificate_public_key="$(openssl x509 -in "$certificate_file" -pubkey -noout | openssl pkey -pubin -outform DER | sha256sum | awk '{print $1}')"
private_key_public_key="$(openssl pkey -in "$private_key_file" -pubout -outform DER | sha256sum | awk '{print $1}')"

if [[ "$certificate_public_key" != "$private_key_public_key" ]]; then
  echo "证书与私钥不匹配，已停止，未替换现有证书。" >&2
  exit 1
fi

if ! openssl x509 -in "$certificate_file" -checkend 86400 -noout >/dev/null; then
  echo "证书将在 24 小时内到期或已经过期，已停止。" >&2
  exit 1
fi

timestamp="$(date +%Y%m%d-%H%M%S)"
backup_dir="${CERT_DIR}/backups/${timestamp}"
had_previous_certificate=false

if [[ -f "${CERT_DIR}/fullchain.crt" && -f "${CERT_DIR}/privkey.key" ]]; then
  had_previous_certificate=true
  install -d -m 0700 "$backup_dir"
  install -o root -g root -m 0644 "${CERT_DIR}/fullchain.crt" "${backup_dir}/fullchain.crt"
  install -o root -g root -m 0600 "${CERT_DIR}/privkey.key" "${backup_dir}/privkey.key"
  echo "已备份当前证书到：$backup_dir"
fi

install -d -m 0750 "$CERT_DIR"
install -o root -g root -m 0644 "$certificate_file" "${CERT_DIR}/fullchain.crt"
install -o root -g root -m 0600 "$private_key_file" "${CERT_DIR}/privkey.key"

if ! nginx -t; then
  echo "Nginx 配置校验失败。" >&2
  if [[ "$had_previous_certificate" == true ]]; then
    echo "正在还原刚才备份的旧证书……" >&2
    install -o root -g root -m 0644 "${backup_dir}/fullchain.crt" "${CERT_DIR}/fullchain.crt"
    install -o root -g root -m 0600 "${backup_dir}/privkey.key" "${CERT_DIR}/privkey.key"
    nginx -t || true
  fi
  exit 1
fi

systemctl reload nginx

echo
echo "续期证书已生效："
openssl x509 -in "${CERT_DIR}/fullchain.crt" -noout -subject -issuer -dates
echo "Nginx 状态：$(systemctl is-active nginx)"
echo "提示：确认网站正常后，请从 /home/ubuntu/ssl-upload/ 手动移除旧的上传副本，避免私钥长期保留在家目录。"
