#!/usr/bin/env bash

bold() {
  echo ". $(tput bold)" "$*" "$(tput sgr0)";
}

err() {
  echo "$*" >&2;
}

bold "Configuring codelab-specific settings..."

bold "Installing the 'jsonnet' CLI..."

curl -LO https://storage.googleapis.com/jsonnet/$(curl -s https://storage.googleapis.com/jsonnet/latest)/linux/amd64/jsonnet
chmod +x jsonnet
mv jsonnet bin/

bold "Installing 'sponnet' libraries..."

curl -LO https://storage.googleapis.com/spinnaker-artifacts/sponnet/$(curl -s https://storage.googleapis.com/spinnaker-artifacts/sponnet/latest)/sponnet.tar.gz
tar -xzvf sponnet.tar.gz && rm sponnet.tar.gz
