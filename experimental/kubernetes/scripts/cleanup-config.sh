kubectl delete secret kube-config --namespace=spinnaker
kubectl delete secret spinnaker-config --namespace=spinnaker

CONF_DIR=../../config/

for FILENAME in $CONF_DIR*.yml; do
    rm config/${FILENAME:${#CONF_DIR}}
done
