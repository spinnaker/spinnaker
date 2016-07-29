kubectl create secret generic kube-config \
    --from-file=$HOME/.kube/config --namespace=spinnaker

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

bash clouddriver/cache/startup-config.sh
bash clouddriver/mutate/startup-config.sh
bash clouddriver/ro/startup-config.sh
