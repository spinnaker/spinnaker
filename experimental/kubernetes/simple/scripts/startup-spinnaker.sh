echo "Starting all services..."
echo

kubectl create -f $SPIN_SCRIPT_PATH/svcs/spin-clouddriver.yaml
kubectl create -f $SPIN_SCRIPT_PATH/svcs/spin-deck.yaml
kubectl create -f $SPIN_SCRIPT_PATH/svcs/spin-echo.yaml
kubectl create -f $SPIN_SCRIPT_PATH/svcs/spin-front50.yaml
kubectl create -f $SPIN_SCRIPT_PATH/svcs/spin-gate.yaml
kubectl create -f $SPIN_SCRIPT_PATH/svcs/spin-igor.yaml
kubectl create -f $SPIN_SCRIPT_PATH/svcs/spin-orca.yaml

echo
echo "Starting all replica sets..."
echo

kubectl create -f $SPIN_SCRIPT_PATH/rs/spin-clouddriver.yaml
kubectl create -f $SPIN_SCRIPT_PATH/rs/spin-deck.yaml
kubectl create -f $SPIN_SCRIPT_PATH/rs/spin-echo.yaml
kubectl create -f $SPIN_SCRIPT_PATH/rs/spin-front50.yaml
kubectl create -f $SPIN_SCRIPT_PATH/rs/spin-gate.yaml
kubectl create -f $SPIN_SCRIPT_PATH/rs/spin-igor.yaml
kubectl create -f $SPIN_SCRIPT_PATH/rs/spin-orca.yaml
