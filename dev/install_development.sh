#!/bin/bash

# This script assumes that the external dependencies were already installed.
# to get these, run sudo InstallSpinnaker.sh --dependencies_only

NVM_VERSION=v0.26.0

function process_args() {
  while [[ $# > 0 ]]
  do
    local key="$1"
    shift
    case $key in
        --no_awscli)
            NO_AWSCLI=true
            ;;
        # Keep around for compatibility with docs people have.
        --package_manager)
            echo "--package_manager option is no longer needed and deprecated."
            ;;
        --nopackage_manager)
            echo "--nopackage_manager is not currently supported,"
            echo "                    but will be re-introduced in the future."
            ;;
      *)
          echo "ERROR: Unknown argument '$key'"
          exit -1
    esac
  done
}


process_args "$@"

sudo apt-get install -y git
sudo apt-get install -y zip
sudo apt-get install -y build-essential
sudo apt-get install -y python-virtualenv libffi-dev libssl-dev python-dev

# redis normally comes in from a dependency on spinnaker,
# which we have not installed.
sudo apt-get install -y redis-server


# Add shortcut devvm host for convenience
if ! egrep '(^| )devvm( |$)' /etc/hosts; then
    sed -i 's/^127.0.0.1 /127.0.0.1 devvm /'
fi

# Install nvm (for deck UI)
sudo chmod 775 /usr/local
sudo mkdir -m 777 -p /usr/local/node /usr/local/nvm

sudo bash -c "curl -o- https://raw.githubusercontent.com/creationix/nvm/$NVM_VERSION/install.sh | NVM_DIR=/usr/local/nvm bash"

content=$(cat <<EOF
export NVM_DIR=/usr/local/nvm
source /usr/local/nvm/nvm.sh

export NPM_CONFIG_PREFIX=/usr/local/node
export PATH="/usr/local/node/bin:\$PATH"
EOF
)
sudo bash -c "echo '$content' > /etc/profile.d/nvm.sh"


# Install aws command-line tool (for convenience)
if [ "x$NO_AWSCLI" = "x" ] && ! aws --version >& /dev/null; then
    sudo apt-get install -y awscli
fi

# Install google command-line tool (for convenience)
# in the bootstrap_dev.sh because it typically is not installed as root.

# Install Halyard (https://github.com/spinnaker/halyard)
cd /tmp
curl -s -O https://raw.githubusercontent.com/spinnaker/halyard/master/install/stable/InstallHalyard.sh
chmod +x ./InstallHalyard.sh
sudo bash -c ./InstallHalyard.sh
