#!/usr/bin/env bash

gcloud builds submit -q --tag gcr.io/spinnaker-playground/demo src/ --project spinnaker-playground
