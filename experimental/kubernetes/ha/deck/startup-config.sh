kubectl create secret generic deck-config \
    --from-file=deck/config/settings.js \
    --namespace=spinnaker
