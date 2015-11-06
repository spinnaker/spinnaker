#!/bin/bash

dist=`gawk -F= '/^NAME/{print $2}' /etc/os-release`

if [[ "$dist" == *Ubuntu* ]]; then

  # Apache

  a2enmod http_proxy

  # Copy vhosts

  # Restart apache

  # Install cassandra keyspaces

  # Start all the services 

  # test dir created to test block execution
  mkdir -p /home/ubuntu/3

fi

if [[ "$dist" == *"Amazon Linux AMI"* ]]; then
  echo "Amazon Linux AMI"
fi