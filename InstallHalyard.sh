#!/usr/bin/env bash

echo "$(tput bold)The install script has moved!$(tput sgr0)" 
echo "Please install with the following script: "
echo ""
echo "curl -O https://raw.githubusercontent.com/spinnaker/halyard/master/install/stable/InstallHalyard.sh" 
echo "sudo bash InstallHalyard.sh"
exit 1
