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

## Configuring Kayenta ##
export HALYARD_POD=$(kubectl -n spinnaker get pods -l \
    stack=halyard,app=spin \
    -o=jsonpath='{.items[0].metadata.name}')
kubectl -n spinnaker exec $HALYARD_POD -- sh -c "hal config canary google enable"
kubectl -n spinnaker exec $HALYARD_POD -- sh -c "hal config canary google account add kayenta-tutorial --project $GOOGLE_CLOUD_PROJECT"
kubectl -n spinnaker exec $HALYARD_POD -- sh -c "hal config canary google edit --stackdriver-enabled=true"
kubectl -n spinnaker exec $HALYARD_POD -- sh -c "hal deploy apply"

set +x
timeout=0
while [ "$timeout" -lt 600 -a $(kubectl -n spinnaker get pods | grep "kayenta" | wc -l) -ne 1 ]; do
  echo "[${timeout}s] Spinnaker not yet ready, waiting 15s more."
  sleep 15
  timeout=$((timeout+15))
done
if [ "$timeout" -ge 600 ]; then
  echo "Timeout configuring Kayenta"
  exit 1
fi
echo "Spinnaker ready!"
set -x

## Enabling canary on sampleapp ##
DECK_POD=$(kubectl -n spinnaker get pods -l \
    cluster=spin-deck,app=spin \
    -o=jsonpath='{.items[0].metadata.name}')
kubectl -n spinnaker port-forward $DECK_POD 8080:9000 \
    >/dev/null &
sleep 5

curl -XPOST -d '{"job":[{"type":"updateApplication","application":{"name":"sampleapp","dataSources":{"enabled":["canaryConfigs"],"disabled":[]}},"user":"[anonymous]"}],"application":"sampleapp","description":"Update Application: sampleapp"}' \
    --fail -sS -H "Content-Type: application/json" -H "Accept: */*" \
    http://localhost:8080/gate/applications/sampleapp/tasks >/dev/null
sleep 5

## Creating Canary config for sampleapp ##
curl -XPOST -d '{"name":"kayenta-test","applications":["sampleapp"],"description":"","metrics":[{"analysisConfigurations":{"canary":{"direction":"increase"}},"name":"error_rate","query":{"type":"stackdriver","serviceType":"stackdriver","resourceType":"k8s_container","perSeriesAligner":"ALIGN_RATE","customFilterTemplate":"http_code","metricType":"external.googleapis.com/prometheus/requests"},"groups":["Group 1"],"scopeName":"default"}],"configVersion":"1","templates":{"http_code":"metric.labels.http_code = \"500\" AND resource.label.pod_name = starts_with(\"${scope}\")"},"classifier":{"groupWeights":{"Group 1":100},"scoreThresholds":{"pass":95,"marginal":75}},"judge":{"name":"NetflixACAJudge-v1.0","judgeConfigurations":{}}}' \
    --fail -sS -H "Content-Type: application/json" -H "Accept: */*" \
    http://localhost:8080/gate/v2/canaryConfig > /dev/null
sleep 5

## Create automated canary pipeline ##
export PIPELINE_ID=$(curl localhost:8080/gate/applications/sampleapp/pipelineConfigs/Simple%20deploy \
    | jq -r '.id')
export CANARY_CONFIG_ID=$(curl localhost:8080/gate/v2/canaryConfig | jq -r '.[0].id')
jq '(.stages[] | select(.refId == "9") | .pipeline) |= env.PIPELINE_ID | (.stages[] | select(.refId == "8") | .pipeline) |= env.PIPELINE_ID | (.stages[] | select(.refId == "16") | .canaryConfig.canaryConfigId) |= env.CANARY_CONFIG_ID' spinnaker-git/solutions/kayenta/pipelines/automated-canary-1-10.json | \
    curl -d@- -X POST \
    -H "Content-Type: application/json" -H "Accept: */*" \
    http://localhost:8080/gate/pipelines
sleep 5

## Running canary deploy pipeline with success rate of 60 ##
EXECUTION_ID=$(curl --fail -d '{"type":"manual","dryRun":false,"parameters":{"successRate":"60"},"user":"[anonymous]"}' \
    -X POST -H "Content-Type: application/json" -H "Accept: */*" \
    http://localhost:8080/gate/pipelines/sampleapp/Automated%20Canary%20Deploy \
    | jq -r '.ref')

## Waiting for pipeline to finish ##
set +x
timeout=0
status="NOTFINISHED"
while [ "$timeout" -lt 1200 -a "$status" != "TERMINAL" ]; do
  echo "[${timeout}s] Waiting for pipeline to fail, waiting 30s more."
  sleep 30
  timeout=$((timeout+30))
  status=$(curl --fail -sS "localhost:8080/gate${EXECUTION_ID}/" | \
    jq -r '.status')
done
if [ "$status" != "TERMINAL" ]; then
  echo "Timeout while waiting for pipeline to fail. Current status: $status"
  exit 1
else
  echo "Pipeline failed, as expected"
fi
set -x

## Checking if metrics are indeed as expected ##
CANARY_CONFIG_ID=$(curl --fail -sS localhost:8080/gate${EXECUTION_ID}/ \
  | jq -r '.stages[] | select(.name == "Run Canary #1") | .context.canaryConfigId')
CANARY_PIPELINE_EXECUTION_ID=$(curl --fail -sS localhost:8080/gate${EXECUTION_ID}/ \
  | jq -r '.stages[] | select(.name == "Run Canary #1") | .context.canaryPipelineExecutionId')
METRIC_SET_PAIR_LIST_ID=$(curl --fail -sS "localhost:8080/gate/v2/canaries/canary/${CANARY_CONFIG_ID}/${CANARY_PIPELINE_EXECUTION_ID}?storageAccountName=kayenta-minio" \
  | jq -r '.metricSetPairListId')
curl --fail -sS "localhost:8080/gate/v2/canaries/metricSetPairList/${METRIC_SET_PAIR_LIST_ID}?storageAccountName=kayenta-minio" > metrics.json

CONTROL_AVG=$(jq -r '.[0].values.control | length as $array_length | add / $array_length' metrics.json)
EXPERIMENT_AVG=$(jq -r '.[0].values.experiment | length as $array_length | add / $array_length' metrics.json)

if [ $(echo "$CONTROL_AVG < $EXPERIMENT_AVG" | bc) -eq 1 ]; then
  echo "The pipeline failed because the canary had a higher error rate ($EXPERIMENT_AVG errors/s) than the baseline ($CONTROL_AVG errors/s), as expected."
else
  echo "There is a problem with the data in Kayenta:"
  echo " - Canary error rate: $EXPERIMENT_AVG errors/s"
  echo " - Baseline error rate: $CONTROL_AVG errors/s"
  exit 1
fi

## Running canary deploy pipeline with success rate of 90 ##
EXECUTION_ID=$(curl --fail -d '{"type":"manual","dryRun":false,"parameters":{"successRate":"90"},"user":"[anonymous]"}' \
    -X POST -H "Content-Type: application/json" -H "Accept: */*" \
    http://localhost:8080/gate/pipelines/sampleapp/Automated%20Canary%20Deploy \
    | jq -r '.ref')

## Waiting for pipeline to finish ##
set +x
timeout=0
status="NOTFINISHED"
while [ "$timeout" -lt 1200 -a "$status" != "SUCCEEDED" ]; do
  echo "[${timeout}s] Waiting for pipeline to succeed, waiting 30s more."
  sleep 30
  timeout=$((timeout+30))
  status=$(curl --fail -sS "localhost:8080/gate${EXECUTION_ID}/" | \
    jq -r '.status')
done
if [ "$status" != "SUCCEEDED" ]; then
  echo "Timeout while waiting for pipeline to succeed. Current status: $status"
  exit 1
else
  echo "Pipeline succeeded, as expected"
fi
set -x


## Checking if metrics are indeed as expected ##
CANARY_CONFIG_ID=$(curl --fail -sS localhost:8080/gate${EXECUTION_ID}/ \
  | jq -r '.stages[] | select(.name == "Run Canary #1") | .context.canaryConfigId')
CANARY_PIPELINE_EXECUTION_ID=$(curl --fail -sS localhost:8080/gate${EXECUTION_ID}/ \
  | jq -r '.stages[] | select(.name == "Run Canary #1") | .context.canaryPipelineExecutionId')
METRIC_SET_PAIR_LIST_ID=$(curl --fail -sS "localhost:8080/gate/v2/canaries/canary/${CANARY_CONFIG_ID}/${CANARY_PIPELINE_EXECUTION_ID}?storageAccountName=kayenta-minio" \
  | jq -r '.metricSetPairListId')
curl --fail -sS "localhost:8080/gate/v2/canaries/metricSetPairList/${METRIC_SET_PAIR_LIST_ID}?storageAccountName=kayenta-minio" > metrics.json

CONTROL_AVG=$(jq -r '.[0].values.control | length as $array_length | add / $array_length' metrics.json)
EXPERIMENT_AVG=$(jq -r '.[0].values.experiment | length as $array_length | add / $array_length' metrics.json)

if [ $(echo "$EXPERIMENT_AVG < $CONTROL_AVG" | bc) -eq 1 ]; then
  echo "The pipeline succeeded because the canary had a lower error rate ($EXPERIMENT_AVG errors/s) than the baseline ($CONTROL_AVG errors/s), as expected."
else
  echo "There is a problem with the data in Kayenta:"
  echo " - Canary error rate: $EXPERIMENT_AVG errors/s"
  echo " - Baseline error rate: $CONTROL_AVG errors/s"
  exit 1
fi
