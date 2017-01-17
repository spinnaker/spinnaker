kubectl delete rs -l stack=$1 --namespace=spinnaker

kubectl create -f rs/spin-$1.yaml
