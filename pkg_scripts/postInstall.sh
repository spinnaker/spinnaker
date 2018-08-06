#!/bin/sh

if [ ! -f /opt/spinnaker/config/spinnaker-local.yml ]; then
  # Create master config on original install, but leave in place on upgrades.
  cp /opt/spinnaker/config/default-spinnaker-local.yml /opt/spinnaker/config/spinnaker-local.yml
fi

# enable deck and reverse proxy in apache
/usr/sbin/a2ensite spinnaker
/usr/sbin/a2enmod proxy_http
service apache2 restart

# Disable auto upstart of the services.
# We'll have spinnaker auto start, and start them as it does.
for s in clouddriver orca front50 rosco echo fiat gate igor; do
    if [ ! -e /etc/init/$s.override ]; then
        /bin/echo -e "limit nofile 32768 32768\nmanual" | sudo tee /etc/init/$s.override;
    fi
done
