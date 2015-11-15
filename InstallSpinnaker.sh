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

    --aws_region <arg>          default region for your chosen cloud provider

    --google_region <arg>          default region for your chosen cloud provider

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
      --repository)
         REPOSITORY_URL="$1"
         shift
         ;;
      --quiet|-q)
          CLOUD_PROVIDER="none"
          AWS_REGION="none"
          GOOGLE_REGION="none"
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
    AWS_REGION="us-west-2"
    read -e -p "specify AWS region: " -i "$AWS_REGION" AWS_REGION
  fi
  AWS_REGION=`echo $AWS_REGION | tr '[:upper:]' '[:lower:]'`
}

function set_google_region() {
  if [ "x$GOOGLE_REGION" == "x" ]; then
    GOOGLE_REGION="us-central1"
    read -e -p "specify Google region: " -i "$GOOGLE_REGION" GOOGLE_REGION
  fi
  GOOGLE_REGION=`echo $GOOGLE_REGION | tr '[:upper:]' '[:lower:]'`
}

process_args "$@"

if [ "x$CLOUD_PROVIDER" == "x" ]; then
  read -p "specify a cloud provider: (aws|google|none|both) " CLOUD_PROVIDER
  CLOUD_PROVIDER=`echo $CLOUD_PROVIDER | tr '[:upper:]' '[:lower:]'`
  set_aws_region
  set_google_region
fi

case $CLOUD_PROVIDER in
  a|aws|amazon)
      CLOUD_PROVIDER="amazon"
      ;;
  g|gce|google)
      CLOUD_PROVIDER="google"
      ;;
  n|no|none)
      CLOUD_PROVIDER="none"
      ;;
  both|all)
      CLOUD_PROVIDER="both"
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
echo "deb $REPOSITORY_URL trusty spinnaker" > /etc/apt/sources.list.d/spinnaker-dev.list

## Install software
# "service cassandra status" is currently broken in Ubuntu grep in the script is grepping for things that do not exist
# Cassandra 2.x can ship with RPC disabeld to enable run "nodetool enablethrift"

apt-get update
apt-get install -y oracle-java8-installer
apt-get install -y cassandra=2.1.11 cassandra-tools=2.1.11

# Let cassandra start
sleep 5
nodetool enablethrift
# apt-get install dsc21


apt-get install -y --force-yes --allow-unauthenticated spinnaker

if [[ "${CLOUD_PROVIDER,,}" == "amazon" || "${CLOUD_PROVIDER,,}" == "google" || "${CLOUD_PROVIDER,,}" == "both" ]]; then
  case $CLOUD_PROVIDER in
     amazon)
        sed -i.bak -e "s/SPINNAKER_AWS_ENABLED=.*$/SPINNAKER_AWS_ENABLED=true/" -e "s/SPINNAKER_AWS_DEFAULT_REGION.*$/SPINNAKER_AWS_DEFAULT_REGION=${AWS_REGION}/" \
        	-e "s/SPINNAKER_GOOGLE_ENABLED=.*$/SPINNAKER_GOOGLE_ENABLED=false/" /etc/default/spinnaker
        ;;
    google)
        sed -i.bak -e "s/SPINNAKER_GOOGLE_ENABLED=.*$/SPINNAKER_GOOGLE_ENABLED=true/" -e "s/SPINNAKER_GOOGLE_DEFAULT_REGION.*$/SPINNAKER_GOOGLE_DEFAULT_REGION=${GOOGLE_REGION}/" \
        	-e "s/SPINNAKER_AWS_ENABLED=.*$/SPINNAKER_AWS_ENABLED=false/" /etc/default/spinnaker
        ;;   amazon)
    both)
        sed -i.bak -e "s/SPINNAKER_GOOGLE_ENABLED=.*$/SPINNAKER_GOOGLE_ENABLED=true/" -e "s/SPINNAKER_GOOGLE_DEFAULT_REGION.*$/SPINNAKER_GOOGLE_DEFAULT_REGION=${GOOGLE_REGION}/" \
        	-e "s/SPINNAKER_AWS_ENABLED=.*$/SPINNAKER_AWS_ENABLED=true/"  -e "s/SPINNAKER_AWS_DEFAULT_REGION.*$/SPINNAKER_AWS_DEFAULT_REGION=${AWS_REGION}/" /etc/default/spinnaker
        ;;   amazon)
  esac
else
  echo "Not enabling a cloud provider"
fi

service clouddriver start
service orca start
service gate start
service rush start
service rosco start
service front50 start
service echo start
