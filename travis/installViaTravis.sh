#!/bin/bash
# This script will install the project.

NODE_JS_VERSION=`node -e 'console.log(require("./package.json").engines.node.replace(/[^\d\.]/g, ""))'`;
echo -e "Installing/activating node v$NODE_JS_VERSION"

nvm install $NODE_JS_VERSION
yarn
