#!/usr/bin/env bash

source "$(dirname "$0")/common.sh"

# Lambda execution role

role_name="${name}-role"
create_role_output=$(\
  aws iam create-role \
  --role-name "$role_name" \
  --assume-role-policy-document "file://$dir/assume-role-lambda-policy.json" \
)
role_arn=$(field "$create_role_output" '.Role.Arn')
role_name=$(field "$create_role_output" '.Role.RoleName')
aws iam wait role-exists \
  --role-name "$role_name"
echo "Created role $role_name"

lambda_policy_arn='arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole'
lambda_policy_name='AWSLambdaBasicExecutionRole'
aws iam attach-role-policy \
  --role-name "$role_name" \
  --policy-arn "$lambda_policy_arn"
echo "Attached policy $lambda_policy_name to role $role_name"

dynamodb_policy_name="${name}-dynamodb-policy"
create_dynamodb_policy_output=$(\
  aws iam create-policy \
  --policy-name "$dynamodb_policy_name" \
  --policy-document "file://$dir/dynamodb-readwrite-policy.json" \
)
dynamodb_policy_arn=$(field "$create_dynamodb_policy_output" '.Policy.Arn')
dynamodb_policy_name=$(field "$create_dynamodb_policy_output" '.Policy.PolicyName')
aws iam wait policy-exists \
  --policy-arn "$dynamodb_policy_arn"
echo "Created policy $dynamodb_policy_name"

aws iam attach-role-policy \
  --role-name "$role_name" \
  --policy-arn "$dynamodb_policy_arn"
echo "Attached policy $dynamodb_policy_name to role $role_name"

# Lambda

mvn -f "$dir/noop-lambda/" -B -q clean package >/dev/null
echo "Built a minimal lambda package"

lambda_name="$name"
echo "Creating lambda $lambda_name..."
create_lambda_output=$(\
  aws lambda create-function \
  --function-name "$lambda_name" \
  --runtime 'java25' \
  --role "$role_arn" \
  --handler 'com.lonebytesoft.hamster.eventnotifybot.noop.LambdaHandler' \
  --timeout '1' \
  --memory-size '128' \
  --package-type 'Zip' \
  --ephemeral-storage 'Size=512' \
  --zip-file "fileb://$dir/noop-lambda/target/event-notify-bot-noop.jar" \
)
lambda_name=$(field "$create_lambda_output" '.FunctionName')
aws lambda wait function-active \
  --function-name "$lambda_name"
echo "Created lambda $lambda_name"
