bash $SPIN_SCRIPT_PATH/scripts/cleanup-spinnaker.sh
sleep 31 # graceful termination period is 30s
bash $SPIN_SCRIPT_PATH/scripts/startup-spinnaker.sh
