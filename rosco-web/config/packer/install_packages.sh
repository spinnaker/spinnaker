#!/bin/bash

# Make the build fail on errors.
set -e

# Strip the first part to avoid credentials leaks.
echo "repository=$(echo $repository | sed s/^.*@//g)"
echo "package_type=$package_type"
echo "packages=$packages"
echo "upgrade=$upgrade"

# Strip leading/trailing quotes if present.
repository=`echo $repository | sed 's/^"\(.*\)"$/\1/'`

# Strip leading/trailing quotes if present.
# Also convert a comma-separated list to a whitespace-separated one.
packages=`echo $packages | sed 's/^"\(.*\)"$/\1/' | sed 's/,/ /g'`


function provision_deb() {
  # https://www.packer.io/docs/builders/amazon-chroot.html look at gotchas at the end.
  if [[ "$disable_services" == "true" ]]; then
    echo "creating /usr/sbin/policy-rc.d to prevent services from being started"
    echo '#!/bin/sh' | sudo tee /usr/sbin/policy-rc.d > /dev/null
    echo 'exit 101' | sudo tee -a /usr/sbin/policy-rc.d > /dev/null
    sudo chmod a+x /usr/sbin/policy-rc.d
  fi

  if [[ "$repository" != "" ]]; then
    IFS=';' read -ra repo <<< "$repository"
    for i in "${repo[@]}"; do
      echo "deb $i" | sudo tee -a /etc/apt/sources.list.d/spinnaker.list > /dev/null
    done
  fi

  sudo apt-get update
  if [[ "$upgrade" == "true" ]]; then
    sudo unattended-upgrade -v
  fi

  # Enforce the package installation order.
  for package in $packages; do sudo DEBIAN_FRONTEND=noninteractive apt-get install --force-yes -y $package; done

  # https://www.packer.io/docs/builders/amazon-chroot.html look at gotchas at the end.
  if [[ "$disable_services" == "true" ]]; then
    echo "removing /usr/sbin/policy-rc.d"
    sudo rm -f /usr/sbin/policy-rc.d
  fi

  if [[ "$repository" != "" ]]; then
    # Cleanup repository configuration
    sudo rm /etc/apt/sources.list.d/spinnaker.list
  fi
}

function provision_rpm() {
  if [[ "$repository" != "" ]]; then
    IFS=';' read -ra repo <<< "$repository"
    for i in "${!repo[@]}"; do
      cat > /tmp/spinnaker-$i.repo <<EOF
[spinnaker-$i]
name=spinnaker-$i
baseurl=${repo[$i]}
gpgcheck=0
enabled=1
EOF
    done
    sudo mv /tmp/spinnaker*.repo /etc/yum.repos.d/
  fi

  if [[ "$upgrade" == "true" ]]; then
    sudo yum -y update
  fi

  # Enforce the package installation order.
  for package in $packages; do sudo yum -y install $package; done

  if [[ "$repository" != "" ]]; then
    # Cleanup repository configuration
    sudo rm /etc/yum.repos.d/spinnaker*.repo
  fi
}

function main() {
  if [[ "$package_type" == "deb" ]]; then
    provision_deb
  elif [[ "$package_type" == "rpm" ]]; then
    provision_rpm
  fi
}

main
