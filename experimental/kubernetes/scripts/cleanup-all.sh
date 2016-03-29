kubectl delete rc --all --namespace=spinnaker
kubectl delete svc --all --namespace=spinnaker
kubectl delete jobs --all --namespace=spinnaker
kubectl delete secret spinnaker-config --namespace=spinnaker
kubectl delete secret kube-config --namespace=spinnaker
