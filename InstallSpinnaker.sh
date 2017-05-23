#!/bin/bash

# This script installs Spinnaker and its dependencies.
# See http://www.spinnaker.io/docs/creating-a-spinnaker-instance


set -e
set -o pipefail

REPOSITORY_URL="https://dl.bintray.com/spinnaker/debians"


# This script uses the following global variables.
AWS_ENABLED=false     # set by --cloud_providers
AZURE_ENABLED=false   # set by --cloud_providers
GOOGLE_ENABLED=false  # set by --cloud_providers

INSTALL_CASSANDRA=    # default depends on platform


# Set by --local-install, means download packages
# rather than adding additional external repositories.
DOWNLOAD=false

# Set by --quiet, wont prompt or echo status output.
QUIET=false

# Set by --dependencies only,
# Install dependencies but not spinnaker itself.
DEPENDENCIES_ONLY=false

MONITORING_CHOICES=(datadog, prometheus, stackdriver, undef)

# We can only currently support limited releases
# First guess what sort of operating system

# must have root perms
if [[ `/usr/bin/id -u` -ne 0 ]]; then
  echo "$0 must be executed with root permissions; exiting"
  exit 1
fi

if [[ -f /etc/lsb-release ]]; then
  . /etc/lsb-release
  DISTRO=$DISTRIB_ID
elif [[ -f /etc/debian_version ]]; then
  DISTRO=Debian
  # XXX or Ubuntu
elif [[ -f /etc/redhat-release ]]; then
  if grep -iq cent /etc/redhat-release; then
    DISTRO="CentOS"
  elif grep -iq red /etc/redhat-release; then
    DISTRO="RedHat"
  fi
else
  DISTRO=$(uname -s)
fi

# If not Ubuntu 14.xx.x or higher

if [[ "$DISTRO" == "Ubuntu" ]]; then
  if [[ "${DISTRIB_RELEASE%%.*}" -ne 14 ]]; then
    echo "Not a supported version of Ubuntu"
    echo "Version is $DISTRIB_RELEASE we require 14.04.x LTS"
    exit 1
  fi
else
  echo "Not a supported operating system"
  echo "Only Ubuntu 14.04.x LTS is supported"
  exit 1
fi


function print_usage() {
  cat <<EOF
usage: $0 [--cloud_provider <aws|google|azure|none>]
    [--aws_region <region>] [--google_region <region>] [--azure_region <region>]
    [--quiet] [--dependencies_only]
    [--google_cloud_logging] [--monitoring]
    [--repository <debian repository url>]
    [--local-install] [--home_dir <path>]


    If run with no arguments you will be prompted for cloud provider and region

    --cloud_provider <arg>      A space separated list of providers to enable.
                                Providers are "aws", "azure", and "google".
                                The default is "none".

    --aws_region <arg>          Default region for aws.

    --google_region <arg>       Default region for google.
    --google_zone <arg>         Default zone for google.

    --azure_region <arg>        Default region for Azure.

    --quiet                     Sets cloud provider to "none". You will need to
                                edit /etc/default/spinnaker manually
                                cannot be used with --cloud_provider.

    --repository <url>          Obtain Spinnaker packages from the <url>
                                rather than the default repository, which is
                                $REPOSITORY_URL

    --dependencies_only         Do not install any Spinnaker services.
                                Only install the dependencies. This is intended
                                for development scenarios only.

    --google_cloud_logging      Install Google Cloud Logging support. This
                                is independent of installing on Google Cloud
                                Platform, but you may require additional
                                authorization. See https://cloud.google.com/logging/docs/agent/authorization#install_private-key_authorization

    --monitoring (datadog|prometheus|stackdriver|undef)
                                Install the Spinnaker Monitoring Daemon. This
                                installs support to /opt/spinnaker-monitoring
                                and --client_only support for the given system.
                                This may not be fully configured or require
                                additional values per system. For more info, see
                                http://www.spinnaker.io/docs/monitoring-a-spinnaker-deployment

    --local-install             For Spinnaker and Java packages, download
                                packages and install using dpkg instead of
                                apt. Use this option only if you are having
                                issues with the bintray repositories.
                                If you use this option you must manually
                                install openjdk-8-jdk.

    --home_dir                  Override where user home directories reside
                                example: /export/home vs /home

    --install_cassandra         Force install of cassandra.

    --noinstall_cassandra       Do not install cassandra.
EOF
}

function echo_status() {
  if ! $QUIET; then
    echo "$@"
  fi
}

function process_provider_list() {
  local arr=($CLOUD_PROVIDER)

  for provider in "${arr[@]}" ; do
    case $provider in
      aws)
        echo "enabling aws provider"
        AWS_ENABLED=true
        ;;
      amazon)
        echo "enabling aws provider"
        AWS_ENABLED=true
        ;;

      azure)
        echo "enabling azure provider"
        AZURE_ENABLED=true
        ;;

      google)
        echo "enabling google provider"
        GOOGLE_ENABLED=true
        ;;
      gce)
        echo "enabling google provider"
        GOOGLE_ENABLED=true
        ;;

      both)
        echo "WARNING: 'both' is deprecated. Use 'aws google' instead"
        echo "enabling both aws and google provider"
        GOOGLE_ENABLED=true
        AWS_ENABLED=true
        ;;

      none)
        ;;

      *)
        echo "Error invalid cloud provider: $CLOUD_PROVIDER"
        echo "cannot continue installation; exiting."
        exit 13
    esac
  done
}

function process_args() {
  while [[ $# > 0 ]]
  do
    local key="$1"
    shift
    case $key in
      --cloud_provider)
        CLOUD_PROVIDER="$1"
        process_provider_list
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
      --azure_region)
        AZURE_REGION="$1"
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
      --google_cloud_logging)
        GOOGLE_CLOUD_LOGGING="true"
        ;;
      --monitoring)
        MONITORING="$1"
        shift
        if [[ ! ${MONITORING_CHOICES[*]} =~ "$MONITORING" ]]
        then
            echo "Unknown monitoring choice '$MONITORING'."
            exit -1
        fi
        ;;
      --dependencies_only)
        CLOUD_PROVIDER="none"
        DEPENDENCIES_ONLY=true
        ;;
      --local-install)
        DOWNLOAD=true
        ;;
      --install_cassandra)
        INSTALL_CASSANDRA=true
        ;;
      --noinstall_cassandra)
        INSTALL_CASSANDRA=false
        ;;
      --quiet|-q)
        QUIET=true
        CLOUD_PROVIDER="none"
        AWS_REGION="none"
        AZURE_REGION="none"
        GOOGLE_REGION="none"
        GOOGLE_ZONE="none"
        ;;
      --home_dir)
        homebase="$1"
        if [[ "$(basename $homebase)" == "spinnaker" ]]; then
          echo "stripping trailing 'spinnaker' from --home_dir=$homebase"
          homebase=$(dirname $homebase)
        fi
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

function prompt_if_unset() {
  local name=$1
  local default_value=$2
  local prompt=$3
  local tmp
  if [[ "${!name}" == "" ]]; then
    read -e -p "$prompt" tmp
    eval ${name}=`echo ${tmp:=$default_value} | tr '[:upper:]' '[:lower:]'`
    echo "  set ${name}=\"${!name}\""
  fi
}

function set_aws_region() {
  if [[ "$AWS_REGION" == "" ]]; then
    if [[ "$DEFAULT_AWS_REGION" == "" ]]; then
      DEFAULT_AWS_REGION="us-west-2"
    fi

    prompt_if_unset AWS_REGION "$DEFAULT_AWS_REGION" "Specify default aws region: "
  fi
}

function set_azure_region() {
  if [[ "$AZURE_REGION" == "" ]]; then
    if [[ "$DEFAULT_AZURE_REGION" == "" ]]; then
      DEFAULT_AZURE_REGION="westus"
    fi

    prompt_if_unset AZURE_REGION "$DEFAULT_AZURE_REGION" "Specify default azure region (westus, centralus, eastus, eastus2): "
  fi
}

function set_google_region() {
  if [[ "$GOOGLE_REGION" == "" ]]; then
    if [[ "$DEFAULT_GOOGLE_REGION" == "" ]]; then
      DEFAULT_GOOGLE_REGION="us-central1"
    fi

    prompt_if_unset GOOGLE_REGION "$DEFAULT_GOOGLE_REGION" "Specify default google region: "
  fi
}

function set_google_zone() {
  if [[ "$GOOGLE_ZONE" == "" ]]; then
    if [[ "$DEFAULT_GOOGLE_ZONE" == "" ]]; then
      DEFAULT_GOOGLE_ZONE="us-central1-f"
    fi

    prompt_if_unset GOOGLE_ZONE "$DEFAULT_GOOGLE_ZONE" "Specify default google zone: "
  fi
}

GOOGLE_METADATA_URL="http://metadata.google.internal/computeMetadata/v1"
function get_google_metadata_value() {
  local path="$1"
  local value=$(curl -L -s -f -H "Metadata-Flavor: Google" \
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
  local value=$(curl --connect-timeout 2 -s -f $AWS_METADATA_URL/$path)

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
  local google_project_id=$(get_google_metadata_value "project/project-id")

  if [[ -n "$google_project_id" ]]; then
    on_platform="google"
    set_google_defaults_from_environ

    if [[ "$INSTALL_CASSANDRA" != "true" ]]; then
        INSTALL_CASSANDRA=false
    fi
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
  curl -s -L http://debian.datastax.com/debian/repo_key | apt-key add -
  echo "deb http://debian.datastax.com/community/ stable main" | tee /etc/apt/sources.list.d/datastax.list > /dev/null

  # Spinnaker
  # DL Repo goes here
  REPOSITORY_HOST=$(echo $REPOSITORY_URL | cut -d/ -f3)
  if [[ "$REPOSITORY_HOST" == "dl.bintray.com" ]]; then
    REPOSITORY_ORG=$(echo $REPOSITORY_URL | cut -d/ -f4)
    # Personal repositories might not be signed, so conditionally check.
    gpg=""
    gpg=$(curl -s -f "https://bintray.com/user/downloadSubjectPublicKey?username=$REPOSITORY_ORG") || true
    if [[ ! -z "$gpg" ]]; then
      echo "$gpg" | apt-key add -
    fi
  fi
  echo "deb $REPOSITORY_URL $DISTRIB_CODENAME spinnaker" | tee /etc/apt/sources.list.d/spinnaker-dev.list > /dev/null
  # Java 8
  # https://launchpad.net/~openjdk-r/+archive/ubuntu/ppa
  add-apt-repository -y ppa:openjdk-r/ppa
  apt-get update ||:
}

function install_java() {
  if ! $DOWNLOAD; then
    apt-get install -y --force-yes openjdk-8-jdk

    # https://bugs.launchpad.net/ubuntu/+source/ca-certificates-java/+bug/983302
    # It seems a circular dependency was introduced on 2016-04-22 with an openjdk-8 release, where
    # the JRE relies on the ca-certificates-java package, which itself relies on the JRE. D'oh!
    # This causes the /etc/ssl/certs/java/cacerts file to never be generated, causing a startup
    # failure in Clouddriver.
    dpkg --purge --force-depends ca-certificates-java
    apt-get install ca-certificates-java
  elif [[ "x`java -version 2>&1|head -1`" != *"1.8.0"* ]]; then
    echo "you must manually install java 8 and then rerun this script; exiting"
    exit 13
  fi
}

function install_platform_dependencies() {
  local google_scopes=$(get_google_metadata_value "instance/service-accounts/default/scopes")

  if [[ -z "$google_scopes" ]]; then
    # Not on GCP
    if [[ "$GOOGLE_CLOUD_LOGGING" == "true" ]]; then
      if [[ ! -f /etc/google/auth/application_default_credentials.json ]];
      then
        echo "You may need to add Google Project Credentials."
        echo "See https://developers.google.com/identity/protocols/application-default-credentials"
      fi
    fi
  fi

  if [[ "$GOOGLE_CLOUD_LOGGING" == "true" ]]; then
    # This can be installed on any platform, so dont scope to google.
    # However, if on google, then certain scopes are required.
    # The add_google_cloud_logging script checks the scope and warns.
    curl -s -L https://raw.githubusercontent.com/spinnaker/spinnaker/master/google/google_cloud_logging/add_google_cloud_logging.sh | sudo bash
  fi
}

function install_dependencies() {
  # java
  if ! $DOWNLOAD; then
    apt-get install -y --force-yes unzip
  else
    mkdir $TEMPDIR/deppkgs && pushd $TEMPDIR/deppkgs
    curl -L -O http://mirrors.kernel.org/ubuntu/pool/main/a/autogen/libopts25_5.18-2ubuntu2_amd64.deb
    curl -L -O http://security.ubuntu.com/ubuntu/pool/main/n/ntp/ntp_4.2.6.p5+dfsg-3ubuntu2.14.04.5_amd64.deb
    curl -L -O http://mirrors.kernel.org/ubuntu/pool/universe/p/python-support/python-support_1.0.15_all.deb
    curl -L -O http://security.ubuntu.com/ubuntu/pool/main/u/unzip/unzip_6.0-9ubuntu1.5_amd64.deb
    dpkg -i *.deb
    popd
    rm -rf $TEMPDIR/deppkgs
  fi
}

function install_redis_server() {
  apt-get -q -y --force-yes install redis-server
  local apt_status=$?
  if [[ $apt_status -eq 0 ]]; then
    return
  fi

  if $DOWNLOAD && [[ $apt_status -eq 100 ]]; then
    echo "Manually downloading and installing redis-server..."
    mkdir $TEMPDIR/deppkgs && pushd $TEMPDIR/deppkgs
    curl -L -O http://mirrors.kernel.org/ubuntu/pool/universe/j/jemalloc/libjemalloc1_3.6.0-2_amd64.deb

    curl -L -O https://launchpad.net/~chris-lea/+archive/ubuntu/redis-server/+build/8914180/+files/redis-server_3.0.7-1chl1~trusty1_amd64.deb
    dpkg -i *.deb
    popd
    rm -rf $TEMPDIR/deppkgs
  else
    echo "Error installing redis-server."
    echo "cannot continue installation; exiting."
    exit 13
  fi
}

function install_apache2() {
  # If apache2 is installed, we want to do as little modification
  # as possible to the existing installation.
  if ! $(dpkg -s apache2 2>/dev/null >/dev/null)
  then
    echo "updating apt cache..." && apt-get -q update > /dev/null 2>&1 ||:
    local apt_status=`apt-get -s -y --force-yes install apache2 > /dev/null 2>&1 ; echo $?`
    if [[ $apt_status -eq 0 ]]; then
      echo "apt sources contain apache2; installing using apt-get"
      apt-get -q -y --force-yes install apache2
    elif $DOWNLOAD && [[ $apt_status -eq 100 ]]; then
      echo "no valid apache2 package found in apt sources; attempting to download debs and install locally..."
      mkdir $TEMPDIR/apache2 && pushd $TEMPDIR/apache2
      curl -L -O http://security.ubuntu.com/ubuntu/pool/main/a/apache2/apache2_2.4.7-1ubuntu4.5_amd64.deb
      curl -L -O http://security.ubuntu.com/ubuntu/pool/main/a/apache2/apache2-bin_2.4.7-1ubuntu4.5_amd64.deb
      curl -L -O http://security.ubuntu.com/ubuntu/pool/main/a/apache2/apache2-data_2.4.7-1ubuntu4.5_all.deb
      curl -L -O http://mirrors.kernel.org/ubuntu/pool/main/a/apr/libapr1_1.5.0-1_amd64.deb
      curl -L -O http://mirrors.kernel.org/ubuntu/pool/main/a/apr-util/libaprutil1_1.5.3-1_amd64.deb
      curl -L -O http://mirrors.kernel.org/ubuntu/pool/main/a/apr-util/libaprutil1-dbd-sqlite3_1.5.3-1_amd64.deb
      curl -L -O http://mirrors.kernel.org/ubuntu/pool/main/a/apr-util/libaprutil1-ldap_1.5.3-1_amd64.deb
      curl -L -O http://mirrors.kernel.org/ubuntu/pool/main/s/ssl-cert/ssl-cert_1.0.33_all.deb
      dpkg -i *.deb
      popd && rm -rf $TEMPDIR/apache2
    else
      echo "unknown error ($apt_status) occurred attempting to install apache2"
      echo "cannot continue installation; exiting"
      exit 13
    fi
    # vhosts
    if ! grep -Fxq "Listen 127.0.0.1:9000" /etc/apache2/ports.conf
    then
      sed -i "s/Listen\ 80/Listen 127.0.0.1:9000/" /etc/apache2/ports.conf
    fi
  else
    # vhosts
    if ! grep -Fxq "Listen 127.0.0.1:9000" /etc/apache2/ports.conf
    then
      echo "Listen 127.0.0.1:9000" >> /etc/apache2/ports.conf
    fi
  fi
}

function install_cassandra() {
  # "service cassandra status" is currently broken in Ubuntu grep in the script is grepping for things that do not exist
  # Cassandra 2.x can ship with RPC disabled to enable run "nodetool enablethrift"

  local package_url="http://debian.datastax.com/community/pool"

  if [[ "x`java -version 2>&1|head -1`" != *"1.8.0"* ]]; then
    cat <<EOF
java 8 is not installed, cannot install cassandra

   After installing java 8 run the following to install cassandra:

   sudo apt-get install -y --force-yes cassandra=2.1.11 cassandra-tools=2.1.11
   sudo apt-mark hold cassandra cassandra-tools

cannot continue installation; exiting
EOF
    exit 13
  fi

  local cassandra_packages=$(apt-cache search cassandra)
  if [[ -z "$cassandra_packages" ]]; then
    if $DOWNLOAD; then
      echo "cassandra not found in apt-cache, downloading from $package_url..."
      mkdir $TEMPDIR/casspkgs && pushd $TEMPDIR/casspkgs
      for pkg in cassandra cassandra-tools;do
        curl -L -O $package_url/${pkg}_2.1.11_all.deb
      done
      dpkg -i *.deb
      apt-mark hold cassandra cassandra-tools
      popd
      rm -rf $TEMPDIR/casspkgs
    else
      echo "Error installing cassandra."
      echo "cannot continue installation; exiting."
      exit 13
    fi
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
      if [[ $count -eq 30 ]]; then
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
  apt-get install -y --force-yes --allow-unauthenticated spinnaker
  local apt_status=$?
  if [[ $apt_status -ne 0 ]]; then
    if $DOWNLOAD && [[ $apt_status -eq 100 ]]; then
      install_packages="spinnaker-clouddriver spinnaker-deck spinnaker-echo spinnaker-fiat spinnaker-front50 spinnaker-gate spinnaker-igor spinnaker-orca spinnaker-rosco spinnaker_"
      for package in $install_packages;do
        latest=`curl $REPOSITORY_URL/dists/$DISTRIB_CODENAME/spinnaker/binary-amd64/Packages | grep "^Filename" | grep $package | awk '{print $2}' | awk -F'/' '{print $NF}' | sort -t. -k 1,1n -k 2,2n -k 3,3n | tail -1`
        debfile=`echo $latest | awk -F "/" '{print $NF}'`
        filelocation=`curl $REPOSITORY_URL/dists/$DISTRIB_CODENAME/spinnaker/binary-amd64/Packages | grep "^Filename" | grep $latest | awk '{print $2}'`
        curl -L -o /tmp/$debfile $REPOSITORY_URL/$filelocation
        dpkg -i /tmp/$debfile && rm -f /tmp/$debfile
      done
    else
      echo "Error installing spinnaker."
      echo "cannot continue installation; exiting."
      exit 13
    fi
  fi

  if [[ "$MONITORING" != "" ]] && [[ "$MONITORING" != "none" ]]; then
     sudo apt-get install -y --force-yes spinnaker-monitoring-daemon
     sudo apt-get install -y --force-yes spinnaker-monitoring-third-party

    if [[ "$MONITORING" != "undef" ]]; then
      sudo /opt/spinnaker-monitoring/third_party/$MONITORING/install.sh \
          --client_only
      sudo service spinnaker-monitoring restart || true
    fi
  fi
}

set_defaults_from_environ

process_args "$@"

prompt_if_unset CLOUD_PROVIDER "$DEFAULT_CLOUD_PROVIDER" "Specify a cloud provider (aws|azure|google|none): "

process_provider_list

#enable cloud provider specific settings
if $AWS_ENABLED; then
  set_aws_region
fi

if $AZURE_ENABLED; then
  set_azure_region
fi

if $GOOGLE_ENABLED; then
  set_google_region
  set_google_zone
fi


# Only add external apt repositories if we are not --local_install
if ! $DOWNLOAD; then
  add_apt_repositories
fi

TEMPDIR=$(mktemp -d installspinnaker.XXXX)

install_java
install_apache2
install_platform_dependencies
install_dependencies
install_redis_server
if [[ "$INSTALL_CASSANDRA" != "false" ]]; then
  install_cassandra
fi

## Packer
mkdir $TEMPDIR/packer && pushd $TEMPDIR/packer
curl -s -L -O https://releases.hashicorp.com/packer/0.12.1/packer_0.12.1_linux_amd64.zip
unzip -u -o -q packer_0.12.1_linux_amd64.zip -d /usr/bin
popd
rm -rf $TEMPDIR/packer

rm -rf $TEMPDIR

if $DEPENDENCIES_ONLY; then
  exit 0
fi

## Spinnaker
install_spinnaker

if [[ "$INSTALL_CASSANDRA" != "false" ]]; then
  # Touch a file to tell other scripts we installed Cassandra.
  touch /opt/spinnaker/cassandra/SPINNAKER_INSTALLED_CASSANDRA
  cqlsh -f "/opt/spinnaker/cassandra/create_echo_keyspace.cql"
  cqlsh -f "/opt/spinnaker/cassandra/create_front50_keyspace.cql"
else
  /opt/spinnaker/install/change_cassandra.sh --echo=inMemory --front50=gcs --change_defaults=true --change_local=true
fi

# Write values to /etc/default/spinnaker.
if [[ $AWS_ENABLED || $AZURE_ENABLED || $GOOGLE_ENABLED ]] ; then
  if [[ $AWS_ENABLED == true ]] ; then
    write_default_value "SPINNAKER_AWS_ENABLED" "true"
    write_default_value "SPINNAKER_AWS_DEFAULT_REGION" $AWS_REGION
    write_default_value "AWS_VPC_ID" $AWS_VPC_ID
    write_default_value "AWS_SUBNET_ID" $AWS_SUBNET_ID
  fi
  if [[ $AZURE_ENABLED == true ]] ; then
    write_default_value "SPINNAKER_AZURE_ENABLED" "true"
    write_default_value "SPINNAKER_AZURE_DEFAULT_REGION" $AZURE_REGION
  fi
  if [[ $GOOGLE_ENABLED == true ]] ; then
    write_default_value "SPINNAKER_GOOGLE_ENABLED" "true"
    write_default_value "SPINNAKER_GOOGLE_PROJECT_ID" $GOOGLE_PROJECT_ID
    write_default_value "SPINNAKER_GOOGLE_DEFAULT_REGION" $GOOGLE_REGION
    write_default_value "SPINNAKER_GOOGLE_DEFAULT_ZONE" $GOOGLE_ZONE
  fi
else
  echo "Not enabling a cloud provider"
fi

## Remove

if [[ "$homebase" == ""  ]]; then
  homebase="/home"
  echo "Setting spinnaker home to $homebase"
fi

if [[ -z `getent group spinnaker` ]]; then
  groupadd spinnaker
fi

if [[ -z `getent passwd spinnaker` ]]; then
  useradd --gid spinnaker -m --home-dir $homebase/spinnaker spinnaker
fi

if [[ ! -d $homebase/spinnaker ]]; then
  mkdir -p $homebase/spinnaker/.aws
  chown -R spinnaker:spinnaker $homebase/spinnaker
fi
##

start spinnaker

if ! $QUIET; then
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
fi
