#!/usr/bin/env bash

set -e

SPINNAKER_REPOSITORY_URL="https://us-apt.pkg.dev/projects/spinnaker-community"
SPINNAKER_DOCKER_REGISTRY="us-docker.pkg.dev/spinnaker-community/docker"
SPINNAKER_GCE_PROJECT="marketplace-spinnaker-release"
CONFIG_BUCKET="halconfig"

# We can only currently support limited releases
# First guess what sort of operating system
if [ -f /etc/lsb-release ]; then
  # This file is Ubuntu specific
  . /etc/lsb-release
  DISTRO=$DISTRIB_ID
elif [ -f /etc/debian_version ]; then
  DISTRO=Debian
elif [ -f /etc/redhat-release ]; then
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
  echo "Not a supported operating system: "
  echo "It's recommended you use either Ubuntu 18.04 or higher"
  echo "or Debian 10 or higher."
  echo ""
  echo "Please file an issue against https://github.com/spinnaker/spinnaker/issues"
  echo "if you'd like to see support for your OS and version"
  exit 1
fi


function process_args() {
  while [ "$#" -gt "0" ]
  do
    local key="$1"
    shift
    case $key in
      --spinnaker-repository)
        echo "spinnaker-repo"
        SPINNAKER_REPOSITORY_URL="$1"
        shift
        ;;
      --spinnaker-registry)
        echo "spinnaker-registry"
        SPINNAKER_DOCKER_REGISTRY="$1"
        shift
        ;;
      --spinnaker-gce-project)
        echo "spinnaker-gce-project"
        SPINNAKER_GCE_PROJECT="$1"
        shift
        ;;
      --config-bucket)
        echo "config-bucket"
        CONFIG_BUCKET="$1"
        shift
        ;;
      --version)
        echo "version"
        HALYARD_VERSION="$1"
        shift
        ;;
      -y)
        echo "non-interactive"
        YES=true
        ;;
      --help|-help|-h)
        print_usage
        exit 13
        ;;
      *)
        echo "ERROR: Unknown argument '$key'"
        exit 1
    esac
  done
}

function configure_defaults() {
  if [ -z "$HALYARD_VERSION" ]; then
    HALYARD_VERSION=`curl -s $SPINNAKER_REPOSITORY_URL/dists/apt/main/binary-all/Packages \
      | grep "^Filename" \
      | grep spinnaker-halyard \
      | awk '{print $2}' \
      | awk -F'/' '{print $NF}' \
      | sort -t. -k 1,1n -k 2,2n -k 3,3n \
      | tail -1 \
      | cut -d '_' -f 2`
  fi

  echo "$(tput bold)Halyard version will be $HALYARD_VERSION $(tput sgr0)"
  echo "$(tput bold)Halyard will be downloaded from the spinnaker-community repository $(tput sgr0)"
  echo "$(tput bold)Halconfig will be stored at /home/spinnaker/.hal/config$(tput sgr0)"

  mkdir -p /opt/spinnaker/config
  chmod +rx /opt/spinnaker/config

  cat > /opt/spinnaker/config/halyard.yml <<EOL
halyard:
  halconfig:
    directory: /home/spinnaker/.hal

spinnaker:
  artifacts:
    debianRepository: $SPINNAKER_REPOSITORY_URL
    dockerRegistry: $SPINNAKER_DOCKER_REGISTRY
    googleImageProject: $SPINNAKER_GCE_PROJECT
  config:
    input:
      bucket: $CONFIG_BUCKET
EOL

  cat > /tmp/uninstall-halyard.sh <<EOL
#!/usr/bin/env bash

if [[ \`/usr/bin/id -u\` -ne 0 ]]; then
  echo "uninstall-halyard.sh  must be executed with root permissions; exiting"
  exit 1
fi

read -p "This script uninstalls Halyard and deletes all of its artifacts, are you sure you want to continue? (Y/n): " yes

if [ "\$yes" != "y" ] && [ "\$yes" != "Y" ]; then
  echo "Aborted"
  exit 0
fi

apt purge -y spinnaker-halyard

rm /opt/halyard -rf
rm /var/log/spinnaker/halyard -rf
rm -f /usr/local/bin/hal /usr/local/bin/update-halyard

echo "Deleting halconfig and artifacts"
rm /opt/spinnaker/config/halyard* -rf
rm /home/spinnaker/.hal -rf
EOL

  mv /tmp/uninstall-halyard.sh /usr/local/bin/uninstall-halyard.sh
  chmod a+rx /usr/local/bin/uninstall-halyard.sh
  echo "$(tput bold)Uninstall script is located at /usr/local/bin/uninstall-halyard.sh$(tput sgr0)"
}


function print_usage() {
  cat <<EOF
usage: $0 [-y] [--version=<version>]
    -y                              Accept all default options during install
                                    (non-interactive mode).

    --halyard-bucket-base-url <name>   The bucket the Halyard JAR to be installed
                                       is stored in.

    --download-with-gsutil          If specifying a GCS bucket using
                                    --halyard-bucket-base-url, this flag causes the 
                                    install script to rely on gsutil and its 
                                    authentication to fetch the Halyard JAR.

    --config-bucket <name>          The bucket the your Bill of Materials and
                                    base profiles are stored in.

    --spinnaker-repository <url>    Obtain Spinnaker artifact debians from <url>
                                    rather than the default repository, which is
                                    $SPINNAKER_REPOSITORY_URL.

    --spinnaker-registry <url>      Obtain Spinnaker docker images from <url>
                                    rather than the default registry, which is
                                    $SPINNAKER_DOCKER_REGISTRY.

    --spinnaker-gce-project <name>  Obtain Spinnaker GCE images from <url>
                                    rather than the default project, which is
                                    $SPINNAKER_GCE_PROJECT.

    --version <version>             Specify the exact version of Halyard to
                                    install.
EOF
}

function check_java() {
  if ! which java 2>&1 > /dev/null; then
    echo "$(tput bold)Installing Java...$(tput sgr0)"
    apt-get update
    apt-get install -y openjdk-11-jre-headless
  fi
}

function configure_bash_completion() {
  echo ""
  if [ -z "$YES" ]; then
    read -p "Would you like to configure halyard to use bash auto-completion? [default=Y]: " yes
  else
    yes="y"
  fi

  if [ "$yes" = "y" ] || [ "$yes = "Y" ] || [ "$yes = "yes" ] || [ "$yes" = "" ]; then
    completion_script="/etc/bash_completion.d/hal"

    mkdir -p $(dirname $completion_script)
    hal --print-bash-completion | tee $completion_script  > /dev/null

    local bashrc
    if [ -z "$YES" ]; then
      echo ""
      read -p "Where is your bash RC? [default=$HOME/.bashrc]: " bashrc
    fi

    if [ -z "$bashrc" ]; then
      bashrc="$HOME/.bashrc"
    fi

    if [ -z "$(grep $completion_script $bashrc)" ]; then
      echo "# configure hal auto-complete " >> $bashrc
      echo ". /etc/bash_completion.d/hal" >> $bashrc
    fi

    echo "Bash auto-completion configured."
    echo "$(tput bold)To use the auto-completion either restart your shell, or run$(tput sgr0)"
    echo "$(tput bold). $bashrc$(tput sgr0)"
  fi
}

function install_halyard() {
  curl -fsSL https://us-apt.pkg.dev/doc/repo-signing-key.gpg \
    | gpg --dearmor \
    | tee /usr/share/keyrings/spinnaker.gpg > /dev/null
  echo "deb [signed-by=/usr/share/keyrings/spinnaker.gpg arch=all] ${SPINNAKER_REPOSITORY_URL} apt main" \
    | tee /etc/apt/sources.list.d/spinnaker-community.list > /dev/null
  apt update
  apt install -y spinnaker-halyard

  if [ -f /opt/update-halyard ]; then
    mv /opt/update-halyard /usr/local/bin
    chmod a+rx /usr/local/bin/update-halyard
  else
    echo "No update script supplied with installer..."
  fi
}

process_args $@
configure_defaults
check_java
install_halyard
configure_bash_completion

HALYARD_INSTALLED_VERSION=$(hal -v)
echo "$(tput bold)Halyard version: ${HALYARD_INSTALLED_VERSION}$(tput sgr0)"
