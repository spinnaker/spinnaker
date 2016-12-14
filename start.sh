#!/bin/bash

NODE_VERSION=$(node -v)

if [[ $NODE_VERSION != $npm_package_engines_node ]]; then
  if [[ -f $HOME/.nvm/nvm.sh ]]; then
    echo "Updating your node version to $npm_package_engines_node..."
    . $HOME/.nvm/nvm.sh
    nvm use $npm_package_engines_node

    if [[ $? != 0 ]]; then
      echo "Installing node $npm_package_engines_node..."
      nvm install $npm_package_engines_node
    fi
  else
    echo "WARNING: could not update to node $NODE_VERSION, nvm not found..."
  fi
fi

npm run start-dev-server
