kubectl create secret generic gate-config \
    --from-file=gate/config/gate.yaml \
    --from-file=gate/config/gate-local.yaml \
    --from-file=config/spinnaker.yaml \
    --from-file=config/spinnaker-local.yaml \
    --namespace=spinnaker
