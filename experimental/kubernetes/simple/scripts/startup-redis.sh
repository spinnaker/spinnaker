echo "Bootstrapping redis master..."
echo

# Create a service to track the servers
kubectl create -f svcs/data-redis-server.yaml

# Create a replication controller for redis servers
kubectl create -f rcs/data-redis-master.yaml
