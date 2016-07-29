kubectl create secret generic echo-sched-config \
    --from-file=echo/sched/config/echo-local.yaml \
    --from-file=echo/config/echo.yaml \
    --from-file=config/spinnaker.yaml \
    --from-file=config/spinnaker-local.yaml \
    --namespace=spinnaker
