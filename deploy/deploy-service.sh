#!/usr/bin/env bash
set -euo pipefail

SERVICE="${1:?Missing compose service name}"
IMAGE_URI="${2:?Missing image uri}"
APP_DIR="${APP_DIR:-/opt/ott}"
COMPOSE_FILE="$APP_DIR/docker-compose.prod.yml"
RUNTIME_ENV_FILE="$APP_DIR/.env"
IMAGES_FILE="$APP_DIR/.env.images"
AWS_REGION="${AWS_REGION:-ap-southeast-1}"
LOCK_FILE="/tmp/ott-deploy.lock"
DOCKER_IMAGE_PRUNE_UNTIL="${DOCKER_IMAGE_PRUNE_UNTIL:-72h}"
DOCKER_BUILDER_PRUNE_UNTIL="${DOCKER_BUILDER_PRUNE_UNTIL:-72h}"
DOCKER_LOG_TRUNCATE_OVER="${DOCKER_LOG_TRUNCATE_OVER:-200M}"

case "$SERVICE" in
  api-gateway) IMAGE_KEY="API_GATEWAY_IMAGE" ;;
  auth-service) IMAGE_KEY="AUTH_SERVICE_IMAGE" ;;
  user-service) IMAGE_KEY="USER_SERVICE_IMAGE" ;;
  notification-service) IMAGE_KEY="NOTIFICATION_SERVICE_IMAGE" ;;
  media-service) IMAGE_KEY="MEDIA_SERVICE_IMAGE" ;;
  chat-service) IMAGE_KEY="CHAT_SERVICE_IMAGE" ;;
  analytic-service) IMAGE_KEY="ANALYTIC_SERVICE_IMAGE" ;;
  *)
    echo "Unknown service: $SERVICE" >&2
    exit 1
    ;;
esac

if [[ ! -f "$COMPOSE_FILE" ]]; then
  echo "Missing compose file: $COMPOSE_FILE" >&2
  exit 1
fi

if [[ ! -f "$RUNTIME_ENV_FILE" ]]; then
  echo "Missing runtime env file: $RUNTIME_ENV_FILE" >&2
  exit 1
fi

mkdir -p "$APP_DIR"
touch "$IMAGES_FILE"

(
  flock -x 200
  cd "$APP_DIR"

  PREVIOUS_IMAGE="$(grep -E "^${IMAGE_KEY}=" "$IMAGES_FILE" | cut -d= -f2- || true)"

  if grep -q "^${IMAGE_KEY}=" "$IMAGES_FILE"; then
    sed -i "s|^${IMAGE_KEY}=.*|${IMAGE_KEY}=${IMAGE_URI}|" "$IMAGES_FILE"
  else
    echo "${IMAGE_KEY}=${IMAGE_URI}" >> "$IMAGES_FILE"
  fi

  ECR_REGISTRY="${IMAGE_URI%%/*}"
  aws ecr get-login-password --region "$AWS_REGION" \
    | docker login --username AWS --password-stdin "$ECR_REGISTRY"

  case "$SERVICE" in
    notification-service|user-service|media-service|chat-service)
      echo "Ensuring shared dependencies are running: redis rabbitmq"
      docker compose --env-file "$RUNTIME_ENV_FILE" --env-file "$IMAGES_FILE" -f "$COMPOSE_FILE" up -d redis rabbitmq
      ;;
    auth-service)
      echo "Ensuring shared dependency is running: rabbitmq"
      docker compose --env-file "$RUNTIME_ENV_FILE" --env-file "$IMAGES_FILE" -f "$COMPOSE_FILE" up -d rabbitmq
      ;;
    analytic-service)
      echo "Ensuring shared dependencies are running: analytic-postgres rabbitmq"
      docker compose --env-file "$RUNTIME_ENV_FILE" --env-file "$IMAGES_FILE" -f "$COMPOSE_FILE" up -d analytic-postgres rabbitmq
      ;;
  esac

  echo "Pulling $SERVICE -> $IMAGE_URI"
  docker compose --env-file "$RUNTIME_ENV_FILE" --env-file "$IMAGES_FILE" -f "$COMPOSE_FILE" pull "$SERVICE"

  echo "Restarting $SERVICE without restarting dependencies"
  if ! docker compose --env-file "$RUNTIME_ENV_FILE" --env-file "$IMAGES_FILE" -f "$COMPOSE_FILE" up -d --no-deps "$SERVICE"; then
    echo "Deploy failed for $SERVICE"
    if [[ -n "$PREVIOUS_IMAGE" ]]; then
      echo "Restoring previous image: $PREVIOUS_IMAGE"
      sed -i "s|^${IMAGE_KEY}=.*|${IMAGE_KEY}=${PREVIOUS_IMAGE}|" "$IMAGES_FILE"
      docker compose --env-file "$RUNTIME_ENV_FILE" --env-file "$IMAGES_FILE" -f "$COMPOSE_FILE" up -d --no-deps "$SERVICE" || true
    fi
    exit 1
  fi

  echo "Disk usage before Docker cleanup:"
  df -h / || true
  docker system df || true

  echo "Pruning unused Docker images and build cache older than ${DOCKER_IMAGE_PRUNE_UNTIL}. Volumes are kept."
  docker image prune -af --filter "until=${DOCKER_IMAGE_PRUNE_UNTIL}" || true
  docker builder prune -af --filter "until=${DOCKER_BUILDER_PRUNE_UNTIL}" || true
  docker container prune -f --filter "until=24h" || true
  if [[ -d /var/lib/docker/containers ]]; then
    find /var/lib/docker/containers -type f -name "*-json.log" -size +"${DOCKER_LOG_TRUNCATE_OVER}" -exec truncate -s 0 {} \; || true
  fi

  if command -v journalctl >/dev/null 2>&1; then
    journalctl --vacuum-time=7d || true
  fi

  echo "Disk usage after Docker cleanup:"
  df -h / || true
  docker system df || true

  docker compose --env-file "$RUNTIME_ENV_FILE" --env-file "$IMAGES_FILE" -f "$COMPOSE_FILE" ps "$SERVICE"
) 200>"$LOCK_FILE"
