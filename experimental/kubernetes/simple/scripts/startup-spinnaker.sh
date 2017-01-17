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
echo "Starting all replica sets..."
echo

kubectl create -f rs/spin-clouddriver.yaml
kubectl create -f rs/spin-deck.yaml
kubectl create -f rs/spin-echo.yaml
kubectl create -f rs/spin-front50.yaml
kubectl create -f rs/spin-gate.yaml
kubectl create -f rs/spin-igor.yaml
kubectl create -f rs/spin-orca.yaml
