#!/usr/bin/env bash

source "$(dirname "$0")/common.sh"

table_name="${name}-table"
echo "Creating table $table_name..."
create_table_output=$(\
  aws dynamodb create-table \
  --table-name "$table_name" \
  --attribute-definitions AttributeName=id,AttributeType=S \
  --key-schema AttributeName=id,KeyType=HASH \
  --billing-mode PROVISIONED \
  --provisioned-throughput ReadCapacityUnits=20,WriteCapacityUnits=20 \
  --table-class STANDARD \
)
table_name=$(field "$create_table_output" '.TableDescription.TableName')
aws dynamodb wait table-exists \
  --table-name "$table_name"
echo "Created table $table_name"
