#!/usr/bin/env bash

source "$(dirname "$0")/common.sh"

lambda_name="$name"
list_functions_output=$(\
  aws lambda list-functions \
  --no-paginate \
  --query "Functions[?FunctionName=='${lambda_name}']" \
)
if [[ "$(field "$list_functions_output" '. | length')" -gt '0' ]]; then
  lambda_name=$(field "$list_functions_output" '.[0].FunctionName')
  lambda_arn=$(field "$list_functions_output" '.[0].FunctionArn')
else
  echo "No lambda $lambda_name exists" >&2
  exit 1
fi

# Schedule execution role

invoke_role_name="${name}-invoke-role"
create_invoke_role_output=$(\
  aws iam create-role \
  --role-name "$invoke_role_name" \
  --assume-role-policy-document "file://$dir/assume-role-scheduler-policy.json" \
)
invoke_role_arn=$(field "$create_invoke_role_output" '.Role.Arn')
invoke_role_name=$(field "$create_invoke_role_output" '.Role.RoleName')
aws iam wait role-exists \
  --role-name "$invoke_role_name"
echo "Created role $invoke_role_name"

invoke_policy_name="${name}-invoke-policy"
create_invoke_policy_output=$(\
  aws iam create-policy \
  --policy-name "$invoke_policy_name" \
  --policy-document "file://$dir/lambda-invoke-policy.json" \
)
invoke_policy_arn=$(field "$create_invoke_policy_output" '.Policy.Arn')
invoke_policy_name=$(field "$create_invoke_policy_output" '.Policy.PolicyName')
aws iam wait policy-exists \
  --policy-arn "$invoke_policy_arn"
echo "Created policy $invoke_policy_name"

aws iam attach-role-policy \
  --role-name "$invoke_role_name" \
  --policy-arn "$invoke_policy_arn"
echo "Attached policy $invoke_policy_name to role $invoke_role_name"

# Schedule

schedule_name="${name}-schedule"
schedule_expression='rate(1 minute)'
schedule_target=$(\
  jq -ncj \
  --arg 'RoleArn' "$invoke_role_arn" \
  --arg 'Arn' "$lambda_arn" \
  --arg 'Input' '{}' \
  '$ARGS.named'
)
create_schedule_output=$(\
  aws scheduler create-schedule \
  --name "$schedule_name" \
  --schedule-expression "$schedule_expression" \
  --flexible-time-window 'Mode=OFF' \
  --target "$schedule_target" \
)
echo "Created schedule $schedule_name with $schedule_expression for $lambda_name"
