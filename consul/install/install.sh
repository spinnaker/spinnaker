#! /bin/bash

CONSUL_VERSION=0.6.4
CONSUL_ARCH=linux_amd64

wget https://releases.hashicorp.com/consul/${CONSUL_VERSION}/consul_${CONSUL_VERSION}_${CONSUL_ARCH}.zip

apt-get update
apt-get install unzip

unzip consul_${CONSUL_VERSION}_${CONSUL_ARCH}.zip
rm consul_${CONSUL_VERSION}_${CONSUL_ARCH}.zip

mv consul /usr/bin
