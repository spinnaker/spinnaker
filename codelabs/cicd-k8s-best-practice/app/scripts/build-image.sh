#!/usr/bin/env bash

gcloud container builds submit -q --tag gcr.io/spinnaker-playground/demo src/ --project spinnaker-playground
