kubectl delete rc -l stack=$1 --namespace=spinnaker

kubectl create -f rcs/spin-$1.yaml
