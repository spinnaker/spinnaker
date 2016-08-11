compname=$(kubectl get pod -l replication-controller=spin-$1-v000 --namespace=spinnaker -o=jsonpath='{.items[0].metadata.name}')

echo "Getting logs from pod " $compname

kubectl logs $compname --namespace=spinnaker
