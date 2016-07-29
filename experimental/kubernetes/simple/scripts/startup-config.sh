cp ../../../config/*.yml config
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

GENERIC_CREDS="--from-file=$HOME/.kube/config"

if [ -f $HOME/.gcp/account.json ]; then
    GENERIC_CREDS="$GENERIC_CREDS --from-file=$HOME/.gcp/account.json"
fi

kubectl create secret generic creds-config \
    $GENERIC_CREDS --namespace=spinnaker

AWS_CREDS=""

if [ -f $HOME/.aws/credentials ]; then
    AWS_CREDS="$AWS_CREDS --from-file=$HOME/.aws/credentials"
fi

kubectl create secret generic aws-config \
    $AWS_CREDS --namespace=spinnaker

