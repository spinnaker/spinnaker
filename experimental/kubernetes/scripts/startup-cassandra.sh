echo "Configuring cassandra cluster..."
echo

kubectl create -f svcs/data-cassandra.yaml

kubectl create -f rcs/data-cassandra.yaml

echo
echo "Waiting for cassandra to come up..."
echo

echo "Setting keyspaces for cassandra..."
echo

kubectl create -f jobs/data-cassandra-keys.yaml

SUCCESS=$(kubectl get job data-cassandra-keys --namespace=spinnaker -o=jsonpath="{.status.succeeded}")

while [ $SUCCESS -ne "1" ]; do
    SUCCESS=$(kubectl get job data-cassandra-keys --namespace=spinnaker -o=jsonpath="{.status.succeeded}")
done


echo
echo "Cleaning keyspace job..."
echo

kubectl delete job data-cassandra-keys --namespace=spinnaker
