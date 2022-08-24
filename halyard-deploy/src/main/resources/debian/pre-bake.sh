#!/usr/bin/env bash

## auto-generated debian install file written by halyard

set -e
set -o pipefail

REPOSITORY_URL="{%debian-repository%}"

## check that the user is root
if [[ `/usr/bin/id -u` -ne 0 ]]; then
  echo "$0 must be executed with root permissions; exiting"
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
    echo "Not a supported version of Ubuntu"
    echo "Version is $DISTRIB_RELEASE we require 18.04 or higher."
    exit 1
  fi
elif [ "$DISTRO" = "Debian" ]; then
  DISTRO_VERSION=$(lsb_release -rs)
  if [ ${DISTRO_VERSION} -lt "10" ]; then
    echo "Not a supported version of Debian"
    echo "Version is ${DISTRO_VERSION} we require 10 or higher."
    exit 1
  fi
else
  echo "Not a supported operating system: " $DISTRO
  echo "It's recommended you use either Ubuntu 18.04 or higher"
  echo "or Debian 10 or higher."
  echo ""
  echo "Please file an issue against https://github.com/spinnaker/spinnaker/issues"
  echo "if you'd like to see support for your OS and version"
  exit 1
fi

function add_spinnaker_apt_repository() {
  if [ ! -f /etc/apt/sources.list.d/spinnaker.list ]; then
    echo "Adding Spinnaker apt repository"
    REPOSITORY_HOST=$(echo $REPOSITORY_URL | cut -d/ -f3)
    curl -fsSL https://us-apt.pkg.dev/doc/repo-signing-key.gpg | gpg --dearmor | sudo tee /usr/share/keyrings/spinnaker.gpg > /dev/null
    echo "deb [signed-by=/usr/share/keyrings/spinnaker.gpg arch=all] $REPOSITORY_URL apt main" | tee /etc/apt/sources.list.d/spinnaker.list > /dev/null
  fi
}

echo "Updating apt package lists..."

add_spinnaker_apt_repository
{%upstart-init%}

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

mkdir -p $(dirname {%startup-file%})

cat > {%startup-file%} <<EOL
#!/usr/bin/env bash

{%startup-command%}
EOL

chmod +x {%startup-file%}

{%install-commands%}
