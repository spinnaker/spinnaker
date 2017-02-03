export SPIN_SCRIPT_PATH=~/spinnaker/experimental/kubernetes/simple
kubectl delete -f $SPIN_SCRIPT_PATH/namespaces/namespace.yaml

kubectl get -f $SPIN_SCRIPT_PATH/namespaces/namespace.yaml &> /dev/null

while [ $? -eq 0 ]; do
    sleep 1
    kubectl get -f $SPIN_SCRIPT_PATH/namespaces/namespace.yaml
done
