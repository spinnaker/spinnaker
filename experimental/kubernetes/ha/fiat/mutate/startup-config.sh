GCP_CREDS=""

if [ -f $HOME/.gcp/account.json ]; then
    GCP_CREDS="$GENERIC_CREDS --from-file=$HOME/.gcp/account.json"
fi

kubectl create secret generic gcp-config --namespace=spinnaker $GCP_CREDS

kubectl create secret generic fiat-mutate-config \
    --from-file=fiat/config/fiat.yaml \
    --from-file=fiat/mutate/config/fiat-local.yaml \
    --from-file=config/spinnaker.yaml \
    --from-file=config/spinnaker-local.yaml \
    --namespace=spinnaker
