#!/usr/bin/env bash

bold() {
  echo ". $(tput bold)" "$*" "$(tput sgr0)";
}

./connect.sh

bold "Configuring sample code & pipelines..."

gsutil cp gs://gke-spinnaker-codelab/kayenta-workshop/front50.tar .
tar -xvf front50.tar

replace() {
  find front50 -type f -name "*.json" -print0 | xargs -0 sed -i $1
}

replace 's|{%SPIN_GCS_ACCOUNT%}|'$SPIN_GCS_ACCOUNT'|g'
replace 's|{%SPIN_GCS_PUB_SUB%}|'$SPIN_GCS_PUB_SUB'|g'
replace 's|{%GCS_SUB%}|'$GCS_SUB'|g'
replace 's|{%GCR_SUB%}|'$GCR_SUB'|g'
replace 's|{%SPIN_GCR_PUB_SUB%}|'$SPIN_GCR_PUB_SUB'|g'
replace 's|{%PROJECT_ID%}|'$PROJECT_ID'|g'
replace 's|{%BUCKET_URI%}|'$BUCKET_URI'|g'
replace 's|{%BUCKET_NAME%}|'$BUCKET_NAME'|g'

mkdir ~/.spin
cat << EOF > ~/.spin/config
gate:
  endpoint: http://localhost:8080/gate
EOF

sleep 10

spin application save --file front50/demo-application.json
spin pipeline save --file front50/deploy-to-staging-pipeline.json
spin pipeline save --file front50/deploy-canary-pipeline.json
spin pipeline save --file front50/promotion-pipeline.json
