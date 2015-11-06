#!/bin/bash

dist=`gawk -F= '/^NAME/{print $2}' /etc/os-release`

if [[ "$dist" == *Ubuntu* ]]; then

  ## PPAs ##
  # Add PPAs for software that is not necessarily in sync with Ubuntu releases

  # Redis
  # https://launchpad.net/~chris-lea/+archive/ubuntu/redis-server

  add-apt-repository -y -q ppa:chris-lea/redis-server

  # Cassandra
  # http://docs.datastax.com/en/cassandra/2.1/cassandra/install/installDeb_t.html

  curl -L http://debian.datastax.com/debian/repo_key | sudo apt-key add -
  echo "deb http://debian.datastax.com/community/ stable main" >> /etc/apt/sources.list.d/datastax.list

  # Java 8
  # https://launchpad.net/~openjdk-r/+archive/ubuntu/ppa 

  add-apt-repository -y -q ppa:openjdk-r/ppa

  # Spinnaker
  # DL Repo goes here


  ## Install software
  apt-get update
  apt-get install -y openjdk-8-jdk
  update-alternatives --config java
  update-alternatives --config javac

  apt-get install -y redis-server dsc21
  apt-get install -y apache2
 # apt-get install -y spinnaker
  
  # test dir created to test block execution
  mkdir -p /home/ubuntu/1

fi

if [[ "$dist" == *"Amazon Linux AMI"* ]]; then
  echo "Amazon Linux AMI"
fi

