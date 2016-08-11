kubectl create secret generic orca-config \
    --from-file=orca/config/orca.yaml \
    --from-file=orca/config/orca-local.yaml \
    --from-file=config/spinnaker.yaml \
    --from-file=config/spinnaker-local.yaml \
    --namespace=spinnaker
