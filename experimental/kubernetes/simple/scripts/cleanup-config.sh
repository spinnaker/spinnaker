kubectl delete secret spinnaker-config --namespace=spinnaker
kubectl delete secret creds-config --namespace=spinnaker
kubectl delete secret aws-config --namespace=spinnaker

CONF_DIR=../../../config/

for FILENAME in $CONF_DIR*.yml; do
    if [ ${FILENAME:${#CONF_DIR}} != "default-spinnaker-local.yml" ]; then
        rm config/${FILENAME:${#CONF_DIR}}
    fi
done
