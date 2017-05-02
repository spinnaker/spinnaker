#!/usr/bin/env bash

## generate install scripts for halyard release tracks

for track in "nightly" "stable"; do
  dest="install/$track/InstallHalyard.sh"
  cp install/InstallHalyard.sh.gen $dest
  sed -i -e "s|{%header-comment%}|_WARNING_ This file was auto generated, do not edit directly|g" $dest
  sed -i -e "s|{%release-track%}|$track|g" $dest
done
