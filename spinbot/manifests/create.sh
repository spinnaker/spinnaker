#!/usr/bin/env bash

kubectl create namespace spinbot
kubectl create secret generic -n spinbot token --from-file ~/.spinbot/token
kubectl create secret generic -n spinbot gcs.creds --from-file ~/.gcp/account.json
kubectl apply -f deploy.yml
