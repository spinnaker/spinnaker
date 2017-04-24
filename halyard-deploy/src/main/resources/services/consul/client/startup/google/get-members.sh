#!/usr/bin/env bash

METADATA_URL="http://metadata.google.internal/computeMetadata/v1"
INSTANCE_METADATA_URL="$METADATA_URL/instance"
INSTANCE_LIST_METADATA="consul-members"

function get_instance_metadata_attribute() {
  local name="$1"
  local value=$(curl -s -f -H "Metadata-Flavor: Google" \
                     $INSTANCE_METADATA_URL/attributes/$name)
  if [[ $? -eq 0 ]]; then
    echo "$value"
  else
    echo ""
  fi
}

function get_instance_list_from_metadata() {
  get_instance_metadata_attribute $INSTANCE_LIST_METADATA
}

instances=$(get_instance_list_from_metadata)

if [ -z "$instances" ]; then
  >&2 echo "Consul instances not found in instance metadata, unable to join cluster."
  exit 1
fi

echo $instances