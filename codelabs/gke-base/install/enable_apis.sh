#!/usr/bin/env bash

bold() {
  echo ". $(tput bold)" "$*" "$(tput sgr0)";
}

# TODO: Move this list of APIs to properties if we end up needing to enable other APIs.

bold "Enabling Pub/Sub API..."

gcloud services --project $PROJECT_ID enable pubsub.googleapis.com


bold "Enabling Cloud Build API..."

gcloud services --project $PROJECT_ID enable cloudbuild.googleapis.com


bold "Enabling Kubernetes Engine API..."

gcloud services --project $PROJECT_ID enable container.googleapis.com
