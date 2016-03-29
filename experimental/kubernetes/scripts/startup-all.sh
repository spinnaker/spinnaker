kubectl get namespace spinnaker &> /dev/null

if [ $? -eq 0 ]; then
    echo "Namespace 'spinnaker' already exists."
else
    kubectl create -f namespaces/spinnaker.yaml
fi

bash scripts/startup-redis.sh
bash scripts/startup-cassandra.sh

bash scripts/startup-config.sh

bash scripts/startup-spinnaker.sh
