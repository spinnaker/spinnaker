#!/bin/bash

# Enable an httpd site, by creating a symbolic link from /etc/httpd/sites-available/{site}.conf to
# /etc/httpd/sites-enabled

if [ "$#" != "1" ]; then
  echo "Please specify one site name to enable."
  exit 1
fi

if test ! -d /etc/httpd/sites-available || test ! -d /etc/httpd/sites-enabled  ; then
  mkdir -p /etc/httpd/sites-available
  mkdir -p /etc/httpd/sites-enabled
fi

if ! grep -Fxq "Include sites-available/*.conf" /etc/httpd/conf/httpd.conf
then
  echo "Include sites-available/*.conf" >> /etc/httpd/conf/httpd.conf
fi

if [ "$#" == "1" ]; then
  site=/etc/httpd/sites-available/$1.conf
  enabled=/etc/httpd/sites-enabled/

  if test -e $enabled/$1.conf; then
    echo "$site is already enabled"
    exit 0
  fi

  if test -e $site; then
    sudo ln -s $site $enabled
  else
    echo "Site $site could not be found."
    exit 1
  fi

  if test -e $enabled/$1.conf; then
    echo "Successfuly enabled $site. Restart Apache server."
  else
    echo "Could not enable virtual site $site."
    exit 1
  fi
fi
