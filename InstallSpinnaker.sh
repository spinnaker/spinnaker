#!/bin/bash

REPOSITORY_URL="https://dl.bintray.com/kenzanlabs/spinnaker"

## This script install pre-requisites for Spinnaker
# To you put this file in the root of a web server
# curl -L https://foo.com/InstallSpinnaker.sh| sudo bash

# We can only currently support limited releases
# First guess what sort of operating system

if [ -f /etc/lsb-release ]; then
  . /etc/lsb-release
  DISTRO=$DISTRIB_ID
elif [ -f /etc/debian_version ]; then
  DISTRO=Debian
  # XXX or Ubuntu
elif [ -f /etc/redhat-release ]; then
  if grep -iq cent /etc/redhat-release; then
    DISTRO="CentOS"
  elif grep -iq red /etc/redhat-release; then
    DISTRO="RedHat"
  fi

  else
    DISTRO=$(uname -s)
fi

# If not Ubuntu 14.xx.x or higher

if [ "$DISTRO" = "Ubuntu" ]; then
  if [ "${DISTRIB_RELEASE%%.*}" -ne 14 ]; then
  echo "Not a supported version of Ubuntu"
  echo "Version is $DISTRIB_RELEASE we require 14.02 or higher"
  exit 1
  fi
else
  echo "Not a supported operating system"
  echo "Recommend you use Ubuntu 14.10 or higher"
  exit 1
fi

function print_usage() {
  cat <<EOF
usage: $0 [--cloud_provider <aws|google|none|both>]
    [--aws_region <region>] [--google_region <region>]
    [--quiet]

    If run with no arguments you will be prompted for cloud provider and region

    --cloud_provider <arg>      currently supported are google, amazon and none
                                if "none" is specified you will need to edit
                                /etc/default/spinnaker manually

    --aws_region <arg>          default region for aws

    --google_region <arg>       default region for google
    --google_zone <arg>         default zone for google

    --quiet                     sets cloud provider to "none", you will need to
                                edit /etc/default/spinnaker manually
                                cannot be used with --cloud_provider

EOF
}

function process_args() {
  while [[ $# > 0 ]]
  do
    local key="$1"
    shift
    case $key in
      --cloud_provider)
          CLOUD_PROVIDER="$1"
          shift
          ;;
      --aws_region)
          AWS_REGION="$1"
          shift
          ;;
      --google_region)
          GOOGLE_REGION="$1"
          shift
          ;;
      --google_zone)
          GOOGLE_ZONE="$1"
          shift
          ;;
      --repository)
         REPOSITORY_URL="$1"
         shift
         ;;
      --quiet|-q)
          CLOUD_PROVIDER="none"
          AWS_REGION="none"
          GOOGLE_REGION="none"
          GOOGLE_ZONE="none"
          shift
          ;;
      --help|-help|-h)
          print_usage
          exit 13
          ;;
      *)
          echo "ERROR: Unknown argument '$key'"
          exit -1
    esac
  done
}

function set_aws_region() {
  if [ "x$AWS_REGION" == "x" ]; then
    if [ "x$DEFAULT_AWS_REGION" == "x" ]; then
      DEFAULT_AWS_REGION="us-west-2"
    fi

    read -e -p "Specify default aws region: " -i "$DEFAULT_AWS_REGION" AWS_REGION
    AWS_REGION=`echo $AWS_REGION | tr '[:upper:]' '[:lower:]'`
  fi
}

function set_google_region() {
  if [ "x$GOOGLE_REGION" == "x" ]; then
    if [ "x$DEFAULT_GOOGLE_REGION" == "x" ]; then
      DEFAULT_GOOGLE_REGION="us-central1"
    fi

    read -e -p "Specify default google region: " -i "$DEFAULT_GOOGLE_REGION" GOOGLE_REGION
    GOOGLE_REGION=`echo $GOOGLE_REGION | tr '[:upper:]' '[:lower:]'`
  fi
}

function set_google_zone() {
  if [ "x$GOOGLE_ZONE" == "x" ]; then
    if [ "x$DEFAULT_GOOGLE_ZONE" == "x" ]; then
      DEFAULT_GOOGLE_ZONE="us-central1-f"
    fi

    read -e -p "Specify default google zone: " -i "$DEFAULT_GOOGLE_ZONE" GOOGLE_ZONE
    GOOGLE_ZONE=`echo $GOOGLE_ZONE | tr '[:upper:]' '[:lower:]'`
  fi
}

GOOGLE_METADATA_URL="http://metadata.google.internal/computeMetadata/v1"
function get_google_metadata_value() {
  local path="$1"
  local value=$(curl -s -f -H "Metadata-Flavor: Google" \
      $GOOGLE_METADATA_URL/$path)

  if [[ $? -eq 0 ]]; then
    echo "$value"
  else
    echo ""
  fi
}

function get_aws_metadata_value() {
  # TODO (dstengle): Do aws magic here...
#  echo 'some-value'
  echo ''
}

function write_default_value() {
  local name="$1"
  local value="$2"

  if egrep "^$name=" /etc/default/spinnaker > /dev/null; then
      sudo sed -i.bak "s/^$name=.*/$name=$value/" /etc/default/spinnaker
  else
      sudo bash -c "echo $name=$value >> /etc/default/spinnaker"
  fi
}

function set_google_defaults_from_environ() {
  local project_id=$(get_google_metadata_value "project/project-id")
  local qualified_zone=$(get_google_metadata_value "instance/zone")
  local zone=$(basename $qualified_zone)
  local region=${zone%-*}

  DEFAULT_CLOUD_PROVIDER="google"
  GOOGLE_PROJECT_ID=$project_id
  DEFAULT_GOOGLE_REGION="$region"
  DEFAULT_GOOGLE_ZONE="$zone"
}

function set_aws_defaults_from_environ() {
  # TODO (dstengle): Do aws magic here...
  local region=$(get_aws_metadata_value "/other/brother/darrell")

  DEFAULT_CLOUD_PROVIDER="aws"
  DEFAULT_AWS_REGION="$region"
}

function set_defaults_from_environ() {
  local on_platform=""
  local google_project_id=$(get_google_metadata_value "/project/project-id")

  if [[ -n "$google_project_id" ]]; then
      on_platform="google"
      set_google_defaults_from_environ
  fi

  # TODO (dstengle): Do aws magic here...
  local some_aws_thing=$(get_aws_metadata_value "/some/path/maybe")

  if [[ -n "$some_aws_thing" ]]; then
      on_platform="aws"
      set_aws_defaults_from_environ
  fi

  if [[ "$on_platform" != "" ]]; then
      echo "Determined that you are running on $on_platform infrastructure."
  else
      echo "No providers are enabled by default."
  fi
}
set_defaults_from_environ

process_args "$@"

if [ "x$CLOUD_PROVIDER" == "x" ]; then
  read -e -p "Specify a cloud provider (aws|google|none|both): " -i "$DEFAULT_CLOUD_PROVIDER" CLOUD_PROVIDER
  CLOUD_PROVIDER=`echo $CLOUD_PROVIDER | tr '[:upper:]' '[:lower:]'`
fi

case $CLOUD_PROVIDER in
  a|aws|amazon)
      CLOUD_PROVIDER="amazon"
      set_aws_region
      ;;
  g|gce|google)
      CLOUD_PROVIDER="google"
      set_google_region
      set_google_zone
      ;;
  n|no|none)
      CLOUD_PROVIDER="none"
      ;;
  both|all)
      CLOUD_PROVIDER="both"
      set_aws_region
      set_google_region
      set_google_zone
      ;;
  *)
      echo "ERROR: invalid cloud provider '$CLOUD_PROVIDER'"
      print_usage
      exit -1
esac

## PPAs ##
# Add PPAs for software that is not necessarily in sync with Ubuntu releases

# Redis
# https://launchpad.net/~chris-lea/+archive/ubuntu/redis-server

sudo add-apt-repository -y ppa:chris-lea/redis-server

# Cassandra
# http://docs.datastax.com/en/cassandra/2.1/cassandra/install/installDeb_t.html

curl -L http://debian.datastax.com/debian/repo_key | sudo apt-key add -
echo "deb http://debian.datastax.com/community/ stable main" | sudo tee /etc/apt/sources.list.d/datastax.list > /dev/null

# Java 8
# https://launchpad.net/~openjdk-r/+archive/ubuntu/ppa
sudo add-apt-repository -y ppa:openjdk-r/ppa

# https://launchpad.net/~openjdk-r/+archive/ubuntu/ppa
# add-apt-repository -y ppa:webupd8team/java
# echo oracle-java8-installer shared/accepted-oracle-license-v1-1 select true | sudo /usr/bin/debconf-set-selections


# Spinnaker
# DL Repo goes here
# echo "deb http://dl.bintray.com/spinnaker/ospackages ./" > /etc/apt/sources.list.d/spinnaker.list
# echo 'deb http://jenkins.staypuft.kenzan.com:8000/ trusty main' > /etc/apt/sources.list.d/spinnaker-dev.list
echo "deb $REPOSITORY_URL trusty spinnaker" | sudo tee /etc/apt/sources.list.d/spinnaker-dev.list > /dev/null

## Install software
# "service cassandra status" is currently broken in Ubuntu grep in the script is grepping for things that do not exist
# Cassandra 2.x can ship with RPC disabeld to enable run "nodetool enablethrift"

sudo apt-get update

## Java
# apt-get install -y oracle-java8-installer
sudo apt-get install -y openjdk-8-jdk
## Cassandra
sudo apt-get install -y --force-yes cassandra=2.1.11 cassandra-tools=2.1.11
# Let cassandra start
sleep 5
nodetool enablethrift
# apt-get install dsc21

## Packer
sudo apt-get install -y unzip
wget https://releases.hashicorp.com/packer/0.8.6/packer_0.8.6_linux_amd64.zip
sudo unzip -q packer_0.8.6_linux_amd64.zip -d /usr/bin
rm -f packer_0.8.6_linux_amd64.zip

## Spinnaker
sudo apt-get install -y --force-yes --allow-unauthenticated spinnaker

if [[ "${CLOUD_PROVIDER,,}" == "amazon" || "${CLOUD_PROVIDER,,}" == "google" || "${CLOUD_PROVIDER,,}" == "both" ]]; then
  case $CLOUD_PROVIDER in
    amazon)
        write_default_value "SPINNAKER_AWS_ENABLED" "true"
        write_default_value "SPINNAKER_AWS_DEFAULT_REGION" $AWS_REGION
        write_default_value "SPINNAKER_GOOGLE_ENABLED" "false"
        ;;
    google)
        write_default_value "SPINNAKER_GOOGLE_ENABLED" "true"
        write_default_value "SPINNAKER_GOOGLE_PROJECT_ID" $GOOGLE_PROJECT_ID
        write_default_value "SPINNAKER_GOOGLE_DEFAULT_REGION" $GOOGLE_REGION
        write_default_value "SPINNAKER_GOOGLE_DEFAULT_ZONE" $GOOGLE_ZONE
        write_default_value "SPINNAKER_AWS_ENABLED" "false"
        ;;
    both)
        write_default_value "SPINNAKER_AWS_ENABLED" "true"
        write_default_value "SPINNAKER_AWS_DEFAULT_REGION" $AWS_REGION
        write_default_value "SPINNAKER_GOOGLE_ENABLED" "true"
        write_default_value "SPINNAKER_GOOGLE_PROJECT_ID" $GOOGLE_PROJECT_ID
        write_default_value "SPINNAKER_GOOGLE_DEFAULT_REGION" $GOOGLE_REGION
        write_default_value "SPINNAKER_GOOGLE_DEFAULT_ZONE" $GOOGLE_ZONE
        ;;
  esac
else
  echo "Not enabling a cloud provider"
fi

## Remove
if [ -z `getent group spinnaker` ]; then
  sudo groupadd spinnaker
fi

if [ -z `getent passwd spinnaker` ]; then
  sudo useradd --gid spinnaker -m --home-dir /home/spinnaker spinnaker
fi

if [ ! -d /home/spinnaker ]; then
  sudo mkdir -p /home/spinnaker/.aws
  sudo chown -R spinnaker:spinnaker /home/spinnaker
fi
##

sudo start spinnaker
  cat <<EOF

To stop all spinnaker subsystems:
  sudo stop spinnaker

To start all spinnaker subsystems:
  sudo start spinnaker

To modify the available cloud providers:
  Edit:   /etc/default/spinnaker
  And/Or: /opt/spinnaker/config/spinnaker-local.yml

  Then restart clouddriver and rosco with:
    sudo service clouddriver restart
    sudo service rosco restart
EOF

