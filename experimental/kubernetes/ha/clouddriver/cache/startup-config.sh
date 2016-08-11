kubectl create secret generic clouddriver-cache-config \
    --from-file=clouddriver/cache/config/clouddriver-local.yaml \
    --from-file=clouddriver/accounts/clouddriver.yaml \
    --from-file=config/spinnaker.yaml \
    --from-file=config/spinnaker-local.yaml \
    --namespace=spinnaker
