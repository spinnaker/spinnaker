#!/bin/bash

echo "deb_repo=$deb_repo"
echo "packages=$packages"
echo "upgrade=$upgrade"

# Strip leading/trailing quotes if present.
deb_repo=`echo $deb_repo | sed 's/^"\(.*\)"$/\1/'`

# Support space-separated list of packages to install.
# Strip leading/trailing quotes if present.
packages=`echo $packages | sed 's/^"\(.*\)"$/\1/'`

# Only add the new source repository if deb_repo is set.
if [[ "$deb_repo" != "" ]]; then
  echo "deb $deb_repo" | sudo tee /etc/apt/sources.list.d/spinnaker.list > /dev/null
fi

sudo apt-get update

if [[ "$upgrade" == "true" ]]; then
  sudo unattended-upgrade -v
fi

sudo apt-get install --force-yes -y $packages
