#!/bin/sh

## This script install pre-requisites for Spinnaker
# To you put this file in the root of a web server
# curl -L https://foo.com/InstallSpinnaker.sh| sudo bash

# We can only currently support limited releases
# First guess what sort of operating system

if [ -f /etc/lsb-release ]; then
    . /etc/lsb-release
    DISTRO=$DISTRIB_ID
elif [ -f /etc/debian_version ]; then
    DISTRO=Debian
    # XXX or Ubuntu
elif [ -f /etc/redhat-release ]; then
    if grep -iq cent /etc/redhat-release; then
      DISTRO="CentOS"
    elif grep -iq red /etc/redhat-release; then
      DISTRO="RedHat"
    fi
    
    else
    DISTRO=$(uname -s)
fi

# If not Ubuntu 14.xx.x or higher

if [ "$DISTRO" = "Ubuntu" ]; then
  if [ "${DISTRIB_RELEASE%%.*}" -ne 14 ]; then
  echo "Not a supported version of Ubuntu"
  echo "Version is $DISTRIB_RELEASE we require 14.02 or higher"
  exit 1
  fi
else
  echo "Not a supported operating system"
  echo "Recommend you use Ubuntu 14.10 or higher"
  exit 1
fi



## PPAs ##
# Add PPAs for software that is not necessarily in sync with Ubuntu releases

# Redis
# https://launchpad.net/~chris-lea/+archive/ubuntu/redis-server

add-apt-repository -y ppa:chris-lea/redis-server

# Cassandra
# http://docs.datastax.com/en/cassandra/2.1/cassandra/install/installDeb_t.html

curl -L http://debian.datastax.com/debian/repo_key | sudo apt-key add -
echo "deb http://debian.datastax.com/community/ stable main" > /etc/apt/sources.list.d/datastax.list

# Java 8
# https://launchpad.net/~openjdk-r/+archive/ubuntu/ppa 

add-apt-repository -y ppa:webupd8team/java
echo oracle-java8-installer shared/accepted-oracle-license-v1-1 select true | sudo /usr/bin/debconf-set-selections
# Spinnaker
# DL Repo goes here
# echo "deb http://dl.bintray.com/spinnaker/ospackages ./" > /etc/apt/sources.list.d/spinnaker.list
echo 'deb http://jenkins.staypuft.kenzan.com:8000/ trusty main' > /etc/apt/sources.list.d/spinnaker-dev.list

## Install software
# "service cassandra status" is currently broken in Ubuntu grep in the script is grepping for things that do not exist
# Cassandra 2.x can ship with RPC disabeld to enable run "nodetool enablethrift"

apt-get update
apt-get install -y oracle-java8-installer
apt-get install -y cassandra=2.1.11 cassandra-tools=2.1.11

# Let cassandra start
sleep 5
nodetool enablethrift
# apt-get install dsc21


apt-get install -y --force-yes --allow-unauthenticated spinnaker

read -p "Enable Amazon AWS? (Y|n)" enableAws
if [[ " ${enableAws,,}" == "y" ]]; then
    setEnableAws="true"
    read -p "Default region: " defaultRegion
    sed -i.bak -e "s/false/$setEnableAws" -e "s/us-west-2/$defaultRegion" /etc/default/spinnaker
fi

