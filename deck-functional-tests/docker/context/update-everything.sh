#!/usr/bin/env bash

set -x
set -e

# Update deck to latest master branch and build production assets

# This file is intended to be run inside a built deck-functional-tests container

cd /

rm -rf /deck
rm -rf /deck-functional-tests
rm -rf /spinnaker

git clone --depth 1 --branch master https://github.com/spinnaker/deck /deck
git clone --depth 1 --branch master https://github.com/spinnaker/spinnaker /spinnaker

mv /spinnaker/deck-functional-tests /deck-functional-tests

cd /deck
yarn
yarn build

cd /deck-functional-tests
yarn

ln -s /deck/build/webpack /deck-functional-tests/static
