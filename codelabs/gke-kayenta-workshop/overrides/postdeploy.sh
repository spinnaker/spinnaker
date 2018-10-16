#!/usr/bin/env bash

bold() {
  echo ". $(tput bold)" "$*" "$(tput sgr0)";
}

bold "Configuring codelab-specific settings..."

bold "Enabling kayenta..."

HALYARD_POD=$(kubectl get po -n spinnaker -l "stack=halyard" \
  -o jsonpath="{.items[0].metadata.name}")

kubectl exec $HALYARD_POD -n spinnaker -- bash -c "$(cat overrides/enable_kayenta.sh | envsubst)"

overrides/publish_samples.sh
