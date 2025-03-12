#!/usr/bin/env bash

CONSUL_JOIN_FILE="/etc/consul.d/join.json"

echo "Configuring consul members for the $1 provider"

members=$({%startup-script-path%}$1/get-members.sh)

echo '{ "start_join": [' > $CONSUL_JOIN_FILE

echo \"${members[@]// /\",\"}\" >> $CONSUL_JOIN_FILE

echo '] }' >> $CONSUL_JOIN_FILE

# Configure local nameserver

apt-get install resolvconf

echo nameserver 127.0.0.1 >> /etc/resolvconf/resolv.conf.d/head

resolvconf -u

service consul restart
