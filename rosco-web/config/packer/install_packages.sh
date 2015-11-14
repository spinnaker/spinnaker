#!/bin/bash

echo "deb_repo=$deb_repo"
echo "packages=$packages"

# Strip leading/trailing quotes if present.
deb_repo=`echo $deb_repo | sed 's/^"\(.*\)"$/\1/'`

# Support space-separated list of packages to install.
# Strip leading/trailing quotes if present.
packages=`echo $packages | sed 's/^"\(.*\)"$/\1/'`

echo "deb $deb_repo" | sudo tee /etc/apt/sources.list.d/spinnaker.list > /dev/null
sudo apt-get update
sudo apt-get install --force-yes -y $packages
