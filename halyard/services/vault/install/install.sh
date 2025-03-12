#! /bin/bash

VAULT_VERSION=0.7.0
VAULT_ARCH=linux_amd64

apt-get update
apt-get install unzip -y

# Download & unzip specified vault binary
wget https://releases.hashicorp.com/vault/${VAULT_VERSION}/vault_${VAULT_VERSION}_${VAULT_ARCH}.zip
unzip -u -o -q vault_${VAULT_VERSION}_${VAULT_ARCH}.zip -d /usr/bin

rm vault_${VAULT_VERSION}_${VAULT_ARCH}.zip

mv vault /usr/bin

# Create directory for running vault

mkdir -p /etc/vault.d/
mkdir -p /var/vault

adduser vault

# Setup config

cp -r config/vault.hcl /etc/vault.d/vault.hcl

# Enable mlock

setcap cap_ipc_lock=+ep $(readlink -f $(which vault))

# Setup upstart

cp upstart/vault.conf /etc/init/

chown -R vault:vault /var/vault
chown -R vault:vault /etc/vault.d
