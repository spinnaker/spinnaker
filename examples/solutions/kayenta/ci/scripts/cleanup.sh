#!/bin/bash

set -x
set -e

gcloud config set project $GOOGLE_CLOUD_PROJECT
gcloud config set compute/zone us-central1-f
gcloud container clusters get-credentials kayenta-tutorial
kubectl delete -f https://www.spinnaker.io/downloads/kubernetes/quick-install.yml || /bin/true
sleep 60

gcloud --quiet beta container clusters delete kayenta-tutorial
