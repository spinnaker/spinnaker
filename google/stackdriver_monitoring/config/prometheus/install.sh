#!/bin/bash
# Copyright 2017 Google Inc. All Rights Reserved.
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


PROMETHEUS_VERSION=prometheus-1.5.0.linux-amd64
PROMETHEUS_PORT=9090
GRAFANA_PORT=3000
CONFIG_DIR=$(readlink -f `dirname $0`)
cd /opt

# Install Prometheus
curl -L -o /tmp/prometheus.gz \
     https://github.com/prometheus/prometheus/releases/download/v1.5.0/prometheus-1.5.0.linux-amd64.tar.gz
sudo tar xzf /tmp/prometheus.gz -C /opt
rm /tmp/prometheus.gz

curl -L -o /tmp/node_exporter.gz \
     https://github.com/prometheus/node_exporter/releases/download/v0.13.0/node_exporter-0.13.0.linux-amd64.tar.gz
sudo tar xzf /tmp/node_exporter.gz -C /opt/prometheus-1.5.0.linux-amd64
sudo ln -s /opt/prometheus-1.5.0.linux-amd64/node_exporter-0.13.0.linux-amd64/node_exporter /usr/bin/node_exporter
rm /tmp/node_exporter.gz

sudo cp $CONFIG_DIR/spinnaker-prometheus.yml prometheus-1.5.0.linux-amd64
sudo cp $CONFIG_DIR/prometheus.conf /etc/init/prometheus.conf
sudo cp $CONFIG_DIR/node_exporter.conf /etc/init/node_exporter.conf


# Install Grafana
cd /tmp
wget https://grafanarel.s3.amazonaws.com/builds/grafana_4.1.1-1484211277_amd64.deb
sudo apt-get install -y adduser libfontconfig
sudo dpkg -i grafana_4.1.1-1484211277_amd64.deb
sudo update-rc.d grafana-server defaults
rm grafana_4.1.1-1484211277_amd64.deb


# Startup
echo "Starting Prometheus"
sudo service node_exporter start
sudo service prometheus start
sudo service grafana-server start

TRIES=0
until nc -z localhost $GRAFANA_PORT || [[ $TRIES -gt 5 ]]; do
  sleep 1
  let TRIES+=1
done

echo "Adding datasource"
PAYLOAD="{'name':'Spinnaker','type':'prometheus','url':'http://localhost:${PROMETHEUS_PORT}','access':'direct','isDefault':true}"
curl -u admin:admin http://localhost:${GRAFANA_PORT}/api/datasources \
     -H "Content-Type: application/json" \
     -X POST \
     -d "${PAYLOAD//\'/\"}"

for dashboard in ${CONFIG_DIR}/*Dashboard.json; do
  echo "Installing $(basename $dashboard)"
  x=$(sed -e "/\"__inputs\"/,/],/d" \
          -e "/\"__requires\"/,/],/d" \
          -e "s/\${DS_SPINNAKER\}/Spinnaker/g" < "$dashboard")
  temp_file=$(mktemp)
  echo "{ \"dashboard\": $x }" > $temp_file
  curl -u admin:admin http://localhost:${GRAFANA_PORT}/api/dashboards/import \
       -H "Content-Type: application/json" \
       -X POST \
       -d @${temp_file}
  rm -f $temp_file
done
