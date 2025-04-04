#!/usr/bin/env bash

# Main project elements
git subtree add -P clouddriver git@github.com:spinnaker/clouddriver.git master
git subtree add -P deck git@github.com:spinnaker/deck.git master
git subtree add -P deck-kayenta git@github.com:spinnaker/deck-kayenta.git master
git subtree add -P echo git@github.com:spinnaker/echo.git master
git subtree add -P fiat git@github.com:spinnaker/fiat.git master
git subtree add -P front50 git@github.com:spinnaker/front50.git master
git subtree add -P gate git@github.com:spinnaker/gate.git master
git subtree add -P halyard git@github.com:spinnaker/halyard.git master
git subtree add -P igor git@github.com:spinnaker/igor.git master
git subtree add -P kayenta git@github.com:spinnaker/kayenta.git master
git subtree add -P keel git@github.com:spinnaker/keel.git master
git subtree add -P kork git@github.com:spinnaker/kork.git master
git subtree add -P orca git@github.com:spinnaker/orca.git master
git subtree add -P rosco git@github.com:spinnaker/rosco.git master
git subtree add -P spin git@github.com:spinnaker/spin.git master

# Supporting projects
git subtree add -P spinnaker-gradle-project git@github.com:spinnaker/spinnaker-gradle-project.git master

git fetch --all
