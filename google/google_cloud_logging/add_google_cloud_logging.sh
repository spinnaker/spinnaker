#!/bin/bash
# This should install Google Cloud Logging to the current instance
# and add a custom Spinnaker configuration to it in addition to the standard ones.
#
# This requires that the current instance have scope for
#    https://www.googleapis.com/auth/logging.write.
#
# If you do not then you are hosed and need to create a new instance because scopes
# are only introduced at construction time.
#
# If you are very much tied to this instance, you can detatch the disk, destroy
# the instance, turn the disk into a new image, then create a new instance off that
# image. Otherwise, just create a new instance.
#
# Since logging.write is added by default, you might have created this instance using
# an older C2D or spinnaker/dev/create_google_dev_vm.sh. Each of these has been updated
# to add logging-write so you can use the same procedure as before, but with the
# current versions.

GOOGLE_METADATA_URL="http://metadata.google.internal/computeMetadata/v1"
SPINNAKER_CONF_PATH=$(readlink -f "$(dirname $0)/spinnaker.conf")

scopes=$(curl -s -L -H "Metadata-Flavor:Google" "$GOOGLE_METADATA_URL/instance/service-accounts/default/scopes")
if [[ $? -eq 0 ]] && [[ $scopes != *"logging.write"* ]]; then
  echo "ERROR - missing scope 'https://www.googleapis.com/auth/logging.write'"
  echo "---------------------------------------------------------------------"
  echo "Have scopes:"
  echo "$scopes"
  echo ""
  echo "You will need to create a new VM that adds the scope for"
  echo "   https://www.googleapis.com/auth/logging.write"
  echo "then try this script again (in the new instance!)."
  exit -1
fi

cd /tmp
mkdir -p /etc/google-fluentd/config.d
sudo cp "$SPINNAKER_CONF_PATH" /etc/google-fluentd/config.d/spinnaker.conf

curl -s -O https://dl.google.com/cloudagents/install-logging-agent.sh
chmod +x ./install-logging-agent.sh
sudo ./install-logging-agent.sh
rm ./install-logging-agent.sh


if [[ -z $scopes ]]; then
  # Instructions when we are not on Google Cloud Platform
  echo ""
  echo "See https://cloud.google.com/logging/docs/agent/authorization#install_private-key_authorization for information about authenticating and activating the Google Cloud Logging agent."
fi
