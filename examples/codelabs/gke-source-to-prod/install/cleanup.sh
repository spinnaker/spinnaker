#!/usr/bin/env bash

bold() {
  printf "$(tput bold)" "$*" "$(tput sgr0)"
}

err() {
  printf "%s\n" "$*" >&2;
}

source ./properties

if [ -z "$PROJECT_ID" ]; then
  err "Not running in a GCP project. Exiting."
  exit 1
fi

SA_EMAIL=$(gcloud iam service-accounts list \
  --filter="displayName:$SERVICE_ACCOUNT_NAME" \
  --format='value(email)')

gcloud iam service-accounts delete $SA_EMAIL -q

rm account.txt

gcloud container clusters delete $GKE_CLUSTER --zone $ZONE -q

gsutil rm -r $BUCKET_URI

rm bucket.txt

gcloud pubsub subscriptions delete $GCS_SUB -q
gcloud pubsub topics delete $GCS_TOPIC -q

gcloud pubsub subscriptions delete $GCR_SUB -q
gcloud pubsub topics delete $GCR_TOPIC -q
