echo "Starting all spinnaker components..."

kubectl get namespace spinnaker &> /dev/null

if [ $? -ne 0 ]; then
    kubectl create namespace spinnaker
fi

bash redis/startup-components.sh
bash clouddriver/startup-components.sh
bash orca/startup-components.sh
bash echo/startup-components.sh
bash front50/startup-components.sh
bash gate/startup-components.sh
bash deck/startup-components.sh
bash igor/startup-components.sh
bash rosco/startup-components.sh
bash fiat/startup-components.sh
