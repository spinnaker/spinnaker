echo "Bootstrapping redis master..."
echo

# Create a service to track the servers
kubectl create -f svcs/data-redis-server.yaml

# Create a replica set for redis servers
kubectl create -f rs/data-redis-master.yaml
