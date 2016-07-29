kubectl create secret generic igor-config \
    --from-file=igor/config/igor.yaml \
    --from-file=igor/config/igor-local.yaml \
    --from-file=config/spinnaker.yaml \
    --from-file=config/spinnaker-local.yaml \
    --namespace=spinnaker
