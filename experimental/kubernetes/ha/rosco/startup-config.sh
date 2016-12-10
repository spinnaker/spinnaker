kubectl create secret generic rosco-config \
    --from-file=rosco/config/rosco.yaml \
    --from-file=rosco/config/rosco-local.yaml \
    --from-file=config/spinnaker.yaml \
    --from-file=config/spinnaker-local.yaml \
    --namespace=spinnaker
