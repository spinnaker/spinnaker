#!/bin/sh

if [ ! -f /opt/spinnaker/config/spinnaker-local.yml ]; then
  # Create master config on original install, but leave in place on upgrades.
  cp /opt/spinnaker/config/default-spinnaker-local.yml /opt/spinnaker/config/spinnaker-local.yml
fi

# deck settings
/opt/spinnaker/bin/reconfigure_spinnaker.sh

if ! grep -Fxq "Listen 127.0.0.1:9000" /etc/httpd/conf/httpd.conf
then
  echo "Listen 127.0.0.1:9000" >> /etc/httpd/conf/httpd.conf
fi

/opt/spinnaker/bin/enable_httpd_site.sh spinnaker
service httpd restart
