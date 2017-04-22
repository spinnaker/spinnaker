kubectl get namespace spinnaker &> /dev/null

if [ $? -eq 0 ]; then
    echo "Namespace 'spinnaker' already exists."
else
    kubectl create -f $SPIN_SCRIPT_PATH/namespaces/namespace.yaml
fi
