#!/usr/bin/env bash
set -euo pipefail

DOCKER_LOG_MAX_SIZE="${DOCKER_LOG_MAX_SIZE:-20m}"
DOCKER_LOG_MAX_FILE="${DOCKER_LOG_MAX_FILE:-3}"
JOURNAL_SYSTEM_MAX_USE="${JOURNAL_SYSTEM_MAX_USE:-1G}"
JOURNAL_MAX_RETENTION="${JOURNAL_MAX_RETENTION:-7d}"
DOCKER_IMAGE_PRUNE_UNTIL="${DOCKER_IMAGE_PRUNE_UNTIL:-72h}"
DOCKER_BUILDER_PRUNE_UNTIL="${DOCKER_BUILDER_PRUNE_UNTIL:-72h}"
DAILY_DOCKER_PRUNE_UNTIL="${DAILY_DOCKER_PRUNE_UNTIL:-168h}"

if [[ "$(id -u)" -ne 0 ]]; then
  echo "Run with sudo: sudo bash deploy/aws/configure-ec2-disk-guardrails.sh" >&2
  exit 1
fi

echo "Disk usage before guardrail setup:"
df -h /
docker system df || true
journalctl --disk-usage || true

if command -v jq >/dev/null 2>&1 && command -v docker >/dev/null 2>&1; then
  mkdir -p /etc/docker

  if [[ -f /etc/docker/daemon.json ]]; then
    cp /etc/docker/daemon.json "/etc/docker/daemon.json.bak.$(date +%Y%m%d%H%M%S)"
  else
    printf '{}\n' > /etc/docker/daemon.json
  fi

  tmp_daemon_json="$(mktemp)"
  jq \
    --arg maxSize "$DOCKER_LOG_MAX_SIZE" \
    --arg maxFile "$DOCKER_LOG_MAX_FILE" \
    '. + {
      "log-driver": "json-file",
      "log-opts": ((."log-opts" // {}) + {
        "max-size": $maxSize,
        "max-file": $maxFile
      })
    }' /etc/docker/daemon.json > "$tmp_daemon_json"
  mv "$tmp_daemon_json" /etc/docker/daemon.json
  chmod 0644 /etc/docker/daemon.json

  systemctl restart docker
else
  echo "Skipping Docker daemon log rotation because jq or docker is missing." >&2
fi

mkdir -p /etc/systemd/journald.conf.d
cat > /etc/systemd/journald.conf.d/99-ott-retention.conf <<EOF
[Journal]
SystemMaxUse=${JOURNAL_SYSTEM_MAX_USE}
MaxRetentionSec=${JOURNAL_MAX_RETENTION}
EOF

systemctl restart systemd-journald || true
journalctl --vacuum-time="$JOURNAL_MAX_RETENTION" || true

cat > /usr/local/sbin/ott-docker-cleanup <<EOF
#!/usr/bin/env bash
set -euo pipefail

docker image prune -af --filter "until=${DAILY_DOCKER_PRUNE_UNTIL}" || true
docker builder prune -af --filter "until=${DAILY_DOCKER_PRUNE_UNTIL}" || true
docker container prune -f --filter "until=24h" || true
journalctl --vacuum-time=${JOURNAL_MAX_RETENTION} || true
EOF
chmod 0755 /usr/local/sbin/ott-docker-cleanup

cat > /etc/systemd/system/ott-docker-cleanup.service <<'EOF'
[Unit]
Description=Prune unused OTT Docker images and logs
Wants=docker.service
After=docker.service

[Service]
Type=oneshot
ExecStart=/usr/local/sbin/ott-docker-cleanup
EOF

cat > /etc/systemd/system/ott-docker-cleanup.timer <<'EOF'
[Unit]
Description=Run OTT Docker cleanup every day

[Timer]
OnCalendar=daily
Persistent=true

[Install]
WantedBy=timers.target
EOF

systemctl daemon-reload
systemctl enable --now ott-docker-cleanup.timer

echo "Running one cleanup pass now. Docker volumes are intentionally kept."
docker image prune -af --filter "until=${DOCKER_IMAGE_PRUNE_UNTIL}" || true
docker builder prune -af --filter "until=${DOCKER_BUILDER_PRUNE_UNTIL}" || true
docker container prune -f --filter "until=24h" || true
if [[ -d /var/lib/docker/containers ]]; then
  find /var/lib/docker/containers -type f -name "*-json.log" -exec truncate -s 0 {} \; || true
fi

echo "Disk usage after guardrail setup:"
df -h /
docker system df || true
journalctl --disk-usage || true
