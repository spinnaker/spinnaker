#!/usr/bin/env bash

kubectl --context utils-cluster create namespace spinbot
kubectl --context utils-cluster create secret generic -n spinbot token --from-file ~/.spinbot/token
kubectl --context utils-cluster create secret generic -n spinbot gcs.creds --from-file ~/.gcp/account.json
kubectl --context utils-cluster apply -f deploy.yml
