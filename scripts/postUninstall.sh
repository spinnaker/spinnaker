#!/bin/bash

dist=`gawk -F= '/^NAME/{print $2}' /etc/os-release`

if [[ "$dist" == *Ubuntu* ]]; then
  # test dir created to test block execution
  mkdir -p /home/ubuntu/4

fi

if [[ "$dist" == *"Amazon Linux AMI"* ]]; then
  echo "Amazon Linux AMI"
fi