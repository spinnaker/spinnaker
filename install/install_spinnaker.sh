#!/bin/bash

# Install Spinnaker from source

# Usage:
#   install_spinnaker.sh [--release_path <path to bucket>]

function download_gcs() {
  gsutil cp $1 $2
}

function download_s3() {
    awscli s3 cp $1 $2
}

DEFAULT_REPOSITORY="https://dl.bintray.com/NETFLIX_DEFAULT_HERE"
PACKAGE_SOURCE="" # If installing from a bucket
REPO_URL="$DEFAULT_REPOSITORY"
function determine_package_source() {
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --release_path)
                PACKAGE_SOURCE="$2"
                return 0
                ;;
            --repo)
                REPO_URL="$2"
                return 0
                ;;
        esac
        shift
    done
    return 1
}

function install_from_path() {
    from="$PACKAGE_SOURCE/spinnaker.tar.gz"
    to="install/spinnaker.tar.gz"

    echo "Downloading package"
    mkdir -p /opt/spinnaker/install
    cd /opt/spinnaker

    if [[ "$from" =~ ^gs:// ]]; then
      download_gcs $from $to
    elif [[ "$from" =~ ^s3:// ]]; then
      download_s3 $from $to
    else
      cp $from $to
    fi

    echo "Installing package"
    tar xzf $to
    PYTHONPATH=pylib python install/install_spinnaker.py \
        --release_path=$PACKAGE_SOURCE \
        "$@"
}

function install_from_repo() {
    DISTRIBUTION=${DISTRIBUTION:-""}
    if [[ "$DISTRIBUTION" == "" ]]; then
       if [ ! -f /etc/lsb-release ]; then
           echo "Need an /etc/lsb-release for now."
           exit -1
       fi
       DISTRIBUTION=$(grep DISTRIB_CODENAME /etc/lsb-release \
                      | sed 's/DISTRIB_CODENAME=//g')
    fi

    sudo bash -c "echo 'deb $DEFAULT_REPOSITORY $DISTRIBUTION spinnaker' > /etc/apt/sources.list.d/spinnaker.list"
    sudo apt-get update
    sudo apt-get install -y --force-yes --allow-unauthenticated spinnaker
    sudo PYTHONPATH=/opt/spinnaker/pylib \
         python /opt/spinnaker/install/install_runtime_dependencies.py \
         --package_manager
    sudo /opt/spinnaker/install/post_install_spinnaker.sh
}

function main() {
    determine_package_source "$@"

    if [[ "$PACKAGE_SOURCE" == "" ]] && [[ "$REPO_URL" == "" ]]; then
        echo "Missing --release_path or --repo."
        exit -1
    fi

    if [[ "$PACKAGE_SOURCE" != "" ]]; then
        install_from_path "$@"
    else
        install_from_repo
    fi
}

main $@
