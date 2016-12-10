kubectl delete svc -l app=redis --namespace=spinnaker
kubectl delete rc -l app=redis --namespace=spinnaker

if [ $? -eq 0 ]; then
    printf 'Waiting for all Redis instances to be gone'
fi

kubectl get pods --namespace=spinnaker -l app=redis -o=jsonpath="{.items[0]}" &> /dev/null

while [ $? -eq 0 ]; do
    sleep 1
    printf .
    kubectl get pods --namespace=spinnaker -l app=redis -o=jsonpath="{.items[0]}" &> /dev/null
done

printf '\n'
