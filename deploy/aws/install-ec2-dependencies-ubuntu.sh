#!/usr/bin/env bash
set -euo pipefail

APP_DIR="${APP_DIR:-/opt/ott}"
SWAP_SIZE="${SWAP_SIZE:-2G}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

sudo apt-get update
sudo apt-get upgrade -y
sudo apt-get install -y ca-certificates curl gnupg unzip jq nginx certbot python3-certbot-nginx

if ! command -v docker >/dev/null 2>&1; then
  curl -fsSL https://get.docker.com | sudo sh
fi

sudo systemctl enable --now docker
sudo usermod -aG docker "${SUDO_USER:-ubuntu}" || true

if [[ -f "$SCRIPT_DIR/configure-ec2-disk-guardrails.sh" ]]; then
  sudo bash "$SCRIPT_DIR/configure-ec2-disk-guardrails.sh"
fi

if ! command -v aws >/dev/null 2>&1; then
  ARCH="$(uname -m)"
  if [[ "$ARCH" == "aarch64" || "$ARCH" == "arm64" ]]; then
    AWSCLI_URL="https://awscli.amazonaws.com/awscli-exe-linux-aarch64.zip"
  else
    AWSCLI_URL="https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip"
  fi
  TMP_DIR="$(mktemp -d)"
  cd "$TMP_DIR"
  curl "$AWSCLI_URL" -o awscliv2.zip
  unzip -q awscliv2.zip
  sudo ./aws/install
  cd -
  rm -rf "$TMP_DIR"
fi

if ! swapon --show | grep -q "/swapfile"; then
  sudo fallocate -l "$SWAP_SIZE" /swapfile
  sudo chmod 600 /swapfile
  sudo mkswap /swapfile
  sudo swapon /swapfile
  if ! grep -q "^/swapfile " /etc/fstab; then
    echo "/swapfile none swap sw 0 0" | sudo tee -a /etc/fstab >/dev/null
  fi
fi

sudo mkdir -p "$APP_DIR"
sudo chown -R "${SUDO_USER:-ubuntu}:${SUDO_USER:-ubuntu}" "$APP_DIR" || true

echo "EC2 dependencies installed. Re-login before running Docker as a non-root user."
