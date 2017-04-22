export SPIN_SCRIPT_PATH=~/spinnaker/experimental/kubernetes/simple
bash $SPIN_SCRIPT_PATH/scripts/startup-namespace.sh

bash $SPIN_SCRIPT_PATH/scripts/startup-redis.sh

bash $SPIN_SCRIPT_PATH/scripts/startup-config.sh

bash $SPIN_SCRIPT_PATH/scripts/startup-spinnaker.sh
