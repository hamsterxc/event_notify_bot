#!/usr/bin/env bash

source "$(dirname "$0")/common.sh"

function format_date() {
    local date="$1"

    echo -n "$(date --rfc-3339 seconds --date="@$(($date/1000))")"
}

log_group_name="/aws/lambda/${name}"
log_streams_output=$(\
  aws logs describe-log-streams \
  --log-group-name "$log_group_name" \
  --order-by 'LastEventTime' \
  --descending \
  --no-paginate \
)
log_stream_output=$(field "$log_streams_output" '.logStreams[0]') # the most recent log stream
log_stream_name=$(field "$log_stream_output" '.logStreamName')
echo "Getting log entries for $name from $(format_date $(field "$log_stream_output" '.firstEventTimestamp')) to $(format_date $(field "$log_stream_output" '.lastEventTimestamp'))..."

aws logs get-log-events \
  --log-group-name "$log_group_name" \
  --log-stream-name "$log_stream_name" \
  --start-from-head \
  --unmask \
  | jq -c '.events[]' \
  | \
while IFS= read -r log_event; do
  echo "$(format_date $(field "$log_event" '.timestamp')) $(field "$log_event" '.message')"
done
