echo "Starting all services..."
echo

kubectl create -f svcs/spin-clouddriver.yaml
kubectl create -f svcs/spin-deck.yaml
kubectl create -f svcs/spin-echo.yaml
kubectl create -f svcs/spin-front50.yaml
kubectl create -f svcs/spin-gate.yaml
kubectl create -f svcs/spin-igor.yaml
kubectl create -f svcs/spin-orca.yaml

echo
echo "Starting all replication controllers..."
echo

kubectl create -f rcs/spin-clouddriver.yaml
kubectl create -f rcs/spin-deck.yaml
kubectl create -f rcs/spin-echo.yaml
kubectl create -f rcs/spin-front50.yaml
kubectl create -f rcs/spin-gate.yaml
kubectl create -f rcs/spin-igor.yaml
kubectl create -f rcs/spin-orca.yaml
