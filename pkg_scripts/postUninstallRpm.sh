#!/bin/sh

if grep -Fxq "Listen 127.0.0.1:9000" /etc/httpd/conf/httpd.conf
then
  sed -i "s/Listen 127.0.0.1:9000//" /etc/httpd/conf/httpd.conf
fi

/opt/spinnaker/bin/disable_httpd_site.sh spinnaker
service httpd restart
