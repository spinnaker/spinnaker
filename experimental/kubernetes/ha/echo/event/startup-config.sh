kubectl create secret generic echo-event-config \
    --from-file=echo/event/config/echo-local.yaml \
    --from-file=echo/config/echo.yaml \
    --from-file=config/spinnaker.yaml \
    --from-file=config/spinnaker-local.yaml \
    --namespace=spinnaker
