echo "Bootstraping redis master..."
echo

# Create a bootstrap master
kubectl create -f pods/spkr-redis-master.yaml

# Create a service to track the servers
# kubectl create -f svcs/spkr-redis-server.yaml

# Create a service to track the sentinels
kubectl create -f svcs/spkr-redis-sentinel.yaml

# Create a replication controller for redis servers
kubectl create -f rcs/spkr-redis-server.yaml

# Create a replication controller for redis sentinels
kubectl create -f rcs/spkr-redis-sentinel.yaml

echo
echo "Waiting a bit for all resources to be ready..."
echo

sleep 20

echo "Scaling redis server & sentinel..."
echo

# Scale both replication controllers
kubectl scale rc spkr-redis-server-v000 --replicas=3 --namespace=spinnaker
kubectl scale rc spkr-redis-sentinel-v000 --replicas=3 --namespace=spinnaker

echo
echo "Waiting a bit for master selection and sentinel master sharing..."
echo

sleep 30

# Delete the original master pod
kubectl delete pods spkr-redis-master-v000-init --namespace=spinnaker

echo
echo "Finished configuring redis"
echo
