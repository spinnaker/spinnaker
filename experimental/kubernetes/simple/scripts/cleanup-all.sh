kubectl delete -f namespaces/namespace.yaml

kubectl get -f namespaces/namespace.yaml &> /dev/null

while [ $? -eq 0 ]; do
    sleep 1
    kubectl get -f namespaces/namespace.yaml
done
