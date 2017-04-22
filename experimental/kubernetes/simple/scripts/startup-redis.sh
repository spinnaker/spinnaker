echo "Bootstrapping redis master..."
echo

# Create a service to track the servers
kubectl create -f $SPIN_SCRIPT_PATH/svcs/data-redis-server.yaml

# Create a replica set for redis servers
kubectl create -f $SPIN_SCRIPT_PATH/rs/data-redis-master.yaml
