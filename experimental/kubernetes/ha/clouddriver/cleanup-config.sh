kubectl delete secret kube-config --namespace=spinnaker
kubectl delete secret gcp-config --namespace=spinnaker
kubectl delete secret aws-config --namespace=spinnaker

bash clouddriver/cache/cleanup-config.sh
bash clouddriver/mutate/cleanup-config.sh
bash clouddriver/ro/cleanup-config.sh
