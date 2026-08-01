#!/usr/bin/env bash

set -eu

aws sts get-caller-identity >/dev/null

function field() {
  local output="$1"
  local field="$2"

  echo "$output" | jq -j "$field"
}

name='event-notify-bot'
dir=$(dirname "$0")
