#!/bin/bash
# This script will build the project.

if [ "$TRAVIS_PULL_REQUEST" != "false" ]; then
  NODE_JS_VERSION=`node -e 'console.log(require("./package.json").engines.node.replace(/[^\d\.]/g, ""))'`;
  echo -e "Installing/activating nodejs v$NODE_JS_VERSION"

  # http://austinpray.com/ops/2015/09/20/change-travis-node-version.html
  # Clear out whatever version of NVM Travis has. ; Their version of NVM is probably old.
  rm -rf ~/.nvm
  git clone https://github.com/creationix/nvm.git ~/.nvm

  # Checkout the latest stable nvm tag.
  (cd ~/.nvm && git checkout `git describe --abbrev=0 --tags`)

  source ~/.nvm/nvm.sh
  nvm install $NODE_JS_VERSION
  yarn --frozen-lockfile

elif [ "$TRAVIS_PULL_REQUEST" == "false" ] && [ "$TRAVIS_TAG" == "" ]; then
  echo -e 'Assemble Branch with Snapshot => Branch ['$TRAVIS_BRANCH']'
  ./gradlew -Prelease.travisci=true assemble

elif [ "$TRAVIS_PULL_REQUEST" == "false" ] && [ "$TRAVIS_TAG" != "" ]; then
  echo -e 'Assemble Branch for Release => Branch ['$TRAVIS_BRANCH']  Tag ['$TRAVIS_TAG']'
  ./gradlew -PdeckVersion="${TRAVIS_TAG}" -Prelease.travisci=true -Prelease.useLastTag=true assemble

else
  echo -e 'WARN: Should not be here => Branch ['$TRAVIS_BRANCH']  Tag ['$TRAVIS_TAG']  Pull Request ['$TRAVIS_PULL_REQUEST']'
  ./gradlew assemble

fi

