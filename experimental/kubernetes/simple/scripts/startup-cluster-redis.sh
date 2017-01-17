echo "Bootstrapping redis master..."
echo

# Create a bootstrap master
kubectl create -f pods/data-redis-master.yaml

# Create a service to track the servers
kubectl create -f svcs/data-redis-server.yaml

# Create a service to track the sentinels
kubectl create -f svcs/data-redis-sentinel.yaml

# Create a replica set for redis servers
kubectl create -f rs/data-redis-server.yaml

# Create a replica set for redis sentinels
kubectl create -f rs/data-redis-sentinel.yaml

echo
echo "Waiting a bit for all resources to be ready..."
echo

sleep 31

echo "Scaling redis server & sentinel..."
echo

# Scale both replica sets
kubectl scale rs data-redis-server-v000 --replicas=3 --namespace=spinnaker
kubectl scale rs data-redis-sentinel-v000 --replicas=3 --namespace=spinnaker

echo
echo "Waiting a bit for master selection and sentinel master sharing..."
echo

sleep 41

# Delete the original master pod
kubectl delete pods data-redis-master-v000-init --namespace=spinnaker

echo
echo "Finished configuring redis"
echo
