#!/usr/bin/env bash

## auto-generated debian uninstall file written by halyard

set -e
set -o pipefail

## check that the user is root
if [[ `/usr/bin/id -u` -ne 0 ]]; then
  echo "$0 must be executed with root permissions; exiting"
  exit 1
fi

{%uninstall-artifacts%}

rm /opt/spinnaker/config -rf
rm /opt/spinnaker-monitoring -rf
rm /etc/apt/preferences.d/pin-spin-*
