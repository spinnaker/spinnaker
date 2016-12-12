#!/bin/bash

# Note: for instances running in GCP only.
#
# This script is meant to be run by any instance either joining the consul 
# network, or bootstrapping a consul network. It does so by first looking for
# an explicit network to join in the instance metadata under "consul-members",
# and if it doesn't find any, it will attempt to join other instances in the
# same instance group.

METADATA_URL="http://metadata.google.internal/computeMetadata/v1"
INSTANCE_METADATA_URL="$METADATA_URL/instance"
INSTANCE_LIST_METADATA="consul-members"

function get_instance_metadata_attribute() {
  local name="$1"
  local value=$(curl -s -f -H "Metadata-Flavor: Google" \
                     $INSTANCE_METADATA_URL/attributes/$name)
  if [[ $? -eq 0 ]]; then
    echo "$value"
  else
    echo ""
  fi
}

function get_instance_list_from_metadata() {
  get_instance_metadata_attribute $INSTANCE_LIST_METADATA
}

function get_instance_list_from_mig() {
  # projects/<projectid>/zones/<zone>/instanceGroupManagers/<igm>
  createduri=$(get_instance_metadata_attribute "created-by")

  if [ -z $createduri ]
  then
    echo "Failed to identify creator of" $HOSTNAME
    echo "Make sure it is running in a managed instance group"
    exit 1
  fi

  IFS='/' read -r -a createdlist <<< "$createduri"

  mig=${createdlist[-1]}
  zone=${createdlist[-3]}

  gcloud compute instance-groups list-instances $mig --zone $zone --format='value(instance)'
}

instances=$(get_instance_list_from_metadata)

if [ -z "$instances" ]
then
  echo "Consul instances not found in instance metadata, attempting to join managed instance group."
  instances=$(get_instance_list_from_mig)
fi

if [ -z "$instances" ]
then
  echo "Failed to retrieve consul instances" 
  exit 1
fi

consul info

while [ $? != 0 ]; do
    sleep 10
    echo "Waiting for consul to start..."
    consul info
done


consul join $instances
