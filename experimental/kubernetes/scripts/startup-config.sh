cp ../../config/*.yml config
rm config/default-spinnaker-local.yml

kubectl create secret generic spinnaker-config \
    --from-file=./config/clouddriver.yml \
    --from-file=./config/clouddriver-local.yml \
    --from-file=./config/echo.yml \
    --from-file=./config/echo-local.yml \
    --from-file=./config/front50.yml \
    --from-file=./config/front50-local.yml \
    --from-file=./config/gate.yml \
    --from-file=./config/gate-local.yml \
    --from-file=./config/igor.yml \
    --from-file=./config/igor-local.yml \
    --from-file=./config/orca.yml \
    --from-file=./config/orca-local.yml \
    --from-file=./config/settings.js \
    --from-file=./config/spinnaker.yml \
    --from-file=./config/spinnaker-local.yml \
    --namespace=spinnaker

kubectl create secret generic kube-config \
    --from-file=$HOME/.kube/config \
    --namespace=spinnaker
