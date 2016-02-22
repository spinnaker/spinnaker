#!/bin/bash

# Strip the first part to avoid credentials leaks
echo "repository=$(echo $repository | sed s/^.*@//g)"
echo "package_type=$package_type"
echo "packages=$packages"
echo "upgrade=$upgrade"

# Strip leading/trailing quotes if present.
repository=`echo $repository | sed 's/^"\(.*\)"$/\1/'`

# Strip leading/trailing quotes if present.
# Also convert a comma separated list to a whitespace separated one
packages=`echo $packages | sed 's/^"\(.*\)"$/\1/' | sed 's/,/ /g'`


function provision_deb() {
  if [[ "$repository" != "" ]]; then
    echo "deb $repository" | sudo tee /etc/apt/sources.list.d/spinnaker.list > /dev/null
  fi

  sudo apt-get update
  if [[ "$upgrade" == "true" ]]; then
    sudo unattended-upgrade -v
  fi

  # Enforce the package installation order
  for package in $packages; do sudo apt-get install --force-yes -y $package; done
}

function provision_rpm() {
  if [[ "$repository" != "" ]]; then
    cat > /etc/yum.repos.d/spinnaker.repo <<EOF
[spinnaker]
name=spinnaker
baseurl=$repository
gpgcheck=0
enabled=1
EOF
  fi

  if [[ "$upgrade" == "true" ]]; then
    yum -y update
  fi

  yum -y install $packages
}

function main() {
  if [[ "$package_type" == "deb" ]]; then
    provision_deb
  elif [[ "$package_type" == "rpm" ]]; then
    provision_rpm
  fi
}

main
