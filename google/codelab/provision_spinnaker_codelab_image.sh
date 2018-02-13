#!/bin/bash

# Copyright 2016 Google Inc. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# This script provisions a fresh spinnaker image with a Jenkins
# server and an aptly repository, served on nginx. It also
# provides a basic Jenkins configuration. This is to be used
# specifically for creating a new (running) codelab image out of a fresh
# spinnaker image.

if ! hal --version; then
  echo "Spinnaker is not installed. Look at http://www.spinnaker.io for installation instructions."
  exit 1
fi

set -e

if [[ `/usr/bin/id -u` -ne 0 ]]; then
  echo "$0 must be executed with root permissions; exiting"
  exit 1
fi

service spinnaker stop || true
service apache2 stop || true

# this allows us to skip any interactive post-install configuration,
# specifically around keeping defaults for files that were modified.
export DEBIAN_FRONTEND=noninteractive

# we don't want to update these. see github.com/spinnaker/spinnaker/issues/1279
# for context.
SPINNAKER_SUBSYSTEMS="spinnaker-clouddriver spinnaker-deck spinnaker-echo spinnaker-fiat spinnaker-front50 spinnaker-gate spinnaker-halyard spinnaker-igor spinnaker-orca spinnaker-rosco spinnaker"

apt-mark hold $SPINNAKER_SUBSYSTEMS
# update apt
apt-get update
# temporary workaround for where DEBIAN_FRONTEND=noninteractive isn't enough
apt-get -o Dpkg::Options::="--force-confdef" -o Dpkg::Options::="--force-confold" -y upgrade
apt-mark unhold $SPINNAKER_SUBSYSTEMS

# acquire and configure jenkins
apt-get install -y git
wget https://pkg.jenkins.io/debian-stable/binary/jenkins_2.89.3_all.deb
# dpkg partially installs jenkins and fails
dpkg -i jenkins_2.89.3_all.deb || true
rm -f jenkins_2.89.3_all.deb

# finish installing jenkins and its dependencies
apt-get -f -y install
sed -i "s/HTTP_PORT=.*/HTTP_PORT=5656/" /etc/default/jenkins

# as jenkins, configure aptly
JENKINS_HOMEDIR=~jenkins

cd $JENKINS_HOMEDIR
touch keep_user
wget https://dl.bintray.com/smira/aptly/aptly_1.2.0_linux_amd64.tar.gz
tar -xf aptly_1.2.0_linux_amd64.tar.gz
rm aptly_1.2.0_linux_amd64.tar.gz

sudo -u jenkins ln -s aptly_1.2.0_linux_amd64/aptly aptly
sudo -u jenkins -H sh -c "./aptly repo create hello"
sudo -u jenkins -H sh -c './aptly publish repo -architectures="amd64,i386" -component=main -distribution=trusty -skip-signing=true hello'

# as jenkins, configure jenkins config directory
# this storage bucket is public so we can pull the jenkins config from anywhere
cd /var/lib/jenkins
sudo -u jenkins -H sh -c 'wget https://storage.googleapis.com/codelab-jenkins-configuration/jenkins_dir.tar.gz'
sudo -u jenkins -H sh -c 'tar -zxvf jenkins_dir.tar.gz'
sudo -u jenkins -H sh -c 'rm jenkins_dir.tar.gz'
sudo -u jenkins -H sh -c 'git clone https://github.com/kenzanlabs/hello-karyon-rxnetty.git'
sudo -u jenkins -H sh -c 'cd /var/lib/jenkins/jobs/Hello-Build; rm -rf builds lastStable lastSuccessful scm-polling.log nextBuildNumber; echo 1 >> nextBuildNumber'

service jenkins restart

# configure nginx to serve the aptly repo and start it
apt-get install -y nginx
nginx_default="/etc/nginx/sites-enabled/default"
if [[ -e "$nginx_default" ]]; then
  echo "Warning: nginx default file exists. Copying to '/etc/nginx/sites-enabled/default.orig' and writing new default file."
  mv $nginx_default /etc/nginx/sites-enabled/default.orig
fi
cat > $nginx_default <<EOF
server {
        listen 9999 default_server;
        listen [::]:9999 default_server ipv6only=on;
        root $JENKINS_HOMEDIR/.aptly/public;
        index index.html index.htm;
        server_name localhost;
        location / {
                try_files \$uri \$uri/ =404;
        }
}
EOF


mv $(dirname $0)/first_codelab_boot.sh /var/spinnaker/startup
chmod 755 /var/spinnaker/startup/first_codelab_boot.sh
chown spinnaker:spinnaker /var/spinnaker/startup/first_codelab_boot.sh

