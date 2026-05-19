#!/usr/bin/env bash
set -euo pipefail

AWS_REGION="${AWS_REGION:-ap-southeast-1}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
POLICY_FILE="$SCRIPT_DIR/../ecr-lifecycle-policy.json"

repos=(
  ott-api-gateway
  ott-auth-service
  ott-user-service
  ott-notification-service
  ott-media-service
  ott-chat-service
  ott-analytic-service
  ott-moderation-service
)

for repo in "${repos[@]}"; do
  if aws ecr describe-repositories --region "$AWS_REGION" --repository-names "$repo" >/dev/null 2>&1; then
    echo "ECR repository exists: $repo"
  else
    echo "Creating ECR repository: $repo"
    aws ecr create-repository \
      --region "$AWS_REGION" \
      --repository-name "$repo" \
      --image-scanning-configuration scanOnPush=true \
      --encryption-configuration encryptionType=AES256 >/dev/null
  fi

  aws ecr put-lifecycle-policy \
    --region "$AWS_REGION" \
    --repository-name "$repo" \
    --lifecycle-policy-text "file://$POLICY_FILE" >/dev/null
done

echo "Done. ECR repositories are ready in $AWS_REGION."
