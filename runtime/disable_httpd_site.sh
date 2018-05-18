#!/bin/bash

# Disable an httpd site, by remove the symbolic link from /etc/httpd/sites-enabled/{site}.conf

if [ "$#" != "1" ]; then
        echo "Please specify one site name to disable."
        exit 1
fi

if [ "$#" == "1" ]; then
  site=/etc/httpd/sites-enabled/$1.conf

  if test -e $site; then
    unlink $site
    echo "Successfuly disabled $site. Restart Apache server."
  else
    echo "Site $site could not be found."
  fi
fi
