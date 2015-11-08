#!/bin/sh


# Apache
a2enmod proxy_http

# Copy vhosts
/opt/spinnaker/bin/reconfigure_spinnaker.sh

# Restart apache
service apache2 restart

# Install cassandra keyspaces
cqlsh -f "/opt/spinnaker/cassandra/create_echo_keyspace.cql"
cqlsh -f "/opt/spinnaker/cassandra/create_front50_keyspace.cql"
cqlsh -f "/opt/spinnaker/cassandra/create_rush_keyspace.cql"

# Start all the services

# start 'clouddriver'
# start 'orca'
# start 'front50'
# start 'rush'
# start 'rosco'
# start 'echo'
# start 'gate'
# start 'igor'


