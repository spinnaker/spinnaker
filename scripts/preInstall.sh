#!/bin/bash

dist=`gawk -F= '/^NAME/{print $2}' /etc/os-release`

if [[ "$dist" == *Ubuntu* ]]; then
  # Create spinnaker user
  /usr/sbin/useradd spinnaker

  # test dir created to test block execution
  mkdir -p /home/ubuntu/2

fi

if [[ "$dist" == *"Amazon Linux AMI"* ]]; then
  echo "Amazon Linux AMI"
fi
