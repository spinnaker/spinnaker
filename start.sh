#!/bin/sh

CURRENT_VERSION=$(node -v)

# Process engines format like { "node": ">=7.0.0" }
DESIRED_VERSION=$(node -e "console.log(require('./package.json').engines.node.replace(/[^0-9.]/g, ''))");

if [ $CURRENT_VERSION != "v$DESIRED_VERSION" ]; then
  if [ -f $HOME/.nvm/nvm.sh ]; then
    echo "Node is currently $CURRENT_VERSION. Activating $DESIRED_VERSION using nvm..."
    . $HOME/.nvm/nvm.sh
    echo "Using $DESIRED_VERSION...";
    nvm use $DESIRED_VERSION

    if [ $? != 0 ]; then
      echo "Installing node $DESIRED_VERSION..."
      nvm install $DESIRED_VERSION
    fi
  else
    echo "WARNING: could not update to node $DESIRED_VERSION, nvm not found..."
  fi
fi

echo "Launching deck using node $(node -v)..."

npm run start-dev-server
