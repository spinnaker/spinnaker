echo "Starting all services..."
echo

kubectl create -f svcs/spkr-clouddriver.yaml
kubectl create -f svcs/spkr-deck.yaml
kubectl create -f svcs/spkr-echo.yaml
kubectl create -f svcs/spkr-front50.yaml
kubectl create -f svcs/spkr-gate.yaml
kubectl create -f svcs/spkr-igor.yaml
kubectl create -f svcs/spkr-orca.yaml

echo
echo "Starting all replication controllers..."
echo

kubectl create -f rcs/spkr-clouddriver.yaml
kubectl create -f rcs/spkr-deck.yaml
kubectl create -f rcs/spkr-echo.yaml
kubectl create -f rcs/spkr-front50.yaml
kubectl create -f rcs/spkr-gate.yaml
kubectl create -f rcs/spkr-igor.yaml
kubectl create -f rcs/spkr-orca.yaml
