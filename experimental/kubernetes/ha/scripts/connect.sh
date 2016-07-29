compname=$(kubectl get pod -l replication-controller=spin-$1-v000 --namespace=spinnaker -o=jsonpath='{.items[0].metadata.name}')

echo "Connecting to" $compname

kubectl port-forward $compname $2:$2 --namespace=spinnaker
