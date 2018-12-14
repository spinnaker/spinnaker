#!/bin/bash

set -x
set -e

gcloud config set project $GOOGLE_CLOUD_PROJECT
gcloud config set compute/zone us-central1-f

## Create GKE cluster ##
gcloud beta container clusters create kayenta-tutorial \
    --machine-type=n1-standard-2 --cluster-version=1.10 \
    --enable-stackdriver-kubernetes \
    --scopes=gke-default,compute-ro
gcloud container clusters get-credentials kayenta-tutorial

## Install Stackdriver Prometheus plugin ##
kubectl apply --as=admin --as-group=system:masters -f \
    https://storage.googleapis.com/stackdriver-prometheus-documentation/rbac-setup.yml
curl -sS "https://storage.googleapis.com/stackdriver-prometheus-documentation/prometheus-service.yml" | \
    sed "s/_stackdriver_project_id:.*/_stackdriver_project_id: $GOOGLE_CLOUD_PROJECT/" | \
    sed "s/_kubernetes_cluster_name:.*/_kubernetes_cluster_name: kayenta-tutorial/" | \
    sed "s/_kubernetes_location:.*/_kubernetes_location: us-central1-f/" | \
    kubectl apply -f -

## Install Spinnaker ##
curl -sSL "https://spinnaker.io/downloads/kubernetes/quick-install.yml" | \
    sed 's/version:.*/version: 1.10.5/g' | kubectl apply -f -
# A successful Spinnaker install has 11 pods
# Timeout of 20minutes (1200s)
set +x
timeout=0
while [ "$timeout" -lt 1200 -a $(kubectl -n spinnaker get pods | grep "1/1" | wc -l) -ne 11 ]; do
  n=$(kubectl -n spinnaker get pods | grep "1/1" | wc -l)
  echo "[${timeout}s|${n}/11] Spinnaker not yet ready, waiting 30s more."
  sleep 30
  timeout=$((timeout+30))
done
if [ "$timeout" -ge 1200 ]; then
  echo "Timeout installing Spinnaker"
  exit 1
fi
echo "Spinnaker ready!"
set -x
