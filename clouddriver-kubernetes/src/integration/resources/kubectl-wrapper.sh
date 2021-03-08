#!/bin/bash

kubectl_args=""

{
  # Extracts the docker container name from the "--kubeconfig" parameter, and replaces it for the hardcoded path in the k3s container
  while [[ "$#" -gt 0 ]]; do
    case $1 in
      --kubeconfig=*)
      container=$(echo "${1/--kubeconfig=/}" | sed 's|.*/||' | sed 's|kubecfg-||' | sed 's|\.yml||')
      kubectl_args+="--kubeconfig=/etc/rancher/k3s/k3s.yaml " ;;
      -l=*)
      # Remove spaces in label selector
      kubectl_args+="${1// }"
      ;;
      *)
      kubectl_args+="$1 "
      ;;
    esac
    shift
  done
} > /dev/null 2>&1

docker exec -i $container kubectl $kubectl_args
