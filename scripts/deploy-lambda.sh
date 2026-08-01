#!/usr/bin/env bash

source "$(dirname "$0")/common.sh"

env_telegram_bot_token="${1:-}"
if [[ -z "$env_telegram_bot_token" ]]; then
  echo 'No telegram bot token set, using empty' >&2
fi

# Code package

echo "Building lambda code package..."
mvn -B -q clean package >/dev/null
echo "Built lambda code package"

lambda_name="$name"

# Lambda code

echo "Updating $lambda_name lambda code..."
update_code_output=$(\
  aws lambda update-function-code \
  --function-name "$lambda_name" \
  --zip-file 'fileb://./target/event-notify-bot.jar' \
)
lambda_name=$(field "$update_code_output" '.FunctionName')

aws lambda wait function-updated-v2 \
  --function-name "$lambda_name"
echo "$lambda_name lambda code updated ($(field "$update_code_output" '.CodeSize') bytes)"

# Lambda configuration

echo "Updating $lambda_name lambda configuration..."
env_commit_id=$(git rev-parse HEAD)
env_is_clean_build=$(if [[ $(git status --porcelain | wc -l) -gt 0 ]]; then echo 'false'; else echo 'true'; fi)
environment=$(\
  jq -ncj \
  --argjson 'Variables' "$(\
    jq -ncj \
    --arg 'COMMIT_ID' "$env_commit_id" \
    --arg 'IS_CLEAN_BUILD' "$env_is_clean_build" \
    --arg 'TELEGRAM_BOT_TOKEN' "$env_telegram_bot_token" \
    '$ARGS.named' \
  )" \
  '$ARGS.named'
)

update_configuration_output=$(\
  aws lambda update-function-configuration \
  --function-name "$lambda_name" \
  --handler 'com.lonebytesoft.hamster.eventnotifybot.handler.AwsLambdaRequestHandler' \
  --timeout '60' \
  --environment "$environment" \
)
lambda_name=$(field "$update_configuration_output" '.FunctionName')

aws lambda wait function-updated-v2 \
  --function-name "$lambda_name"
echo "$lambda_name lambda configuration updated"
