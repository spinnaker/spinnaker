#!/bin/bash

URL="https://dl.bintray.com/kenzanlabs/spinnaker/InstallSpinnaker.sh"

function download {
  scratch=$(mktemp -d -t tmp.XXXXXXXXXX) || exit
  script_file=$scratch/InstallSpinnaker.bash

  echo "Downloading Spinnaker install script: $URL"
  curl -# $URL > $script_file || exit
  chmod 775 $script_file

  echo "Running install script from: $script_file"
  $script_file
}

download
