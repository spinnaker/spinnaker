#!/usr/bin/env bash

METADATA_URL="http://metadata.google.internal/computeMetadata/v1"
INSTANCE_METADATA_URL="$METADATA_URL/instance"

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

TOKEN=$(get_instance_metadata_attribute "vault_token")
if [ -z "$TOKEN" ]; then
  echo "No vault token supplied"
  exit 1
fi

ADDRESS=$(get_instance_metadata_attribute "vault_address")
if [ -z "$ADDRESS" ]; then
  echo "No vault address supplied"
  exit 1
fi

SECRET=$(get_instance_metadata_attribute "vault_secret")
if [ -z "$SECRET" ]; then
  echo "No vault secret supplied"
  exit 1
fi

{%startup-script-path%}mount-config.py --token $TOKEN --address $ADDRESS --secret $SECRET
