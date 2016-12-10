kubectl delete namespace spinnaker

if [ $? -eq 0 ]; then
    printf 'Waiting for the spinnaker namespace to be deleted'
fi

kubectl get namespace spinnaker &> /dev/null

while [ $? -eq 0 ]; do
    sleep 1
    printf .
    kubectl get namespace spinnaker &> /dev/null
done

printf '\n'
