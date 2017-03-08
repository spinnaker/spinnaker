#!/bin/bash

# This script installs Halyard.
# See http://www.spinnaker.io/docs/creating-a-spinnaker-instance


set -e
set -o pipefail

REPOSITORY_URL="https://dl.bintray.com/spinnaker-team/spinnakerbuild"

# We can only currently support limited releases
# First guess what sort of operating system

if [ -f /etc/lsb-release ]; then
  . /etc/lsb-release
  DISTRO=$DISTRIB_ID
elif [ -f /etc/debian_version ]; then
  DISTRO=Debian
  # XXX or Ubuntu
elif [ -f /etc/redhat-release ]; then
  if grep -iq cent /etc/redhat-release; then
    DISTRO="CentOS"
  elif grep -iq red /etc/redhat-release; then
    DISTRO="RedHat"
  fi
else
  DISTRO=$(uname -s)
fi

# If not Ubuntu 14.xx.x or higher

if [ "$DISTRO" = "Ubuntu" ]; then
  if [ "${DISTRIB_RELEASE%%.*}" -lt "14" ]; then
    echo "Not a supported version of Ubuntu"
    echo "Version is $DISTRIB_RELEASE we require 14.04 or higher"
    exit 1
  fi
else
  echo "Not a supported operating system: "
  echo "It's recommended you use Ubuntu 14.04 or higher"
  echo ""
  echo "Please file an issue against github.com/spinnaker/spinnaker/issues "
  echo "if you'd like to see support for your OS and version"
  exit 1
fi


function print_usage() {
  cat <<EOF
usage: $0 [--quiet] [--dependencies_only]
    [--repository <debian repository url>]
    [--local-install] [--home_dir <path>]

    --quiet                     Sets cloud provider to "none". You will need to
                                edit /etc/default/spinnaker manually
                                cannot be used with --cloud_provider.

    --repository <url>          Obtain Spinnaker packages from the <url>
                                rather than the default repository, which is
                                $REPOSITORY_URL

    --dependencies_only         Do not install any Spinnaker services.
                                Only install the dependencies. This is intended
                                for development scenarios only.

    --local-install             For Spinnaker and Java packages, download
                                packages and install using dpkg instead of
                                apt. Use this option only if you are having
                                issues with the bintray repositories.
                                If you use this option you must manually
                                install openjdk-8-jdk.

    --home_dir                  Override where user home directories reside
                                example: /export/home vs /home
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
      --dependencies_only)
        echo "deps"
        DEPENDENCIES_ONLY=true
        ;;
      --local-install)
        echo "local"
        DOWNLOAD=true
        ;;
      --quiet|-q)
        QUIET=true
        ;;
      --home_dir)
        homebase="$1"
        if [ "$(basename $homebase)" = "spinnaker" ]; then
          echo "stripping trailing 'spinnaker' from --home_dir=$homebase"
          homebase=$(dirname $homebase)
        fi
        shift
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
  # Spinnaker
  # DL Repo goes here
  REPOSITORY_HOST=$(echo $REPOSITORY_URL | cut -d/ -f3)
  if [ "$REPOSITORY_HOST" = "dl.bintray.com" ]; then
    REPOSITORY_ORG=$(echo $REPOSITORY_URL | cut -d/ -f4)
    # Personal repositories might not be signed, so conditionally check.
    gpg=""
    gpg=$(curl -s -f "https://bintray.com/user/downloadSubjectPublicKey?username=$REPOSITORY_ORG") || true
    if [ -n "$gpg" ]; then
      echo "$gpg" | apt-key add -
    fi
  fi
  echo "deb $REPOSITORY_URL $DISTRIB_CODENAME spinnaker" | tee /etc/apt/sources.list.d/spinnaker.list > /dev/null
  # Java 8
  # https://launchpad.net/~openjdk-r/+archive/ubuntu/ppa
  add-apt-repository -y ppa:openjdk-r/ppa
  apt-get update ||:
}

function install_java() {
  if [ -z "$DOWNLOAD" ]; then
    apt-get install -y --force-yes openjdk-8-jdk

    # https://bugs.launchpad.net/ubuntu/+source/ca-certificates-java/+bug/983302
    # It seems a circular dependency was introduced on 2016-04-22 with an openjdk-8 release, where
    # the JRE relies on the ca-certificates-java package, which itself relies on the JRE. D'oh!
    # This causes the /etc/ssl/certs/java/cacerts file to never be generated, causing a startup
    # failure in Clouddriver.
    dpkg --purge --force-depends ca-certificates-java
    apt-get install ca-certificates-java
  elif [[ "x`java -version 2>&1|head -1`" != *"1.8.0"* ]]; then
    echo "you must manually install java 8 and then rerun this script; exiting"
    exit 13
  fi
}

function install_halyard() {
  package="spinnaker-halyard"
  apt-get install -y --force-yes --allow-unauthenticated $package
  local apt_status=$?
  if [ $apt_status -ne 0 ]; then
    if [ -n "$DOWNLOAD" ] && [ "$apt_status" -eq "100" ]; then
      echo "$(tput bold)Downloading packages for installation by dpkg...$(tput sgr0)"
      latest=`curl $REPOSITORY_URL/dists/$DISTRIB_CODENAME/spinnaker/binary-amd64/Packages | grep "^Filename" | grep $package | awk '{print $2}' | awk -F'/' '{print $NF}' | sort -t. -k 1,1n -k 2,2n -k 3,3n | tail -1`
      debfile=`echo $latest | awk -F "/" '{print $NF}'`
      filelocation=`curl $REPOSITORY_URL/dists/$DISTRIB_CODENAME/spinnaker/binary-amd64/Packages | grep "^Filename" | grep $latest | awk '{print $2}'`
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
  read -p "Would you like to configure halyard to use bash auto-completion? [default=Y]: " yes

  completion_script="/etc/bash_completion.d/hal"
  if [ "$yes" = "y" ] || [ "$yes = "Y" ] || [ "$yes = "yes" ] || [ "$yes" = "" ]; then
    local bashrc
    hal --print-bash-completion | tee $completion_script  > /dev/null
    read -p "Where is your bash RC? [default=$HOME/.bashrc]: " bashrc
    
    if [ -z "$bashrc" ]; then
      bashrc="$HOME/.bashrc"
    fi
    
    if [ -z "$(grep $completion_script $bashrc)" ]; then
      echo "# configure hal auto-complete " >> $bashrc
      echo ". /etc/bash_completion.d/hal" >> $bashrc
    fi

    echo "Bash auto-completion configured."
    echo "$(tput bold)To use the auto-completion, either restart your shell, or run$(tput sgr0)"
    echo "$(tput bold). $bashrc$(tput sgr0)"
  fi
  
}

function configure_halyard_defaults() {
  local halconfig
  echo ""
  read -p "Where would you like to store your halconfig? [default=$HOME/.hal]: " halconfig

  if [ -z "$halconfig" ]; then
    halconfig="$HOME/.hal"
  fi

  mkdir -p $halconfig
  chown spinnaker $halconfig

  mkdir -p /opt/spinnaker/config
  chown spinnaker /opt/spinnaker/config

  cat > /opt/spinnaker/config/halyard.yml <<EOL
spinnaker:
  config:
    output:
      directory: ~/.spinnaker

halyard:
  halconfig:
    directory: $halconfig
EOL

  chown spinnaker /opt/spinnaker/config/halyard.yml
}

process_args "$@"

# Only add external apt repositories if we are not --local_install
if [ -n "$DOWNLOAD"]; then
  echo "$(tput bold)Configuring external apt repos...$(tput sgr0)"
  add_apt_repositories
fi

TEMPDIR=$(mktemp -d installhalyard.XXXX)

echo "$(tput bold)Installing Java 8...$(tput sgr0)"

install_java

if [ -n "$DEPENDENCIES_ONLY" ]; then
  exit 0
fi

## Spinnaker
echo "$(tput bold)Installing Halyard...$(tput sgr0)"
install_halyard

## Remove

if [ "$homebase" = "" ]; then
  homebase="/home"
  echo "Setting spinnaker home to $homebase"
fi

if [ -z "$(getent group spinnaker)" ]; then
  groupadd spinnaker
fi

if [ -z "$(getent passwd spinnaker)" ]; then
  useradd --gid spinnaker -m --home-dir $homebase/spinnaker spinnaker
fi

configure_halyard_defaults
configure_bash_completion

rm -rf $TEMPDIR

start halyard

if [ -z "$QUIET" ]; then
cat <<EOF

Halyard is now installed and running. To interact with it, use:

$ hal --help

More information can be found here:
https://github.com/spinnaker/halyard/blob/master/README.md

EOF
fi
