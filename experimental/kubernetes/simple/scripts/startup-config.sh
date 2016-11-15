cp ../../../config/*.yml config
rm config/default-spinnaker-local.yml

kubectl create secret generic spinnaker-config \
    --from-file=./config/. \
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

