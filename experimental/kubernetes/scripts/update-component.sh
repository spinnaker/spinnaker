kubectl delete rc -l stack=$1 --namespace=spinnaker

kubectl create -f rcs/spkr-$1.yaml
