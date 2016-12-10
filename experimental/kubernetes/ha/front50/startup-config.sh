GCP_CREDS=""

if [ -f $HOME/.gcp/account.json ]; then
    GCP_CREDS="$GENERIC_CREDS --from-file=$HOME/.gcp/account.json"
fi

kubectl create secret generic gcp-config --namespace=spinnaker $GCP_CREDS

AWS_CREDS=""

if [ -f $HOME/.aws/credentials ]; then
    AWS_CREDS="$GENERIC_CREDS --from-file=$HOME/.aws/credentials"
fi

kubectl create secret generic aws-config --namespace=spinnaker $AWS_CREDS

kubectl create secret generic front50-config \
    --from-file=front50/config/front50.yaml \
    --from-file=front50/config/front50-local.yaml \
    --from-file=config/spinnaker.yaml \
    --from-file=config/spinnaker-local.yaml \
    --namespace=spinnaker
