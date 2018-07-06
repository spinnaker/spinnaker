#!/bin/bash

# Make the build fail on errors.
set -e

# Strip the first part to avoid credentials leaks.
echo "repository=$(echo $repository | sed s/^.*@//g)"
echo "package_type=$package_type"
echo "packages=$packages"
echo "upgrade=$upgrade"
if [[ -e /tmp/artifacts.json ]]; then
  echo "artifacts="
  cat /tmp/artifacts.json
  echo
fi
uninstall_jq=false

# Strip leading/trailing quotes if present.
repository=`echo $repository | sed 's/^"\(.*\)"$/\1/'`

# Strip leading/trailing quotes if present.
# Also convert a comma-separated list to a whitespace-separated one.
packages=`echo $packages | sed 's/^"\(.*\)"$/\1/' | sed 's/,/ /g'`

function ensure_jq_deb() {
  if ! dpkg-query -W jq; then
    sudo apt-get update
    sudo DEBIAN_FRONTEND=noninteractive apt-get install --force-yes -y jq
    uninstall_jq=true
  fi
}

function ensure_jq_rpm() {
  if ! rpm -q jq; then
    sudo yum -y install jq
    uninstall_jq=true
  fi
}

function ensure_jq() {
  if [[ "$package_type" == "deb" ]]; then
    ensure_jq_deb
  fi

  if [[ "$package_type" == "rpm" ]]; then
    ensure_jq_rpm
  fi
}

function remove_jq_deb() {
  if [[ "$uninstall_jq" = true ]]; then
    sudo DEBIAN_FRONTEND=noninteractive apt-get purge --force-yes -y jq
  fi
}

function remove_jq_rpm() {
  if [[ "$uninstall_jq" = true ]]; then
    sudo yum -y autoremove jq
  fi
}

function remove_jq() {
  if [[ "$package_type" == "deb" ]]; then
    remove_jq_deb
  fi

  if [[ "$package_type" == "rpm" ]]; then
    remove_jq_rpm
  fi
}

function get_artifact_references() {
  touch /tmp/repos
  if [[ "$repository" != "" ]]; then
    IFS=';' read -ra repo <<< "$repository"
    for i in "${repo[@]}"; do
      echo "$i" >> /tmp/repos
    done
  fi

  if [[ -e /tmp/artifacts.json ]]; then
    ensure_jq
    # The last field of each artifact reference is the package to install, and the remaning
    # fields are the repo from which to install it
    jq -r '.[] | .reference' /tmp/artifacts.json | awk 'NF{NF-=1} { print $0 }' >> /tmp/repos
    artifact_packages="$(jq -r '.[] | .reference' /tmp/artifacts.json | awk '{print $NF}')"
    remove_jq
    rm /tmp/artifacts.json
  fi

  readarray -t repo < <(sort /tmp/repos | uniq)
  rm /tmp/repos
}

function provision_deb() {
  # https://www.packer.io/docs/builders/amazon-chroot.html look at gotchas at the end.
  if [[ "$disable_services" == "true" ]]; then
    echo "creating /usr/sbin/policy-rc.d to prevent services from being started"
    echo '#!/bin/sh' | sudo tee /usr/sbin/policy-rc.d > /dev/null
    echo 'exit 101' | sudo tee -a /usr/sbin/policy-rc.d > /dev/null
    sudo chmod a+x /usr/sbin/policy-rc.d
  fi

  for i in "${repo[@]}"; do
    echo "deb $i" | sudo tee -a /etc/apt/sources.list.d/spinnaker.list > /dev/null
  done

  sudo apt-get update
  if [[ "$upgrade" == "true" ]]; then
    sudo unattended-upgrade -v
  fi

  # Enforce the package installation order.
  for package in $packages $artifact_packages; do
    sudo DEBIAN_FRONTEND=noninteractive apt-get install --force-yes -y $package;
  done

  # https://www.packer.io/docs/builders/amazon-chroot.html look at gotchas at the end.
  if [[ "$disable_services" == "true" ]]; then
    echo "removing /usr/sbin/policy-rc.d"
    sudo rm -f /usr/sbin/policy-rc.d
  fi

  if [[ -e /etc/apt/sources.list.d/spinnaker.list ]]; then
    # Cleanup repository configuration
    sudo rm /etc/apt/sources.list.d/spinnaker.list
  fi
}

function provision_rpm() {
  for i in "${!repo[@]}"; do
    ts=$(date +%s)
    cat > /tmp/spinnaker-$i.repo <<EOF
[spinnaker-$ts-$i]
name=spinnaker-$ts-$i
baseurl=${repo[$i]}
gpgcheck=0
enabled=1
EOF
  done
  if ls /tmp/spinnaker*.repo; then
    sudo mv /tmp/spinnaker*.repo /etc/yum.repos.d/
  fi

  if [[ "$upgrade" == "true" ]]; then
    sudo yum -y update
  fi

  # Enforce the package installation order.
  for package in $packages $artifact_packages; do sudo yum -y install $package; done

  if [[ "$repository" != "" ]]; then
    # Cleanup repository configuration
    sudo rm /etc/yum.repos.d/spinnaker*.repo
  fi
}

function main() {
  get_artifact_references
  if [[ "$package_type" == "deb" ]]; then
    provision_deb
  elif [[ "$package_type" == "rpm" ]]; then
    provision_rpm
  fi
}

main
