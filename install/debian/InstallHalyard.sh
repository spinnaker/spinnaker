#!/usr/bin/env bash

set -e

function check_migration_needed() {
  set -e

  which dpkg &> /dev/null
  if [ "$?" = "0" ]; then
    dpkg -s spinnaker-halyard &> /dev/null

    if [ "$?" != "1" ]; then
      >&2 echo "Attempting to install halyard while a debian installation is present."
      >&2 echo "Please visit: http://spinnaker.io/setup/install/halyard_migration"
      exit 1
    fi
  fi
  set -e
}

function process_args() {
  while [ "$#" -gt "0" ]
  do
    local key="$1"
    shift
    case $key in
      --user)
        echo "user"
        HAL_USER="$1"
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
        exit -1
    esac
  done
}

function get_user() {
  local user 

  user=$(who -m | awk '{print $1;}')
  if [ -z "$YES" ]; then
    if [ "$user" = "root" ] || [ -z "$user" ]; then
      read -p "Please supply a non-root user to run Halyard as: " user
    fi
  fi

  echo $user
}

function configure_defaults() {
  if [ -z "$HAL_USER" ]; then
    HAL_USER=$(get_user)
  fi

  if [ -z "$HAL_USER" ]; then
    >&2 echo "You have not supplied a user to run Halyard as."
    exit 1
  fi

  if [ "$HAL_USER" = "root" ]; then
    >&2 echo "Halyard may not be run as root. Supply a user to run Halyard as: "
    >&2 echo "  sudo bash $0 --user <user>"
    exit 1
  fi

  set +e
  getent passwd $HAL_USER &> /dev/null

  if [ "$?" != "0" ]; then
    >&2 echo "Supplied user $HAL_USER does not exist"
    exit 1
  fi
  set -e

  if [ -z "$HALYARD_VERSION" ]; then
    HALYARD_VERSION="stable"
  fi

  echo "$(tput bold)Halyard version will be $HALYARD_VERSION $(tput sgr0)"

  home=$(getent passwd $HAL_USER | cut -d: -f6)
  local halconfig_dir="$home/.hal"

  echo "$(tput bold)Halconfig will be stored at $halconfig_dir/config$(tput sgr0)"

  mkdir -p $halconfig_dir
  chown $HAL_USER $halconfig_dir

  mkdir -p /opt/spinnaker/config
  chmod +rx /opt/spinnaker/config

  cat > /opt/spinnaker/config/halyard.yml <<EOL
halyard:
  halconfig:
    directory: $halconfig_dir

spinnaker:
  artifacts:
    debianRepository: $SPINNAKER_REPOSITORY_URL
    dockerRegistry: $SPINNAKER_DOCKER_REGISTRY
    googleImageProject: $SPINNAKER_GCE_PROJECT
EOL

  echo $HAL_USER > /opt/spinnaker/config/halyard-user

  cat > $halconfig_dir/uninstall.sh <<EOL
#!/usr/bin/env bash

if [[ \`/usr/bin/id -u\` -ne 0 ]]; then
  echo "$0 must be executed with root permissions; exiting"
  exit 1
fi

read -p "This script uninstalls Halyard and deletes all of its artifacts, are you sure you want to continue? (Y/n): " yes

if [ "\$yes" != "y" ] && [ "\$yes" != "Y" ]; then
  echo "Aborted"
  exit 0
fi

rm /opt/halyard -rf
rm /var/log/spinnaker/halyard -rf

echo "Deleting halconfig and artifacts"
rm /opt/spinnaker/config/halyard* -rf
rm $halconfig_dir -rf
EOL

  chmod +x $halconfig_dir/uninstall.sh
  echo "$(tput bold)Uninstall script is located at $halconfig_dir/uninstall.sh$(tput sgr0)"
}


function print_usage() {
  cat <<EOF
usage: $0 [-y] [--version=<version>] [--user=<user>]
    -y                              Accept all default options during install
                                    (non-interactive mode).

    --version <version>             Specify the exact verison of Halyard to
                                    install.

    --user <user>                   Specify the user to run Halyard as. This
                                    user must exist.
EOF
}

function install_java() {
  set +e
  local java_version=$(java -version 2>&1 head -1)
  set -e

  if [[ "$java_version" == *1.8* ]]; then
    echo "Java is already installed & at the right version"
    return 0;
  fi

  if [ ! -f /etc/os-release ]; then
    >&2 "Unable to determine OS platform (no /etc/os-release file)"
    exit 1
  fi

  source /etc/os-release

  if [ "$ID" = "ubuntu" ]; then
    echo "Running ubuntu $VERSION_ID"
    # Java 8
    # https://launchpad.net/~openjdk-r/+archive/ubuntu/ppa
    add-apt-repository -y ppa:openjdk-r/ppa
    apt-get update ||:

    apt-get install -y --force-yes openjdk-8-jre

    # https://bugs.launchpad.net/ubuntu/+source/ca-certificates-java/+bug/983302
    # It seems a circular dependency was introduced on 2016-04-22 with an openjdk-8 release, where
    # the JRE relies on the ca-certificates-java package, which itself relies on the JRE. D'oh!
    # This causes the /etc/ssl/certs/java/cacerts file to never be generated, causing a startup
    # failure in Clouddriver.
    dpkg --purge --force-depends ca-certificates-java
    apt-get install ca-certificates-java
  elif [ "$ID" = "debian" ] && [ "$VERSION_ID" = "8" ]; then
    echo "Running debian 8 (jessie)"
    apt install -t jessie-backports openjdk-8-jre-headless ca-certificates-java
  elif [ "$ID" = "debian" ] && [ "$VERSION_ID" = "9" ]; then
    echo "Running debian 9 (stretch)"
    apt install -t stretch-backports openjdk-8-jre-headless ca-certificates-java
  else
    >&2 echo "Distribution $PRETTY_NAME is not supported yet - please file an issue"
    >&2 echo "  https://github.com/spinnaker/halyard/issues"
    exit 1
  fi
}

function install_halyard() {
  TEMPDIR=$(mktemp -d installhalyard.XXXX)
  pushd $TEMPDIR

  curl -O https://storage.googleapis.com/spinnaker-artifacts/halyard/$HALYARD_VERSION/debian/halyard.tar.gz
  tar -xvf halyard.tar.gz -C /opt

  groupadd halyard || true
  groupadd spinnaker || true
  usermod -G halyard -a $HAL_USER || true
  usermod -G spinnaker -a $HAL_USER || true
  chown $HAL_USER:halyard /opt/halyard

  mv /opt/hal /usr/local/bin
  chmod +x /usr/local/bin/hal

  if [ -f /opt/update-halyard ]; then
    mv /opt/update-halyard /usr/local/bin
    chmod +x /usr/local/bin/update-halyard
  else 
    echo "No update script supplied with installer..."
  fi

  mkdir -p /var/log/spinnaker/halyard
  chown $HAL_USER /var/log/spinnaker/halyard
  chmod 755 /var/log/spinnaker/halyard

  popd
  rm -rf $TEMPDIR
}

check_migration_needed

process_args $@
configure_defaults

install_java
install_halyard

su -c "hal -v" -s /bin/bash $HAL_USER
