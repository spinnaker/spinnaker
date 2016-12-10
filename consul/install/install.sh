#! /bin/bash

if [ -z "${1}" ]; then
    echo "Please provide either 'server' or 'client' as the only argument to this script"
fi

CONSUL_VERSION=0.6.4
CONSUL_ARCH=linux_amd64

# Download & unzip specified consul binary
wget https://releases.hashicorp.com/consul/${CONSUL_VERSION}/consul_${CONSUL_VERSION}_${CONSUL_ARCH}.zip

apt-get update
apt-get install unzip -y

unzip consul_${CONSUL_VERSION}_${CONSUL_ARCH}.zip
rm consul_${CONSUL_VERSION}_${CONSUL_ARCH}.zip

mv consul /usr/bin

# Create directory for running consul

mkdir -p /etc/consul.d/
mkdir -p /var/consul

adduser consul

chown consul:consul /var/consul

# Setup config

cp -r config/* /etc/consul.d/

# Setup upstart

cp upstart/consul.${1}.conf /etc/init/
