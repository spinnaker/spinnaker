#!/bin/bash
set -e

function init_local_yml() {
  CONFIG_DIR="/root/.spinnaker"
  LOCAL_CONFIG="$CONFIG_DIR/spinnaker-local.yml"
  if [[ ! -f $LOCAL_CONFIG ]]; then
    sudo mkdir -p $CONFIG_DIR
    sudo cp /opt/spinnaker/config/default-spinnaker-local.yml $LOCAL_CONFIG
    sudo chmod 600 $LOCAL_CONFIG
  fi
}

function update_spring_config() {
    spring="-Dspring.config.location=\/opt\/spinnaker\/config\/,\/root\/.spinnaker\/"
    for package in clouddriver echo front50 gate igor orca rosco rush; do
        bin_path=/opt/$package/bin/$package
        sudo sed -i "s/^\(DEFAULT_JVM_OPTS='\)\(.\+\)/\1\"${spring}\" \2/g" $bin_path
    done
}

sudo chmod +x /opt/spinnaker/scripts/*.sh
sudo chmod +x /opt/spinnaker/install/*.sh
init_local_yml
update_spring_config
sudo /opt/spinnaker/scripts/reconfigure_spinnaker.sh

