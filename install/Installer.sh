#!/usr/bin/env bash

# This script installs Halyard.

set -e
set -o pipefail

REPOSITORY_URL="https://us-apt.pkg.dev/projects/spinnaker-community"
SPINNAKER_REPOSITORY_URL="https://us-apt.pkg.dev/projects/spinnaker-community"
SPINNAKER_DOCKER_REGISTRY="us-docker.pkg.dev/spinnaker-community/docker"
SPINNAKER_GCE_PROJECT="marketplace-spinnaker-release"
CONFIG_BUCKET="halconfig"

VERSION=""
HALYARD_STARTUP_TIMEOUT_SECONDS=120
HAL_USER="spinnaker"

if [ -z "$RELEASE_TRACK" ]; then
  >&2 echo "RELEASE_TRACK env var must be set (nightly or stable)"
  >&2 echo "Typically this script is invoked from a wrapper, e.g. "
  >&2 echo "   https://raw.githubusercontent.com/spinnaker/halyard/master/install/stable/InstallHalyard.sh"
  exit 1
fi

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


function print_usage() {
  cat <<EOF
usage: $0 [-y] [--quiet] [--dependencies_only]
    [--repository <debian repository url>]
    [--local-install]
    -y                              Accept all default options during install
                                    (non-interactive mode).

    --repository <url>              Obtain Halyard debian from <url>
                                    rather than the default repository, which is
                                    $REPOSITORY_URL.

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

    --dependencies_only             Do not install any Spinnaker services.
                                    Only install the dependencies. This is
                                    intended for development scenarios only.

    --local-install                 For Spinnaker and Java packages, download
                                    packages and install using dpkg instead of
                                    apt. Use this option only if you are having
                                    issues with the repositories.
                                    If you use this option you must manually
                                    install openjdk-11-jre-headless.
EOF
}

function echo_status() {
  if [ -n "$QUIET" ]; then
    echo "$@"
  fi
}

function process_args() {
  while [ "$#" -gt "0" ]
  do
    local key="$1"
    shift
    case $key in
      --repository)
        echo "repo"
        REPOSITORY_URL="$1"
        shift
        ;;
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
        VERSION="$1"
        shift
        ;;
      --dependencies_only)
        echo "deps"
        DEPENDENCIES_ONLY=true
        ;;
      -y)
        echo "non-interactive"
        YES=true
        ;;
      --local-install)
        echo "local"
        DOWNLOAD=true
        ;;
      --quiet|-q)
        QUIET=true
        ;;
      --help|-help|-h)
        print_usage
        exit 13
        ;;
      *)
        echo "ERROR: Unknown argument '$key'"
        exit -1
    esac
  done
}

function add_apt_repositories() {
  if [ ! -f /etc/apt/sources.list.d/spinnaker.list ]; then
    REPOSITORY_HOST=$(echo $REPOSITORY_URL | cut -d/ -f3)
    curl -fsSL https://us-apt.pkg.dev/doc/repo-signing-key.gpg | gpg --dearmor | sudo tee /usr/share/keyrings/spinnaker.gpg > /dev/null
    echo "deb [signed-by=/usr/share/keyrings/spinnaker.gpg arch=all] $REPOSITORY_URL apt main" | tee /etc/apt/sources.list.d/spinnaker.list > /dev/null
  fi
  apt-get update ||:
}

function install_java() {
  if [ -z "$DOWNLOAD" ]; then
    echo "$(tput bold)Installing Java...$(tput sgr0)"
    apt-get install -y --allow-unauthenticated --allow-downgrades --allow-remove-essential --allow-change-held-packages openjdk-11-jre-headless
  elif [[ "x`java -version 2>&1|head -1`" != *"11.0"* ]]; then
    echo "You must manually install Java 11 and then rerun this script; exiting"
    exit 13
  fi
}

function install_halyard() {
  local package installed_package
  package="spinnaker-halyard"
  installed_package=$package
  if [ -n "$VERSION" ]; then
    installed_package="$package=$VERSION"
  fi
  apt-get install -y --allow-unauthenticated --allow-downgrades --allow-remove-essential --allow-change-held-packages $installed_package
  local apt_status=$?
  if [ $apt_status -ne 0 ]; then
    if [ -n "$DOWNLOAD" ] && [ "$apt_status" -eq "100" ]; then
      local debfile version
      echo "$(tput bold)Downloading packages for installation by dpkg...$(tput sgr0)"
      if [ -n "$VERSION" ]; then
        version="${package}_${VERSION}_all.deb"
        debfile=$version
      else 
        version=`curl -s $REPOSITORY_URL/dists/apt/main/binary-all/Packages | grep "^Filename" | grep $package | awk '{print $2}' | awk -F'/' '{print $NF}' | sort -t. -k 1,1n -k 2,2n -k 3,3n | tail -1`
        debfile=`echo $version | awk -F "/" '{print $NF}'`
      fi
      filelocation=`curl -s $REPOSITORY_URL/dists/apt/main/binary-all/Packages | grep "^Filename" | grep $version | awk '{print $2}'`
      curl -L -o /tmp/$debfile $REPOSITORY_URL/$filelocation
      dpkg -i /tmp/$debfile && rm -f /tmp/$debfile
    else
      echo "Error installing halyard."
      echo "cannot continue installation; exiting."
      exit 13
    fi
  fi
}

function configure_bash_completion() {
  local yes
  echo ""
  if [ -z "$YES" ]; then
    read -p "Would you like to configure halyard to use bash auto-completion? [default=Y]: " yes
  else
    yes="y"
  fi

  completion_script="/etc/bash_completion.d/hal"
  if [ "$yes" = "y" ] || [ "$yes = "Y" ] || [ "$yes = "yes" ] || [ "$yes" = "" ]; then
    local bashrc
    hal --print-bash-completion | tee $completion_script  > /dev/null
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

function configure_halyard_defaults() {
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

read -p "This script uninstalls Halyard and deletes all of its artifacts, are you sure you want to continue? (y/N): " yes

if [ "\$yes" != "y" ] && [ "\$yes" != "Y" ]; then
  echo "Aborted"
  exit 0
fi

apt-get purge -y spinnaker-halyard

echo "Deleting halconfig and artifacts"
rm /opt/spinnaker/config/halyard* -rf
rm /home/spinnaker/.hal -rf
EOL

  mv /tmp/uninstall-halyard.sh /usr/local/bin/uninstall-halyard.sh
  chmod +x /usr/local/bin/uninstall-halyard.sh
  echo "$(tput bold)Uninstall script is located at /usr/local/bin/uninstall-halyard.sh$(tput sgr0)"
}

process_args "$@"
configure_halyard_defaults

echo "$(tput bold)Configuring external apt repos...$(tput sgr0)"
add_apt_repositories
install_java

if [ -n "$DEPENDENCIES_ONLY" ]; then
  exit 0
fi

## Spinnaker
echo "$(tput bold)Installing Halyard...$(tput sgr0)"
install_halyard
configure_bash_completion

printf 'Waiting for the Halyard daemon to start running'

WAIT_START=$(date +%s)

set +e 
hal --ready &> /dev/null

while [ "$?" != "0" ]; do
  WAIT_NOW=$(date +%s)
  WAIT_TIME=$(( $WAIT_NOW - $WAIT_START ))

  if [ "$WAIT_TIME" -gt "$HALYARD_STARTUP_TIMEOUT_SECONDS" ]; then
    >&2 echo ""
    >&2 echo "Waiting for halyard to start timed out after $WAIT_TIME seconds"
    exit 1
  fi

  printf '.'
  sleep 2
  hal --ready &> /dev/null
done

echo 

if [ -z "$QUIET" ]; then
cat <<EOF

Halyard is now installed and running. To interact with it, use:

$ hal --help

More information can be found here:
https://github.com/spinnaker/halyard/blob/master/README.md

EOF
fi
