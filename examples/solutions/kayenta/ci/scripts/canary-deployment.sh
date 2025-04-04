#!/bin/bash

set -x
set -e

## Setup ##
gcloud config set project $GOOGLE_CLOUD_PROJECT
gcloud config set compute/zone us-central1-f
gcloud container clusters get-credentials kayenta-tutorial

curl --fail -LO https://github.com/stedolan/jq/releases/download/jq-1.5/jq-linux64
chmod +x jq-linux64
mv jq-linux64 /usr/local/bin/jq
apt-get -y install bc

DECK_POD=$(kubectl -n spinnaker get pods -l \
    cluster=spin-deck,app=spin \
    -o=jsonpath='{.items[0].metadata.name}')
kubectl -n spinnaker port-forward $DECK_POD 8080:9000 \
    >/dev/null &
sleep 5

## Creating Canary deploy pipeline ##
export PIPELINE_ID=$(curl --fail localhost:8080/gate/applications/sampleapp/pipelineConfigs/Simple%20deploy \
    | jq -r '.id')
jq '(.stages[] | select(.refId == "9") | .pipeline) |= env.PIPELINE_ID | (.stages[] | select(.refId == "8") | .pipeline) |= env.PIPELINE_ID' \
    spinnaker-git/solutions/kayenta/pipelines/canary-deploy.json | \
    curl --fail -d@- -X POST \
    -H "Content-Type: application/json" -H "Accept: */*" \
    http://localhost:8080/gate/pipelines
sleep 5

## Running canary deploy pipeline with success rate of 80 ##
curl --fail -d '{"type":"manual","dryRun":false,"parameters":{"successRate":"80"},"user":"[anonymous]"}' \
    -X POST -H "Content-Type: application/json" -H "Accept: */*" \
    http://localhost:8080/gate/pipelines/sampleapp/Canary%20Deploy

## Waiting for canary and baseline to be up ##
set +x
timeout=0
deployed=0
while [ "$timeout" -lt 300 -a "$deployed" -ne 1 ]; do
  canary=$(kubectl -n default get pods | grep "canary" | grep "1/1" | wc -l)
  baseline=$(kubectl -n default get pods | grep "baseline" | grep "1/1" | wc -l)
  if [ "$canary" -eq 1 -a "$baseline" -eq 1 ]; then
    deployed=1
  else
    echo "[${timeout}s] Canary and baseline not yet deployed, waiting 5s more."
    sleep 5
    timeout=$((timeout+5))
  fi
done
if [ "$timeout" -ge 300 ]; then
  echo "Timeout deploying Sampleapp baseline & canary"
  exit 1
fi
echo "Sampleapp baseline and canary are ready!"
set -x

## Comparing canary and baseline error rates ##
sleep 360
START_TIME=$(date -u +'%Y-%m-%dT%H:%M:%S+00:00' -d '-6min')
END_TIME=$(date -u +'%Y-%m-%dT%H:%M:%S+00:00' -d '-1min')
set +x
echo "Getting auth token"
TOKEN=$(gcloud auth application-default print-access-token)
echo "Getting metrics from Stackdriver"
curl -H "Authorization: Bearer ${TOKEN}" -G \
    --data-urlencode interval.startTime=$START_TIME \
    --data-urlencode interval.endTime=$END_TIME \
    --data aggregation.alignmentPeriod=60s \
    --data aggregation.crossSeriesReducer=REDUCE_SUM \
    --data aggregation.perSeriesAligner=ALIGN_RATE \
    --data "filter=metric.type+%3D+%22external.googleapis.com%2Fprometheus%2Frequests%22%20AND+metric.label.http_code+%3D+500+resource.label.pod_name+%3D+starts_with(%22sampleapp-canary-%22)" \
    "https://content-monitoring.googleapis.com/v3/projects/kayenta-solution-ci/timeSeries" > canary.json
echo "Canary results"
cat canary.json
curl -H "Authorization: Bearer ${TOKEN}" -G \
    --data-urlencode interval.startTime=$START_TIME \
    --data-urlencode interval.endTime=$END_TIME \
    --data aggregation.alignmentPeriod=60s \
    --data aggregation.crossSeriesReducer=REDUCE_SUM \
    --data aggregation.perSeriesAligner=ALIGN_RATE \
    --data "filter=metric.type+%3D+%22external.googleapis.com%2Fprometheus%2Frequests%22%20AND+metric.label.http_code+%3D+500+resource.label.pod_name+%3D+starts_with(%22sampleapp-baseline-%22)" \
    "https://content-monitoring.googleapis.com/v3/projects/kayenta-solution-ci/timeSeries" > baseline.json
echo "Baseline results"
cat baseline.json
set -x
ERRORS_CANARY=$(cat canary.json | jq '.timeSeries[] | [.points[].value.doubleValue] | length as $array_length | add / $array_length')
ERRORS_BASELINE=$(cat baseline.json | jq '.timeSeries[] | [.points[].value.doubleValue] | length as $array_length | add / $array_length')

if [ $(echo "$ERRORS_CANARY < $ERRORS_BASELINE" | bc) -eq 1 ]; then
  echo "Canary has a lower error rate ($ERRORS_CANARY errors/s) than baseline ($ERRORS_BASELINE errors/s), as expected."
else
  echo "Canary does NOT have a lower error rate ($ERRORS_CANARY errors/s) than baseline ($ERRORS_BASELINE errors/s), as expected."
  exit 1
fi

## "Clicking" on continue for the manual judgment stage ##
curl --fail "localhost:8080/gate/applications/sampleapp/pipelines?statuses=RUNNING" > pipeline.json
# Test that we have only one running pipeline
[ $(jq '. | length' pipeline.json) -eq 1 ]
export EXECUTION_ID=$(jq -r '.[0].id' pipeline.json)
export MANUAL_JUDGMENT_STAGE_ID=$(jq -r '.[0].stages[] | select(.name == "Manual Judgment") | .id' pipeline.json)

curl --fail -X PATCH -d '{"judgmentStatus":"continue"}' \
  -H "Content-Type: application/json" -H "Accept: */*" \
  "localhost:8080/gate/pipelines/${EXECUTION_ID}/stages/${MANUAL_JUDGMENT_STAGE_ID}" > /dev/null

## Waiting for baseline and canary to be destroyed ##
set +x
timeout=0
deployed=1
while [ "$timeout" -lt 300 -a "$deployed" -ne 0 ]; do
  canary=$(kubectl -n default get pods | grep "canary" | wc -l)
  baseline=$(kubectl -n default get pods | grep "baseline" | wc -l)
  if [ "$canary" -eq 0 -a "$baseline" -eq 0 ]; then
    deployed=0
  else
    echo "[${timeout}s] Canary and baseline still deployed, waiting 5s more."
    sleep 5
    timeout=$((timeout+5))
  fi
done
if [ "$timeout" -ge 300 ]; then
  echo "Timeout destroying Sampleapp baseline & canary"
  exit 1
fi
echo "Sampleapp baseline and canary are destroyed!"

## Waiting for pipeline to succeed ##
timeout=0
status="NOSUCCESS"
while [ "$timeout" -lt 300 -a "$status" != "SUCCEEDED" ]; do
  status=$(curl --fail -sS "localhost:8080/gate/applications/sampleapp/pipelines" | \
    jq -r '.[] | select(.name == "Canary Deploy") | select(.id == env.EXECUTION_ID) | .status')
  echo "[${timeout}s] Waiting for pipeline to succeed, waiting 5s more."
  sleep 5
  timeout=$((timeout+5))
done
if [ "$status" != "SUCCEEDED" ]; then
  echo "Timeout while waiting for pipeline to complete."
  exit 1
else
  echo "Pipeline completed"
fi
set -x

## Checking that prod is now running with a 80% success rate ##
CONFIG_MAP_PROD=$(kubectl -n default get deployment sampleapp -o json | \
  jq -r '.spec.template.spec.containers[0].env[0].valueFrom.configMapKeyRef.name')
SUCCESS_RATE=$(kubectl -n default get cm $CONFIG_MAP_PROD -o json | \
  jq -r '.data.SUCCESS_RATE')
if [ "$SUCCESS_RATE" -eq 80 ]; then
  echo "Success rate for production sampleapp is 80, as expected."
else
  echo "Success rate for production sampleapp is not 80, as expected: $SUCCESS_RATE"
  exit 1
fi
