kubectl create secret generic clouddriver-mutate-config \
    --from-file=clouddriver/mutate/config/clouddriver-local.yaml \
    --from-file=clouddriver/accounts/clouddriver.yaml \
    --from-file=config/spinnaker.yaml \
    --from-file=config/spinnaker-local.yaml \
    --namespace=spinnaker
