#!/bin/bash
. ~/.nvm/nvm.sh

nvm install $npm_package_engines_node

./node_modules/protractor/bin/webdriver-manager update
./node_modules/protractor/bin/protractor protractor.conf.js

