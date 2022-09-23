#!/usr/bin/env bash

## auto-generated debian install file written by halyard
## which is executed when running 'hal deploy apply'.

set -e
set -o pipefail

# install redis as a local service
INSTALL_REDIS="{%install-redis%}"

# install first-time spinnaker dependencies (setup apt repos)
PREPARE_ENVIRONMENT="{%prepare-environment%}"

REPOSITORY_URL="{%debian-repository%}"

echo_err() {
  echo "$@" 1>&2
}

## check that the user is root
if [[ `/usr/bin/id -u` -ne 0 ]]; then
  echo_err "$0 must be executed with root permissions; exiting"
  exit 1
fi

if [[ -f /etc/lsb-release ]]; then
  # This file is Ubuntu specific
  . /etc/lsb-release
  DISTRO=$DISTRIB_ID
elif [[ -f /etc/debian_version ]]; then
  DISTRO=Debian
elif [[ -f /etc/redhat-release ]]; then
  if grep -iq cent /etc/redhat-release; then
    DISTRO="CentOS"
  elif grep -iq red /etc/redhat-release; then
    DISTRO="RedHat"
  fi
else
  DISTRO=$(uname -s)
fi

if [ "$DISTRO" = "Ubuntu" ]; then
  if [ "${DISTRIB_RELEASE%%.*}" -lt "18" ]; then
    echo_err "Not a supported version of Ubuntu"
    echo_err "Version is $DISTRIB_RELEASE we require 18.04 or higher."
    exit 1
  fi
elif [ "$DISTRO" = "Debian" ]; then
  DISTRO_VERSION=$(lsb_release -rs)
  if [ ${DISTRO_VERSION} -lt "10" ]; then
    echo_err "Not a supported version of Debian"
    echo_err "Version is ${DISTRO_VERSION} we require 10 or higher."
    exit 1
  fi
else
  echo_err "Not a supported operating system: " $DISTRO
  echo_err "It's recommended you use either Ubuntu 18.04 or higher"
  echo_err "or Debian 10 or higher."
  echo_err ""
  echo_err "Please file an issue against https://github.com/spinnaker/spinnaker/issues"
  echo_err "if you'd like to see support for your OS and version"
  exit 1
fi

function add_spinnaker_apt_repository() {
  # Most probably not required since the repo would already
  # need to exist in order to install the spinnaker-halyard
  # package in the first place.
  if [ ! -f /etc/apt/sources.list.d/spinnaker.list ]; then
    echo "Adding Spinnaker apt repository"
    REPOSITORY_HOST=$(echo $REPOSITORY_URL | cut -d/ -f3)
    curl -fsSL https://us-apt.pkg.dev/doc/repo-signing-key.gpg | gpg --dearmor | tee /usr/share/keyrings/spinnaker.gpg > /dev/null
    echo "deb [signed-by=/usr/share/keyrings/spinnaker.gpg arch=all] $REPOSITORY_URL apt main" | tee /etc/apt/sources.list.d/spinnaker.list > /dev/null
  fi
}

echo "Updating apt package lists..."

if [ -n "$PREPARE_ENVIRONMENT" ]; then
  add_spinnaker_apt_repository
  {%upstart-init%}
fi

apt-get update ||:

echo "Installing desired components..."

if [ -z "$(getent group spinnaker)" ]; then
  groupadd spinnaker
fi

if [ -z "$(getent passwd spinnaker)" ]; then
  if [ "$homebase" = "" ]; then
    homebase="/home"
    echo "Setting spinnaker home to $homebase"
  fi

  useradd --gid spinnaker -m --home-dir $homebase/spinnaker spinnaker
fi

mkdir -p /opt/spinnaker/config
chown spinnaker /opt/spinnaker/config

mkdir -p /opt/spinnaker-monitoring/config
chown spinnaker /opt/spinnaker-monitoring/config

mkdir -p /opt/spinnaker-monitoring/registry
chown spinnaker /opt/spinnaker-monitoring/registry

{%install-commands%}

# so this script can be used for updates
set +e
systemctl {%service-action%} spinnaker

# Ensure apache is started for deck. Restart to ensure enabled site is loaded.
systemctl restart apache2

# Ensure that Halyard and Spinnaker start up automatically on reboot
systemctl daemon-reload
systemctl enable spinnaker
systemctl enable halyard
