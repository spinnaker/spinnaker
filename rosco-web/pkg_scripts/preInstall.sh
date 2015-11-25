#!/bin/sh

# backup everything in /opt/rosco/config/packer
if [ -d /opt/rosco/config/packer ]; then
  if [ -d /opt/rosco/config/packer/backup ]; then
    rm -rf /opt/rosco/config/packer/backup/*
  else
    mkdir /opt/rosco/config/packer/backup
  fi
  find /opt/rosco/config/packer -type f -not -iname 'backup' -print0 | xargs -0 cp -t /opt/rosco/config/packer/backup/
fi
