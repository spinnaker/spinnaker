#!/usr/bin/env bash

PORT=8080
DECK_POD=$(kubectl get po -n spinnaker -l "cluster=spin-deck" \
  -o jsonpath="{.items[0].metadata.name}")

EXISTING_PID=$(sudo netstat -nlp | grep $PORT | awk '{print $7}' | cut -f1 -d '/')

if [ -n "$EXISTING_PID" ]; then
  echo "PID $EXISTING_PID already listening... restarting port-forward"
  kill $EXISTING_PID
  sleep 5
fi

kubectl port-forward $DECK_POD $PORT:9000 -n spinnaker >> /dev/null &

echo "Port opened on $PORT"
