#!/usr/bin/env bash

bold() {
  echo ". $(tput bold)" "$*" "$(tput sgr0)";
}

sleep 30

gsutil cp gs://gke-spinnaker-codelab/kayenta-workshop/front50.tar .
tar -xvf front50.tar

gsutil cp gs://gke-spinnaker-codelab/kayenta-workshop/services.tar .
tar -xvf services.tar

replace() {
  find front50 -type f -name "*.json" -print0 | xargs -0 sed -i $1
  find services -type f -name "*" -print0 | xargs -0 sed -i $1
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


bold "Publishing sample manifests into $BUCKET_URI/manifests..."

gsutil cp -r services/manifests/frontend.yml $BUCKET_URI/manifests/frontend.yml
gsutil cp -r services/manifests/backend.yml $BUCKET_URI/manifests/backend.yml


bold "Pushing sample images into gcr.io/$PROJECT_ID..."

gcloud docker -- pull gcr.io/spinnaker-marketplace/frontend

gcloud docker -- tag gcr.io/spinnaker-marketplace/frontend \
  gcr.io/$PROJECT_ID/frontend

gcloud docker -- push gcr.io/$PROJECT_ID/frontend

pushd services/backend
./build.sh 
popd

bold "Deploying sample service..."

kubectl apply -f services/manifests/seeding.yml


bold "Starting port forwarding..."

./connect.sh

sleep 10


bold "Configuring sample application & pipelines via spin cli..."

spin application save --file front50/demo-application.json
spin pipeline save --file front50/deploy-to-staging-pipeline.json
spin pipeline save --file front50/deploy-canary-pipeline.json
spin pipeline save --file front50/promotion-pipeline.json
