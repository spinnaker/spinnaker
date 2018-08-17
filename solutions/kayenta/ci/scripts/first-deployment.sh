#!/bin/bash

set -x
set -e

gcloud config set project $GOOGLE_CLOUD_PROJECT
gcloud config set compute/zone us-central1-f
gcloud container clusters get-credentials kayenta-tutorial

curl --fail -LO https://storage.googleapis.com/spinnaker-artifacts/spin/$(curl -s https://storage.googleapis.com/spinnaker-artifacts/spin/latest)/linux/amd64/spin
chmod +x spin
mv spin /usr/local/bin/spin
apt-get -y install jq bc

DECK_POD=$(kubectl -n spinnaker get pods -l \
    cluster=spin-deck,app=spin \
    -o=jsonpath='{.items[0].metadata.name}')
kubectl -n spinnaker port-forward $DECK_POD 8080:9000 \
    >/dev/null &
sleep 5

spin application save --application-name sampleapp --owner-email example@example.com --cloud-providers "kubernetes" --gate-endpoint http://localhost:8080/gate/

sleep 5

curl --fail -d@spinnaker-git/solutions/kayenta/pipelines/simple-deploy.json \
    -X POST -H "Content-Type: application/json" -H "Accept: */*" \
    http://localhost:8080/gate/pipelines

sleep 5

curl --fail -d '{"type":"manual","dryRun":false,"parameters":{"successRate":"70"},"user":"[anonymous]"}' \
    -X POST -H "Content-Type: application/json" -H "Accept: */*" \
    http://localhost:8080/gate/pipelines/sampleapp/Simple%20deploy

sleep 5

set +x
timeout=0
while [ "$timeout" -lt 300 -a $(kubectl -n default get pods | grep "1/1" | wc -l) -ne 4 ]; do
  n=$(kubectl -n default get pods | grep "1/1" | wc -l)
  echo "[${timeout}s|${n}/4] Sampleapp not yet ready, waiting 5s more."
  sleep 5
  timeout=$((timeout+5))
done
if [ "$timeout" -ge 300 ]; then
  echo "Timeout deploying Sampleapp"
  exit 1
fi
echo "Sampleapp ready!"
set -x

kubectl -n default run injector --image=alpine -- \
    /bin/sh -c "apk add --no-cache --yes curl; \
    while true; do curl -sS --max-time 3 \
    http://sampleapp:8080/; done"

sleep 360
START_TIME=$(date -u +'%Y-%m-%dT%H:%M:%S+00:00' -d '-6min')
END_TIME=$(date -u +'%Y-%m-%dT%H:%M:%S+00:00' -d '-1min')
set +x
echo "Getting auth token"
TOKEN=$(gcloud auth application-default print-access-token)
echo "Getting metrics from Stackdriver"
curl --fail -H "Authorization: Bearer ${TOKEN}" -G \
    --data-urlencode interval.startTime=$START_TIME \
    --data-urlencode interval.endTime=$END_TIME \
    --data aggregation.alignmentPeriod=60s \
    --data aggregation.crossSeriesReducer=REDUCE_SUM \
    --data aggregation.groupByFields=metric.label.http_code \
    --data aggregation.perSeriesAligner=ALIGN_RATE \
    --data filter=metric.type+%3D+%22external.googleapis.com%2Fprometheus%2Frequests%22 \
    "https://content-monitoring.googleapis.com/v3/projects/kayenta-solution-ci/timeSeries" > stackdriver.json
cat stackdriver.json
set -x
RATE_500=$(cat stackdriver.json | jq '.timeSeries[] | select(.metric.labels.http_code == "500") | [.points[].value.doubleValue] | length as $array_length | add / $array_length')
RATE_200=$(cat stackdriver.json | jq '.timeSeries[] | select(.metric.labels.http_code == "200") | [.points[].value.doubleValue] | length as $array_length | add / $array_length')
ERROR_RATE=$(echo "scale=2;$RATE_500 * 100 / ($RATE_200 + $RATE_500)" | bc)
if [ $(echo "$ERROR_RATE >= 25" | bc) -eq 1 -a $(echo "$ERROR_RATE <= 35" | bc) -eq 1 ]; then
  echo "Error rate $ERROR_RATE is between 25 and 35, as expected."
else
  echo "Error rate $ERROR_RATE is NOT between 25 and 35, as it should be."
  exit 1
fi
