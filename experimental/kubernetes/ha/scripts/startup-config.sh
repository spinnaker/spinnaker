echo "Creating all spinnaker config..."

kubectl get namespace spinnaker &> /dev/null

if [ $? -ne 0 ]; then
    kubectl create namespace spinnaker
fi

bash clouddriver/startup-config.sh
bash orca/startup-config.sh
bash echo/startup-config.sh
bash front50/startup-config.sh
bash gate/startup-config.sh
bash deck/startup-config.sh
bash igor/startup-config.sh
bash rosco/startup-config.sh
bash fiat/startup-config.sh
