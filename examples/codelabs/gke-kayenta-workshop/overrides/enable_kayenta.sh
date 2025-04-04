#!/usr/bin/env bash

hal config canary enable
hal config canary google enable
hal config canary google account add my-google-account --project $PROJECT_ID --bucket $BUCKET_NAME
hal config canary google edit --gcs-enabled true --stackdriver-enabled true
hal deploy apply
