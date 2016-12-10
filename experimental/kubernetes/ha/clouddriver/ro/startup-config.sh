kubectl create secret generic clouddriver-ro-config \
    --from-file=clouddriver/ro/config/clouddriver-local.yaml \
    --from-file=clouddriver/accounts/clouddriver.yaml \
    --from-file=config/spinnaker.yaml \
    --from-file=config/spinnaker-local.yaml \
    --namespace=spinnaker
