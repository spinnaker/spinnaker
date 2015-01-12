# Install Java 8
if [ ! -d "/usr/lib/jvm/jdk1.8.0_25" ]; then
  curl -L -b "oraclelicense=a" http://download.oracle.com/otn-pub/java/jdk/8u25-b17/jdk-8u25-linux-x64.tar.gz > /tmp/jdk8.tar.gz
  tar -zxvf /tmp/jdk8.tar.gz -C /usr/lib/jvm
  rm /etc/alternatives/java
  ln -s /usr/lib/jvm/jdk1.8.0_25/bin/java /etc/alternatives/java
fi

# Fix Front50
if [ -f "/opt/front50/bin/front50-web" ]; then
  mv /opt/front50/bin/front50-web /opt/front50/bin/front50
fi

# Move configs to a common location; makes for easier management.
for svc in kato mort oort orca gate;
do
  if [ ! -L /opt/$svc/config/$svc.yml ]; then
    mv /opt/$svc/config/$svc.yml /opt/spinnaker/config/
    ln -s /opt/spinnaker/config/$svc.yml /opt/$svc/config/$svc.yml
  fi
done

# Override deck's config with a local one
mv /var/www/scripts/local.js /var/www/scripts/settings.js

# Restart Redis
service redis-server restart

# Remove default site
if [ -L /etc/apache2/sites-enabled/000-default.conf ]; then
  rm /etc/apache2/sites-enabled/000-default.conf
fi

# Configure ProxyPass
a2enmod proxy_http

# Restart Apache
service apache2 restart
