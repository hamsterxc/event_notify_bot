#!/usr/bin/env bash

source "$(dirname "$0")/common.sh"

function delete_role() {
  local role_name="$1"

  local list_roles_output=$(\
    aws iam list-roles \
    --no-paginate \
    --query "Roles[?RoleName=='$role_name']"
  )
  if [[ "$(field "$list_roles_output" '. | length')" -gt '0' ]]; then
    role_name=$(field "$list_roles_output" '.[0].RoleName')

    for attached_policy in $(aws iam list-attached-role-policies --role-name "$role_name" | jq -c '.AttachedPolicies[]'); do
      local attached_policy_arn="$(field "$attached_policy" '.PolicyArn')"
      local attached_policy_name="$(field "$attached_policy" '.PolicyName')"
      aws iam detach-role-policy \
        --role-name "$role_name" \
        --policy-arn "$attached_policy_arn"
      echo "Detached policy $attached_policy_name from role $role_name"
    done

    aws iam delete-role \
      --role-name "$role_name"
    echo "Deleted role $role_name"
  else
    echo "No role $role_name exists"
  fi
}

function delete_policy() {
  local policy_name="$1"

  local list_policies_output=$(\
    aws iam list-policies \
    --scope Local \
    --no-paginate \
    --query "Policies[?PolicyName=='${policy_name}']" \
  )
  if [[ "$(field "$list_policies_output" '. | length')" -gt '0' ]]; then
    local policy_arn="$(field "$list_policies_output" '.[0].Arn')"
    policy_name="$(field "$list_policies_output" '.[0].PolicyName')"
    aws iam delete-policy \
      --policy-arn "$policy_arn"
    echo "Deleted policy $policy_name"
  else
    echo "No policy $policy_name exists"
  fi
}

function delete_lambda() {
  local lambda_name="$1"

  local list_functions_output=$(\
    aws lambda list-functions \
    --no-paginate \
    --query "Functions[?FunctionName=='${lambda_name}']" \
  )
  if [[ "$(field "$list_functions_output" '. | length')" -gt '0' ]]; then
    lambda_name="$(field "$list_functions_output" '.[0].FunctionName')"
    local delete_function_output=$(\
      aws lambda delete-function \
      --function-name "$lambda_name" \
    )
    local delete_function_status=$(field "$delete_function_output" '.StatusCode')
    echo "Deleted lambda $lambda_name (status code: $delete_function_status)"
  else
    echo "No lambda $lambda_name exists"
  fi
}

function delete_schedule() {
  local schedule_name="$1"

  local list_schedules_output=$(\
    aws scheduler list-schedules \
    --no-paginate \
    --query "Schedules[?Name=='${schedule_name}']" \
  )
  if [[ "$(field "$list_schedules_output" '. | length')" -gt '0' ]]; then
    schedule_name="$(field "$list_schedules_output" '.[0].Name')"
    aws scheduler delete-schedule \
      --name "$schedule_name"
    echo "Deleted schedule $schedule_name"
  else
    echo "No schedule $schedule_name exists"
  fi
}

function delete_table() {
  local table_name="$1"

  local list_tables_output=$(\
    aws dynamodb list-tables \
    --no-paginate \
    --query "TableNames[?@=='$table_name']" \
  )
  if [[ "$(field "$list_tables_output" '. | length')" -gt '0' ]]; then
    table_name="$(field "$list_tables_output" '.[0]')"
    local delete_table_output=$(\
      aws dynamodb delete-table \
      --table-name "$table_name" \
    )
    table_name=$(field "$delete_table_output" '.TableDescription.TableName')

    aws dynamodb wait table-not-exists \
      --table-name "$table_name"
    echo "Deleted table $table_name"
  else
    echo "No table $table_name exists"
  fi
}

# Lambda schedule

delete_schedule "${name}-schedule"
delete_role "${name}-invoke-role"
delete_policy "${name}-invoke-policy"

# Lambda

delete_lambda "${name}"

# Lambda execution role

delete_role "${name}-role"
delete_policy "${name}-dynamodb-policy"

# DynamoDB table

delete_table "${name}-table"
