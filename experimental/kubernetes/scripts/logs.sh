compname=$(kubectl get pod -l replication-controller=spkr-$1-v000 --namespace=spinnaker -o=jsonpath='{.items[0].metadata.name}')

echo "Logging " $compname

kubectl log $compname --namespace=spinnaker
