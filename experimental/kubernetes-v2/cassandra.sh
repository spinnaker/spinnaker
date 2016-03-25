echo "Configuring cassandra cluster..."
echo

kubectl create -f svcs/spkr-cassandra.yaml

kubectl create -f rcs/spkr-cassandra.yaml

echo
echo "Waiting for cassandra to come up..."
echo

sleep 20

echo "Setting keyspaces for cassandra..."
echo

kubectl create -f jobs/spkr-cassandra-keys.yaml

sleep 20

echo
echo "Cleaning keyspace job..."
echo

kubectl delete job spkr-cassandra-keys --namespace=spinnaker
