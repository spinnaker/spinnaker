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


PACKAGE_SOURCE=""  # Eventually this can be default bintray location
function determine_package_source() {
    result=$(getopt -q --longoptions release_path --name "$0" -- "$@")
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --release_path)
                PACKAGE_SOURCE="$2"
                return 0
        esac
        shift
    done
    return 1
}

function main() {
    determine_package_source "$@"
    if [[ "$PACKAGE_SOURCE" == "" ]]; then
        echo "Missing --release_path."
        exit -1
    fi
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

main "$@"
