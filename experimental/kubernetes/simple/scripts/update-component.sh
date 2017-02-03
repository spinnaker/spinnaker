kubectl delete rs -l stack=$1 --namespace=spinnaker

kubectl create -f $SPIN_SCRIPT_PATH/rs/spin-$1.yaml
