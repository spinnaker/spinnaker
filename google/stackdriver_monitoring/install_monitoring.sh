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


SOURCE_DIR=$(readlink -f `dirname $0`)
COMMAND_LINE_FLAGS=("$@")
USE_DATADOG=false
USE_PROMETHEUS=false
USE_STACKDRIVER=false
PROVIDERS=""
EXTRA_ARGS=""

function print_usage() {
  cat <<-EOF
	`basename $0`: <provider_switch>+ \
	               <monitor_options>* \
	               <service_options>* \
	               <provider_options>*

	<provider_switch> is one or more of:
	   --datadog
	        Install and configure a Datadog agent.
	        Spinnaker's metric monitoring tool will publish metrics to Datadog.
	        You will be prompted for your API and APP keys unless you define
	        environment variables DATADOG_APP_KEY and DATADOG_API_KEY.

	   --prometheus
	        Install and configure Prometheus and Grafana Dashboard.
	        Spinnaker's metric monitoring tool will publish metrics to Prometheus.

	   --stackdriver
	        Spinnaker's metric monitoring tool will publish metrics to Stackdriver.
	        You may also need --credentials_path=<path>


	<monitor_options> zero or more of:
	   --port=8008
	        The port number to use for the embedded HTTP server within the monitor.

	   --period=60
	        Number of seconds between pollings of microservices.

	   --service_hosts <ip>+
	        A comma-delimited list of hostnames (or IPs) to poll by default.
	        The default is localhost.
	        Make this empty "" to not poll any services by default.


	<service_options> are in the form --<service>=<netloc>* where:
	    <service> is one of clouddriver, echo, fiat, front50, gate, igor, rosco
	    <netloc> is a comma-delimited list of either a <host> or <host>:<port>
	    If only a <host> is provided, then the dfeault port will be used.

	    An empty <netloc> list will disable polling on the service entirely.
	    A value of "*" refers to all the --service_hosts.
	    The default <netloc> for each of the services is "*".
	   

	<provider_options> are zero or more of:
	    --credentials_path=<path>
	        If using --stackdriver, the path for the Google Credentials to use.
	        The default will be the application default credentials.
EOF
}


function process_args() {
  while [[ $# > 0 ]]
  do
      local key="$1"
      shift
      case $key in
          --datadog)
              USE_DATADOG=true
              PROVIDERS="$PROVIDERS --datadog"
              ;;

          --prometheus)
              USE_PROMETHEUS=true
              PROVIDERS="$PROVIDERS --prometheus"
              ;;

          --stackdriver)
              USE_STACKDRIVER=true
              PROVIDERS="$PROVIDERS --stackdriver"
              ;;

          --help|-h)
              print_usage
              exit 1
              ;;

          *)
              ;;  # ignore

      esac
  done
}


function install_dependencies() {
  apt-get update
  apt-get install python-pip python-dev -y
  pip install -r $SOURCE_DIR/requirements.txt
}


function install_metric_services() {
  if [[ "$USE_DATADOG" == "true" ]]; then
      $SOURCE_DIR/config/datadog/install.sh
  fi
  if [[ "$USE_PROMETHEUS" == "true" ]]; then
      $SOURCE_DIR/config/prometheus/install.sh
  fi
  if [[ "$USE_STACKDRIVER" == "true" ]]; then
      local credentials=""
      for arg in ${COMMAND_LINE_FLAGS[@]}; do
          if [[ $arg = --credentials_path=* ]]; then
              credentials=$arg
          fi
      done
      $SOURCE_DIR/config/stackdriver/install.sh $credentials
  fi
}


function write_startup_script() {
  cat <<-EOF > "$SOURCE_DIR/monitor_spinnaker.sh"
	#!/bin/bash

	set -o allexport
	if [[ -f /etc/default/spinnaker ]]; then
	  source /etc/default/spinnaker
	fi
	if [[ -f "$SOURCE_DIR/environ" ]]; then
	  source "$SOURCE_DIR/environ"
	fi
	set +o allexport

	PYTHONWARNINGS=once \
	python "$SOURCE_DIR/monitor_spinnaker.zip" \
	monitor $@ "\$@"
EOF
  chmod 755 "$SOURCE_DIR/monitor_spinnaker.sh"
}


function write_upstart_script() {
  cat <<-EOF > /etc/init/monitor_spinnaker.conf
	start on filesystem or runlevel [2345]

	exec $SOURCE_DIR/monitor_spinnaker.sh > /var/log/monitor_spinnaker.log 2>&1
EOF
  chmod 644 /etc/init/monitor_spinnaker.conf
}


process_args "${COMMAND_LINE_FLAGS[@]}"
if [[ "$PROVIDERS" == "" ]]; then
  print_usage
  echo ""
  echo "ERROR: No <provider_switch> options were provided."
  exit -1
fi


if [[ `/usr/bin/id -u` -ne 0 ]]; then
  echo "$0 must be executed with root permissions; exiting"
  exit 1
fi

install_dependencies
install_metric_services
write_startup_script "${COMMAND_LINE_FLAGS[@]}"
write_upstart_script

echo "Starting to monitor Spinnaker services..."
service monitor_spinnaker start

cat <<EOF


Be sure that your spinnaker-local.yml has services.spectator.webEndpoint.enabled=true
For more information, see:
    http://www.spinnaker.io/docs/monitoring-a-spinnaker-deployment
EOF






