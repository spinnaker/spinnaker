#!/usr/bin/env bash

set -e

function check_migration_needed() {
  set +e

  which dpkg &> /dev/null
  if [ "$?" = "0" ]; then
    dpkg -s spinnaker-halyard &> /dev/null

    if [ "$?" != "1" ]; then
      >&2 echo "Attempting to install halyard while a debian installation is present."
      >&2 echo "Please visit: https://spinnaker.io/setup/install/halyard_migration/"
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
      --halyard-bucket-base-url)
        echo "halyard-bucket-base-url"
        HALYARD_BUCKET_BASE_URL="$1"
        shift
        ;;
      --download-with-gsutil)
        echo "download-with-gsutil"
        DOWNLOAD_WITH_GSUTIL=true
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
        exit 1
    esac
  done
}

function get_user() {
  local user

  user=$(whoami)
  if [ -z "$YES" ]; then
    if [ "$user" = "root" ] || [ -z "$user" ]; then
      read -p "Please supply a non-root user to run Halyard as: " user
    fi
  fi

  echo $user
}

function get_home() {
  getent passwd $HAL_USER | cut -d: -f6
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

  if [ -z "$HALYARD_BUCKET_BASE_URL" ]; then
    HALYARD_BUCKET_BASE_URL="gs://spinnaker-artifacts/halyard"
  fi

  echo "$(tput bold)Halyard will be downloaded from $HALYARD_BUCKET_BASE_URL $(tput sgr0)"

  if [ -z "$CONFIG_BUCKET" ]; then
    CONFIG_BUCKET="halconfig"
  fi

  echo "$(tput bold)Halyard config will come from bucket gs://$CONFIG_BUCKET $(tput sgr0)"

  home=$(get_home)
  local halconfig_dir="$home/.hal"

  echo "$(tput bold)Halconfig will be stored at $halconfig_dir/config$(tput sgr0)"

  mkdir -p $halconfig_dir
  chown $HAL_USER $halconfig_dir

  mkdir -p /opt/spinnaker/config
  chmod a+rx /opt/spinnaker/config

  cat > /opt/spinnaker/config/halyard.yml <<EOL
halyard:
  halconfig:
    directory: $halconfig_dir

spinnaker:
  artifacts:
    debianRepository: $SPINNAKER_REPOSITORY_URL
    dockerRegistry: $SPINNAKER_DOCKER_REGISTRY
    googleImageProject: $SPINNAKER_GCE_PROJECT
  config:
    input:
      bucket: $CONFIG_BUCKET
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
rm -f /usr/local/bin/hal /usr/local/bin/update-halyard

echo "Deleting halconfig and artifacts"
rm /opt/spinnaker/config/halyard* -rf
rm $halconfig_dir -rf
EOL

  chmod a+rx $halconfig_dir/uninstall.sh
  echo "$(tput bold)Uninstall script is located at $halconfig_dir/uninstall.sh$(tput sgr0)"
}


function print_usage() {
  cat <<EOF
usage: $0 [-y] [--version=<version>] [--user=<user>]
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

    --user <user>                   Specify the user to run Halyard as. This
                                    user must exist.
EOF
}

function check_java() {

  if ! which java 2>&1 > /dev/null; then
    echo "Couldn't find a 'java' binary in your \$PATH. Halyard requires Java to run."
    exit 1
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

  if [ "$yes" = "y" ] || [ "$yes = "Y" ] || [ "$yes = "yes" ] || [ "$yes" = "" ]; then
    local home=$(get_home)
    completion_script="/etc/bash_completion.d/hal"

    mkdir -p $(dirname $completion_script)
    hal --print-bash-completion | tee $completion_script  > /dev/null

    local bashrc
    if [ -z "$YES" ]; then
      echo ""
      read -p "Where is your bash RC? [default=$home/.bashrc]: " bashrc
    fi

    if [ -z "$bashrc" ]; then
      bashrc="$home/.bashrc"
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
  TEMPDIR=$(mktemp -d installhalyard.XXXX)
  pushd $TEMPDIR
  local gcs_bucket_and_file

  if [[ "$HALYARD_BUCKET_BASE_URL" != gs://* ]]; then
    >&2 echo "Currently installing halyard is only supported from a GCS bucket."
    >&2 echo "The --halyard-install-url parameter must start with 'gs://'."
    exit 1
  else
    gcs_bucket_and_file=${HALYARD_BUCKET_BASE_URL:5}/$HALYARD_VERSION/debian/halyard.tar.gz
  fi

  if [ -n "$DOWNLOAD_WITH_GSUTIL" ]; then
    gsutil cp gs://$gcs_bucket_and_file halyard.tar.gz
  else
    curl -O https://storage.googleapis.com/$gcs_bucket_and_file
  fi

  tar --no-same-owner -xvf halyard.tar.gz -C /opt


  if which systemd-sysusers &>/dev/null; then
    if [ ! -d "/usr/lib/sysusers.d" ]; then
      if [ ! -L "/usr/lib/sysusers.d" ]; then
        echo "Creating /usr/lib/sysusers.d directory."
        install -dm755 -o root -g root /usr/lib/sysusers.d
      fi
    fi
    cat > /usr/lib/sysusers.d/halyard.conf <<EOL
g halyard - -
g spinnaker - -
EOL

    systemd-sysusers &> /dev/null || true

  else
    groupadd halyard || true
    groupadd spinnaker || true
  fi

  usermod -G halyard -a $HAL_USER || true
  usermod -G spinnaker -a $HAL_USER || true
  chown $HAL_USER:halyard /opt/halyard

  mv /opt/hal /usr/local/bin
  chmod a+rx /usr/local/bin/hal

  if [ -f /opt/update-halyard ]; then
    mv /opt/update-halyard /usr/local/bin
    chmod a+rx /usr/local/bin/update-halyard
  else
    echo "No update script supplied with installer..."
  fi

  mkdir -p /var/log/spinnaker/halyard
  chown $HAL_USER:halyard /var/log/spinnaker/halyard
  chmod 755 /var/log/spinnaker /var/log/spinnaker/halyard

  popd
  rm -rf $TEMPDIR
}

check_migration_needed

process_args $@
configure_defaults

check_java
install_halyard

su -l -c "hal -v" -s /bin/bash $HAL_USER

configure_bash_completion
