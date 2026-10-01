#!/usr/bin/env bash

source "$(dirname "$0")/common.sh"

lambda_name="$name"

echo "Invoking ${lambda_name}..."
invoke_output=$(\
  aws lambda invoke \
  --function-name "$lambda_name" \
  --invocation-type 'RequestResponse' \
  --log-type 'Tail' \
  '/dev/null' \
)
echo "Finished ${lambda_name} invocation, status code $(field "$invoke_output" '.StatusCode')"

echo
field "$invoke_output" '.LogResult' | base64 -d
