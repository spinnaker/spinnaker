#!/bin/sh

## This script install pre-requisites for Spinnaker
# To you put this file in the root of a web server
# curl -L https://foo.com/InstallSpinnaker.sh| sudo bash


## PPAs ##
# Add PPAs for software that is not necessarily in sync with Ubuntu releases

# Redis
# https://launchpad.net/~chris-lea/+archive/ubuntu/redis-server

add-apt-repository -y -q ppa:chris-lea/redis-server

# Cassandra
# http://docs.datastax.com/en/cassandra/2.1/cassandra/install/installDeb_t.html

curl -L http://debian.datastax.com/debian/repo_key | sudo apt-key add -
echo "deb http://debian.datastax.com/community/ stable main" > /etc/apt/sources.list.d/datastax.list

# Java 8
# https://launchpad.net/~openjdk-r/+archive/ubuntu/ppa 

add-apt-repository -y -q ppa:openjdk-r/ppa

# Spinnaker
# DL Repo goes here
 echo "deb http://dl.bintray.com/spinnaker/ospackages ./" > /etc/apt/sources.list.d/spinnaker.list

## Install software
apt-get update
apt-get install -y spinnaker


