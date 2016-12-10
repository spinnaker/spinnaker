 #!/bin/bash

#run install script
#wget https://dl.bintray.com/kenzanlabs/spinnaker_scripts/InstallSpinnaker.sh
#chmod +x InstallSpinnaker.sh
#sudo ./InstallSpinnaker.sh
##pasting it here for now in case it changes

## This script install pre-requisites for Spinnaker
# To you put this file in the root of a web server
# curl -L https://foo.com/InstallSpinnaker.sh| sudo bash

# We can only currently support limited releases
# First guess what sort of operating system

echo '/  ___| ___ \_   _| \ | || \ | | / _ \ | | / /|  ___| ___ \'
echo '\ `--.| |_/ / | | |  \| ||  \| |/ /_\ \| |/ / | |__ | |_/ /'
echo ' `--. \  __/  | | | . ` || . ` ||  _  ||    \ |  __||    / '
echo '/\__/ / |    _| |_| |\  || |\  || | | || |\  \| |___| |\ \ '
echo '\____/\_|    \___/\_| \_/\_| \_/\_| |_/\_| \_/\____/\_| \_|'


CLOUD_PROVIDER=amazon
DEFAULT_REGION=us-west-2




add-apt-repository -y ppa:chris-lea/redis-server

# Cassandra
# http://docs.datastax.com/en/cassandra/2.1/cassandra/install/installDeb_t.html

curl -L http://debian.datastax.com/debian/repo_key | sudo apt-key add -
echo "deb http://debian.datastax.com/community/ stable main" > /etc/apt/sources.list.d/datastax.list

# Java 8
# https://launchpad.net/~openjdk-r/+archive/ubuntu/ppa

add-apt-repository -y ppa:webupd8team/java
echo oracle-java8-installer shared/accepted-oracle-license-v1-1 select true | sudo /usr/bin/debconf-set-selections
# Spinnaker
# DL Repo goes here
# echo "deb http://dl.bintray.com/spinnaker/ospackages ./" > /etc/apt/sources.list.d/spinnaker.list
# echo 'deb http://jenkins.staypuft.kenzan.com:8000/ trusty main' > /etc/apt/sources.list.d/spinnaker-dev.list
echo 'deb https://dl.bintray.com/kenzanlabs/spinnaker trusty spinnaker' > /etc/apt/sources.list.d/spinnaker-dev.list

## Install software
# "service cassandra status" is currently broken in Ubuntu grep in the script is grepping for things that do not exist
# Cassandra 2.x can ship with RPC disabled to enable run "nodetool enablethrift"

apt-get update
apt-get install -y oracle-java8-installer
apt-get install -y cassandra=2.1.11 cassandra-tools=2.1.11

# Let cassandra start
sleep 5
nodetool enablethrift
# apt-get install dsc21


apt-get install -y --force-yes --allow-unauthenticated spinnaker

sed -i.bak -e "s/SPINNAKER_AWS_ENABLED=.*$/SPINNAKER_AWS_ENABLED=true/" -e "s/SPINNAKER_AWS_DEFAULT_REGION.*$/SPINNAKER_AWS_DEFAULT_REGION=${DEFAULT_REGION}/" -e "s/SPINNAKER_GCE_ENABLED=.*$/SPINNAKER_GCE_ENABLED=false/" /etc/default/spinnaker

service clouddriver start
service orca start
service gate start
service rosco start
service front50 start
service echo start



#install newest deck
mv /var/www /var/www_old
wget https://bintray.com/artifact/download/spinnaker/ospackages/deck_2.352-3_all.deb
dpkg -i deck_2.352-3_all.deb

#add reverse proxy
service apache2 stop
a2enmod proxy proxy_ajp proxy_http rewrite deflate headers proxy_balancer proxy_connect proxy_html xml2enc
rm -f /etc/apache2/sites-enabled/*.conf
rm -f /etc/apache2/sites-available/*.conf
touch /etc/apache2/sites-available/spinnaker.conf

cat <<EOT >> /etc/apache2/sites-available/spinnaker.conf
<VirtualHost *:80>
  DocumentRoot /var/www

<Location /gate>
  ProxyPass http://localhost:8084
  ProxyPassReverse http://localhost:8084
  Order allow,deny
  Allow from all
</Location>

<Location /bakery>
  ProxyPass http://localhost:8087
  ProxyPassReverse http://localhost:8087
  Order allow,deny
  Allow from all
</Location>

<Location /jenkins>
  ProxyPass http://localhost:9999
  ProxyPassReverse http://localhost:9999
  Order allow,deny
  Allow from all
</Location>

</VirtualHost>
EOT




#docker
curl -sSL https://get.docker.com/ | sh
service docker stop
rm /etc/default/docker
echo 'DOCKER_OPTS="--default-ulimit nofile=1024:4096 -H tcp://0.0.0.0:7104 -H unix:///var/run/docker.sock -r=false"' >> /etc/default/docker
service docker start



a2ensite spinnaker
service apache2 start

chmod +x /home/ubuntu/config.sh
