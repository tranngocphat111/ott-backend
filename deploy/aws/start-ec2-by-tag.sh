#!/usr/bin/env bash
set -euo pipefail

AWS_REGION="${AWS_REGION:-ap-southeast-1}"
EC2_TAG_NAME="${EC2_TAG_NAME:-ott-backend-prod}"

INSTANCE_ID="$(aws ec2 describe-instances \
  --region "$AWS_REGION" \
  --filters "Name=tag:Name,Values=$EC2_TAG_NAME" "Name=instance-state-name,Values=stopped,stopping" \
  --query "Reservations[0].Instances[0].InstanceId" \
  --output text)"

if [[ -z "$INSTANCE_ID" || "$INSTANCE_ID" == "None" ]]; then
  echo "No stopped instance found with tag Name=$EC2_TAG_NAME"
  exit 1
fi

aws ec2 start-instances --region "$AWS_REGION" --instance-ids "$INSTANCE_ID" >/dev/null
aws ec2 wait instance-running --region "$AWS_REGION" --instance-ids "$INSTANCE_ID"

aws ec2 describe-instances \
  --region "$AWS_REGION" \
  --instance-ids "$INSTANCE_ID" \
  --query "Reservations[0].Instances[0].{InstanceId:InstanceId,PublicIp:PublicIpAddress,State:State.Name}" \
  --output table
