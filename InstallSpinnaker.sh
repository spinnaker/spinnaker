#!/bin/bash

REPOSITORY_URL="https://dl.bintray.com/spinnaker/debians"

## This script install pre-requisites for Spinnaker
# To you put this file in the root of a web server
# curl -L https://foo.com/InstallSpinnaker.sh| sudo bash

# We can only currently support limited releases
# First guess what sort of operating system

# must have root perms
if [[ `/usr/bin/id -u` -ne 0 ]];then
  echo "$0 must be executed with root permissions; exiting"
  exit 1
fi

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
  echo "Version is $DISTRIB_RELEASE we require 14.04 or higher"
  exit 1
  fi
else
  echo "Not a supported operating system"
  echo "Recommend you use Ubuntu 14.04 or higher"
  exit 1
fi

function print_usage() {
  cat <<EOF
usage: $0 [--cloud_provider <aws|google|none|both>]
    [--aws_region <region>] [--google_region <region>]
    [--quiet] [--dependencies_only]
    [--repository <debian repository url>]
    [--local-install] [--home_dir <path>]


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

    --repository <url>          Obtain Spinnaker packages from the <url>
                                rather than the default repository, which is
                                $REPOSITORY_URL

    --dependencies_only         Do not install any Spinnaker services.
                                Only install the dependencies. This is intended
                                for development scenarios only

    --local-install             For Spinnaker and Java packages, download
                                packages and install using dpkg instead of
                                apt. Use this option only if you are having
                                issues with the bintray repositories.
                                If you use this option you must manually
                                install openjdk-8-jdk.

    --home_dir                  Override where user home directories reside
                                example: /export/home vs /home
EOF
}

function echo_status() {
  if [ "x$QUIET" != "xtrue" ]; then
      echo "$@"
  fi
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
      --dependencies_only)
          CLOUD_PROVIDER="none"
          DEPENDENCIES_ONLY=true
          ;;
      --local-install)
          DOWNLOAD="true"
          ;;
      --quiet|-q)
          QUIET="true"
          CLOUD_PROVIDER="none"
          AWS_REGION="none"
          GOOGLE_REGION="none"
          GOOGLE_ZONE="none"
          shift
          ;;
      --home_dir)
          homebase="$1"
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

AWS_METADATA_URL="http://169.254.169.254/latest/meta-data"
function get_aws_metadata_value() {
  local path="$1"
  local value=$(curl -s -f $AWS_METADATA_URL/$path)

  if [[ $? -eq 0 ]]; then
    echo "$value"
  else
    echo ""
  fi
}

function write_default_value() {
  local name="$1"
  local value="$2"

  if egrep "^$name=" /etc/default/spinnaker > /dev/null; then
      sed -i.bak "s/^$name=.*/$name=$value/" /etc/default/spinnaker
  else
      bash -c "echo $name=$value >> /etc/default/spinnaker"
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
  local zone=$(get_aws_metadata_value "/placement/availability-zone")
  local region=${zone%?}
  local mac_addr=$(get_aws_metadata_value "/network/interfaces/macs/")
  local vpc_id=$(get_aws_metadata_value "/network/interfaces/macs/${mac_addr}vpc-id")
  local subnet_id=$(get_aws_metadata_value "/network/interfaces/macs/${mac_addr}subnet-id")

  DEFAULT_CLOUD_PROVIDER="aws"
  DEFAULT_AWS_REGION="$region"
  AWS_VPC_ID="$vpc_id"
  AWS_SUBNET_ID="$subnet_id"
}

function set_defaults_from_environ() {
  local on_platform=""
  local google_project_id=$(get_google_metadata_value "/project/project-id")

  if [[ -n "$google_project_id" ]]; then
      on_platform="google"
      set_google_defaults_from_environ
  fi


  local aws_az=$(get_aws_metadata_value "/placement/availability-zone")

  if [[ -n "$aws_az" ]]; then
      on_platform="aws"
      set_aws_defaults_from_environ
  fi

  if [[ "$on_platform" != "" ]]; then
      echo "Determined that you are running on $on_platform infrastructure."
  else
      echo "No providers are enabled by default."
  fi
}

function add_apt_repositories() {
  # Redis
  # https://launchpad.net/~chris-lea/+archive/ubuntu/redis-server
  add-apt-repository -y ppa:chris-lea/redis-server
  # Cassandra
  # http://docs.datastax.com/en/cassandra/2.1/cassandra/install/installDeb_t.html
  curl -L http://debian.datastax.com/debian/repo_key | apt-key add -
  echo "deb http://debian.datastax.com/community/ stable main" | tee /etc/apt/sources.list.d/datastax.list > /dev/null

  # Spinnaker
  # DL Repo goes here
  REPOSITORY_HOST=$(echo $REPOSITORY_URL | cut -d/ -f3)
  if [ "$REPOSITORY_HOST" == "dl.bintray.com" ]; then
    REPOSITORY_ORG=$(echo $REPOSITORY_URL | cut -d/ -f4)
    curl "https://bintray.com/user/downloadSubjectPublicKey?username=$REPOSITORY_ORG" | apt-key add -
  fi
  echo "deb $REPOSITORY_URL $DISTRIB_CODENAME spinnaker" | tee /etc/apt/sources.list.d/spinnaker-dev.list > /dev/null
  # Java 8
  # https://launchpad.net/~openjdk-r/+archive/ubuntu/ppa
  add-apt-repository -y ppa:openjdk-r/ppa
  apt-get update
}

function install_java() {
  if [ "$DOWNLOAD" != "true" ];then
    apt-get install -y --force-yes openjdk-8-jdk
  elif [[ "x`java -version 2>&1|head -1`" != *"1.8.0"* ]];then
    echo "you must manually install java 8 and then rerun this script; exiting"
    exit 13
  fi
}

function install_dependencies() {
  # java
  if [ "$DOWNLOAD" != "true" ];then
    apt-get install -y --force-yes redis-server apache2 unzip
  else
    # these are for cassandra only
    # if download is truee then neither redis or apache2 are installed
    # this is dirty hackish and hard coded
    mkdir /tmp/deppkgs && pushd /tmp/deppkgs
    curl -L -O http://mirrors.kernel.org/ubuntu/pool/main/a/autogen/libopts25_5.18-2ubuntu2_amd64.deb
    curl -L -O http://security.ubuntu.com/ubuntu/pool/main/n/ntp/ntp_4.2.6.p5+dfsg-3ubuntu2.14.04.5_amd64.deb
    curl -L -O http://mirrors.kernel.org/ubuntu/pool/universe/p/python-support/python-support_1.0.15_all.deb
    curl -L -O http://security.ubuntu.com/ubuntu/pool/main/u/unzip/unzip_6.0-9ubuntu1.5_amd64.deb
    dpkg -i *.deb
    popd
    rm -rf /tmp/deppkgs
  fi
}

function install_cassandra() {
  # "service cassandra status" is currently broken in Ubuntu grep in the script is grepping for things that do not exist
  # Cassandra 2.x can ship with RPC disabeld to enable run "nodetool enablethrift"

  local package_url="http://debian.datastax.com/community/pool"

  if [[ "x`java -version 2>&1|head -1`" != *"1.8.0"* ]];then
    cat <<EOF
java 8 is not installed, cannot install cassandra

after installing java 8 run the following to install cassandra:

sudo apt-get install -y --force-yes cassandra=2.1.11 cassandra-tools=2.1.11
sudo apt-mark hold cassandra cassandra-tools

EOF
    exit 13
  fi

  if [ "$DOWNLOAD" == "true" ];then
    mkdir /tmp/casspkgs && pushd /tmp/casspkgs
    for pkg in cassandra cassandra-tools;do
      curl -L -O $package_url/${pkg}_2.1.11_all.deb
    done
    dpkg -i *.deb
    apt-mark hold cassandra cassandra-tools
    popd
    rm -rf /tmp/casspkgs
  else
    apt-get install -y --force-yes cassandra=2.1.11 cassandra-tools=2.1.11
    apt-mark hold cassandra cassandra-tools
    sleep 1
  fi

  # Let cassandra start
  if ! nc -z localhost 7199; then
    echo_status "Waiting for Cassandra to start..."
    count=0
    while ! nc -z localhost 7199; do
      sleep 1
      count=`expr $count + 1`
      if [ $count -eq 30  ];then
        break
      fi
    done
    if ! nc -z localhost 7199; then
      echo "Cassandra has failed to start; exiting"
      exit 13
    else
      echo_status "Cassandra is ready."
    fi
  fi

  while ! $(nodetool enablethrift >& /dev/null); do
    sleep 1
    echo_status "Retrying..."
  done
}

function install_spinnaker() {
  if [ "$DOWNLOAD" != "true" ];then
    apt-get install -y --force-yes --allow-unauthenticated spinnaker
  else
    install_packages="spinnaker-clouddriver spinnaker-deck spinnaker-echo spinnaker-front50 spinnaker-gate spinnaker-igor spinnaker-orca spinnaker-rosco spinnaker-rush spinnaker"
    for package in $install_packages;do
      latest=`curl $REPOSITORY_URL/dists/$DISTRIB_CODENAME/spinnaker/binary-amd64/Packages | grep "/$package/${package}_" | grep Filename | awk '{print $2}' | sort -t. -k 1,1n -k 2,2n -k 3,3n | tail -1`
      debfile=`echo $latest | awk -F "/" '{print $NF}'`
      curl -L -o /tmp/$debfile $REPOSITORY_URL/$latest
      dpkg -i /tmp/$debfile && rm -f /tmp/$debfile
    done
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


# add all apt repositories, if
if [ "$DOWNLOAD" != "true" ];then
  add_apt_repositories
fi

## Packer
mkdir /tmp/packer && pushd /tmp/packer
curl -L -O https://releases.hashicorp.com/packer/0.8.6/packer_0.8.6_linux_amd64.zip
unzip -q packer_0.8.6_linux_amd64.zip -d /usr/bin
popd
rm -rf /tmp/packer

install_java
install_dependencies
install_cassandra

if [[ "x$DEPENDENCIES_ONLY" != "x" ]]; then
    exit 0
fi

## Spinnaker
install_spinnaker


if [[ "${CLOUD_PROVIDER,,}" == "amazon" || "${CLOUD_PROVIDER,,}" == "google" || "${CLOUD_PROVIDER,,}" == "both" ]]; then
  case $CLOUD_PROVIDER in
    amazon)
        write_default_value "SPINNAKER_AWS_ENABLED" "true"
        write_default_value "SPINNAKER_AWS_DEFAULT_REGION" $AWS_REGION
        write_default_value "SPINNAKER_GOOGLE_ENABLED" "false"
        write_default_value "AWS_VPC_ID" $AWS_VPC_ID
        write_default_value "AWS_SUBNET_ID" $AWS_SUBNET_ID
        ;;
    google)
        write_default_value "SPINNAKER_GOOGLE_ENABLED" "true"
        write_default_value "SPINNAKER_GOOGLE_DEFAULT_REGION" $GOOGLE_REGION
        write_default_value "SPINNAKER_GOOGLE_DEFAULT_ZONE" $GOOGLE_ZONE
        write_default_value "SPINNAKER_AWS_ENABLED" "false"
        ;;
    both)
        write_default_value "SPINNAKER_AWS_ENABLED" "true"
        write_default_value "SPINNAKER_AWS_DEFAULT_REGION" $AWS_REGION
        write_default_value "SPINNAKER_GOOGLE_ENABLED" "true"
        write_default_value "SPINNAKER_GOOGLE_DEFAULT_REGION" $GOOGLE_REGION
        write_default_value "SPINNAKER_GOOGLE_DEFAULT_ZONE" $GOOGLE_ZONE
        write_default_value "AWS_VPC_ID" $AWS_VPC_ID
        write_default_value "AWS_SUBNET_ID" $AWS_SUBNET_ID
        ;;
  esac
else
  echo "Not enabling a cloud provider"
fi

if [ "x$GOOGLE_PROJECT_ID" != "x" ]; then
  write_default_value "SPINNAKER_GOOGLE_PROJECT_ID" $GOOGLE_PROJECT_ID
fi

## Remove
if [ "x$homebase" == "x"  ]; then
  homebase="/home"
fi

if [ -z `getent group spinnaker` ]; then
  groupadd spinnaker
fi

if [ -z `getent passwd spinnaker` ]; then
  useradd --gid spinnaker -m --home-dir $homebase/spinnaker spinnaker
fi

if [ ! -d $homebase/spinnaker ]; then
  mkdir -p $homebase/spinnaker/.aws
  chown -R spinnaker:spinnaker $homebase/spinnaker
fi
##

start spinnaker
  cat <<EOF

To stop all spinnaker subsystems:
  sudo stop spinnaker

To start all spinnaker subsystems:
  sudo start spinnaker

To configure the available cloud providers:
  Edit:   /etc/default/spinnaker
  And/Or: /opt/spinnaker/config/spinnaker-local.yml

  Next, ensure that the regions configured in deck are up-to-date:
    sudo /opt/spinnaker/bin/reconfigure_spinnaker.sh

  Lastly, restart clouddriver and rosco with:
    sudo service clouddriver restart
    sudo service rosco restart
EOF
