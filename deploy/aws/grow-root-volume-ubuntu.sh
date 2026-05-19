#!/usr/bin/env bash
set -euo pipefail

ROOT_DISK="${ROOT_DISK:-/dev/nvme0n1}"
ROOT_PARTITION_NUMBER="${ROOT_PARTITION_NUMBER:-1}"
ROOT_PARTITION="${ROOT_PARTITION:-${ROOT_DISK}p${ROOT_PARTITION_NUMBER}}"
ROOT_MOUNT="${ROOT_MOUNT:-/}"

if [[ "$(id -u)" -ne 0 ]]; then
  echo "Run with sudo: sudo bash deploy/aws/grow-root-volume-ubuntu.sh" >&2
  exit 1
fi

echo "Before resize:"
df -hT "$ROOT_MOUNT"
lsblk

if ! command -v growpart >/dev/null 2>&1; then
  apt-get update
  apt-get install -y cloud-guest-utils
fi

partprobe "$ROOT_DISK" || true
growpart "$ROOT_DISK" "$ROOT_PARTITION_NUMBER"

FSTYPE="$(findmnt -n -o FSTYPE "$ROOT_MOUNT")"
case "$FSTYPE" in
  ext2|ext3|ext4)
    resize2fs "$ROOT_PARTITION"
    ;;
  xfs)
    xfs_growfs -d "$ROOT_MOUNT"
    ;;
  *)
    echo "Unsupported root filesystem type: $FSTYPE" >&2
    exit 1
    ;;
esac

echo "After resize:"
df -hT "$ROOT_MOUNT"
lsblk
