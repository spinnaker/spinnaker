#!/bin/bash
# This script will build the project.

echo -e "Running unit tests..."
source ~/.nvm/nvm.sh
NODE_JS_VERSION=`node -e 'console.log(require("./package.json").engines.node.replace(/[^\d\.]/g, ""))'`;
nvm install $NODE_JS_VERSION

./node_modules/.bin/karma start --single-run
