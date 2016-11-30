kubectl create secret generic fiat-ro-config \
    --from-file=fiat/config/fiat.yaml \
    --from-file=fiat/ro/config/fiat-local.yaml \
    --from-file=config/spinnaker.yaml \
    --from-file=config/spinnaker-local.yaml \
    --namespace=spinnaker
