 #!/bin/bash

#ssh -L 9000:127.0.0.1:80 -L 9999:127.0.0.1:9999 ubuntu@



read -e -p "enter vpc id: " -i "vpc-a6e5a5c3" VPC
read -e -p "enter subnet id: " -i "subnet-ed56219a" SUBNET
#read -e -p "enter package repo: " -i "https://dl.bintray.com/moondev/spinnaker trusty main" REPO
read -e -p "enter rosco trusty base ami: " -i "ami-5189a661" AMI
read -e -p "enter bucket name where debs will be published: " -i "spinnaker-debs" BUCKET

#read -e -p "enter jenkins address: " -i "http://127.0.0.1:9999" JENKINSADDRESS
#read -e -p "enter jenkins user: " -i "jenkins" JENKINSUSERNAME
#read -e -p "enter jenkins password: " -i "jenkins" JENKINSPASSWORD
#REPO=https://dl.bintray.com/moondev/spinnaker trusty main
REPO=https://s3-us-west-2.amazonaws.com/$BUCKET ./
JENKINSADDRESS=http://127.0.0.1:9999
JENKINSUSERNAME=jenkins
JENKINSPASSWORD=jenkins



#jenkins
wget -q -O - https://jenkins-ci.org/debian/jenkins-ci.org.key | sudo apt-key add -
sh -c 'echo deb http://pkg.jenkins-ci.org/debian binary/ > /etc/apt/sources.list.d/jenkins.list'
apt-get update -y
apt-get install jenkins -y

service jenkins stop

rm /etc/default/jenkins
touch /etc/default/jenkins

cat <<EOT >> /etc/default/jenkins
NAME=jenkins
JAVA=/usr/bin/java
JAVA_ARGS="-Djava.awt.headless=true"  # Allow graphs etc. to work even when an X server is present
PIDFILE=/var/run/\$NAME/\$NAME.pid
JENKINS_USER=\$NAME
JENKINS_GROUP=\$NAME
JENKINS_WAR=/usr/share/\$NAME/\$NAME.war
JENKINS_HOME=/var/lib/\$NAME
RUN_STANDALONE=true
JENKINS_LOG=/var/log/\$NAME/\$NAME.log
MAXOPENFILES=8192
AJP_PORT=-1
PREFIX=/\$NAME
HTTP_PORT=9999
JENKINS_ARGS="--webroot=/var/cache/\$NAME/war --httpPort=\$HTTP_PORT --ajp13Port=\$AJP_PORT"
EOT

rm /var/lib/jenkins/config.xml
touch /var/lib/jenkins/config.xml

cat <<EOT >> /var/lib/jenkins/config.xml
<?xml version='1.0' encoding='UTF-8'?>
<hudson>
  <disabledAdministrativeMonitors/>
  <version>1.0</version>
  <numExecutors>2</numExecutors>
  <mode>NORMAL</mode>
  <useSecurity>true</useSecurity>
  <authorizationStrategy class="hudson.security.AuthorizationStrategy\$Unsecured"/>
  <securityRealm class="hudson.security.HudsonPrivateSecurityRealm">
    <disableSignup>false</disableSignup>
    <enableCaptcha>false</enableCaptcha>
  </securityRealm>
  <disableRememberMe>false</disableRememberMe>
  <projectNamingStrategy class="jenkins.model.ProjectNamingStrategy$DefaultProjectNamingStrategy"/>
  <workspaceDir>\${JENKINS_HOME}/workspace/\${ITEM_FULLNAME}</workspaceDir>
  <buildsDir>\${ITEM_ROOTDIR}/builds</buildsDir>
  <markupFormatter class="hudson.markup.EscapedMarkupFormatter"/>
  <jdks/>
  <viewsTabBar class="hudson.views.DefaultViewsTabBar"/>
  <myViewsTabBar class="hudson.views.DefaultMyViewsTabBar"/>
  <clouds/>
  <scmCheckoutRetryCount>0</scmCheckoutRetryCount>
  <views>
    <hudson.model.AllView>
      <owner class="hudson" reference="../../.."/>
      <name>All</name>
      <filterExecutors>false</filterExecutors>
      <filterQueue>false</filterQueue>
      <properties class="hudson.model.View\$PropertyList"/>
    </hudson.model.AllView>
  </views>
  <primaryView>All</primaryView>
  <slaveAgentPort>50000</slaveAgentPort>
  <label></label>
  <nodeProperties/>
  <globalNodeProperties/>
</hudson>
EOT

chown jenkins:jenkins /var/lib/jenkins/config.xml

service jenkins start
sleep 10
curl -O http://localhost:9999/api/json
sleep 10
content=$(curl -L http://localhost:9999/api/json)
echo $content

sleep 10

curl -O http://localhost:9999/jnlpJars/jenkins-cli.jar

sleep 10

echo 'jenkins.model.Jenkins.instance.securityRealm.createAccount("jenkins", "jenkins")' | java -jar jenkins-cli.jar -s http://127.0.0.1:9999/ groovy =

#APTLY REPLACEMENT
#apt-get install dpkg-dev -y
#mkdir /var/www/repo
#cp deck_2.352-3_all.deb /var/www/repo/deck_2.352-3_all.deb
#cd /var/www/repo
#dpkg-scanpackages . /dev/null | gzip -9c > Packages.gz
#deb file:/usr/local/mydebs ./

apt-get install libgdbm-dev libncurses5-dev automake libtool bison libffi-dev sudo apt-get install build-essential ruby2.0-dev -y
sudo gpg --keyserver hkp://keys.gnupg.net --recv-keys 409B6B1796C275462A1703113804BB82D39DC0E3
curl -L https://get.rvm.io | bash -s stable
source /etc/profile.d/rvm.sh
rvm install 2.2.3
rvm use 2.2.3 --default
ruby -v
gem install bundler
#gem install deb-s3

#deb-s3 upload --bucket my-bucket my-deb-package-1.0.0_amd64.deb

docker run -d -p 5000:5000 --name registry registry:2

rm /var/www/settings.js
touch /var/www/settings.js

cat <<EOT >> /var/www/settings.js
webpackJsonp([1,2],[
/* 0 */
/***/ function(module, exports) {

	'use strict';

	/**
	 * This section is managed by scripts/reconfigure_spinnaker.sh
	 * If hand-editing, only add comment lines that look like
	 * '// var VARIABLE = VALUE'
	 * and let scripts/reconfigure manage the actual values.
	 */
	// BEGIN reconfigure_spinnaker

	// var gateUrl = \${services.gate.baseUrl};
	var gateUrl = '/gate';
	// var bakeryBaseUrl = \${services.bakery.baseUrl};
	var bakeryBaseUrl = '/bakery';
	// var awsDefaultRegion = \${providers.aws.defaultRegion};
	var awsDefaultRegion = 'us-west-2';
	// var awsPrimaryAccount = \${providers.aws.primaryCredentials.name};
	var awsPrimaryAccount = 'my-aws-account';
	// var googleDefaultRegion = \${providers.google.defaultRegion};
	var googleDefaultRegion = 'us-central1';
	// var googleDefaultZone = \${providers.google.defaultZone};
	var googleDefaultZone = 'us-central1-f';
	// var googlePrimaryAccount = \${providers.google.primaryCredentials.name};
	var googlePrimaryAccount = 'my-google-account';

	// END reconfigure_spinnaker
	/**
	 * Any additional custom var statements can go below without
	 * being affected by scripts/reconfigure_spinnaker.sh
	 */

	window.spinnakerSettings = {
	  gateUrl: '' + gateUrl,
	  bakeryDetailUrl: bakeryBaseUrl + '/api/v1/global/logs/{{context.status.id}}?html=true',
	  pollSchedule: 30000,
	  defaultTimeZone: 'America/New_York', // see http://momentjs.com/timezone/docs/#/data-utilities/
	  providers: {
	    gce: {
	      defaults: {
	        account: '' + googlePrimaryAccount,
	        region: '' + googleDefaultRegion,
	        zone: '' + googleDefaultZone
	      }
	    },
	    aws: {
	      defaults: {
	        account: '' + awsPrimaryAccount,
	        region: '' + awsDefaultRegion
	      }
	    }
	  },
	  authEnabled: false,
	  feature: {
	    pipelines: true,
	    notifications: false,
	  }
	};

/***/ }
]);
EOT

service docker start
docker run -d -p 5000:5000 --restart=always --name registry registry:2

#build image

cd /opt/rosco/docker

rm Dockerfile
touch Dockerfile

cat <<EOT >> Dockerfile
FROM jpetazzo/dind
# There is a known issue with later versions of docker requiring us to stick with this version for now: https://github.com/mitchellh/packer/issues/1752
RUN sudo apt-get update -y
#RUN sudo apt-get install -q -y lxc-docker-1.3.3
# The REPLACE_THIS_WITH_DOCKER_REGISTRY_HOST token on the next line should be replaced prior to running docker build.
##RUN echo 'DOCKER_OPTS="$DOCKER_OPTS --insecure-registry 172.30.0.179:5000"' >> /etc/default/docker
RUN echo 'debconf debconf/frontend select Noninteractive' | debconf-set-selections
RUN sudo apt-get update -q -y
RUN sudo apt-get install -q -y wget
RUN sudo apt-get install -q -y unzip
WORKDIR /tmp
RUN sudo wget -q https://dl.bintray.com/mitchellh/packer/packer_0.7.5_linux_amd64.zip
RUN sudo mkdir /usr/local/packer
WORKDIR /usr/local/packer
RUN sudo unzip /tmp/packer_0.7.5_linux_amd64.zip
ENV PATH /usr/local/packer/:$PATH
RUN mkdir /usr/local/rosco
COPY *.json /usr/local/rosco/
WORKDIR /usr/local/rosco
EOT

rm aws-ebs.json
touch aws-ebs.json

cat <<EOT >> aws-ebs.json
{
  "variables": {
    "aws_access_key": "",
    "aws_secret_key": "",
    "aws_region": null,
    "aws_ssh_username": null,
    "aws_instance_type": null,
    "aws_source_ami": null,
    "aws_target_ami": null,
    "appversion": "",
    "build_host": "",
    "deb_repo": null,
    "packages": ""
  },
  "builders": [{
    "type": "amazon-ebs",
    "access_key": "{{user \`aws_access_key\`}}",
    "secret_key": "{{user \`aws_secret_key\`}}",
    "region": "{{user \`aws_region\`}}",
    "source_ami": "$AMI",
    "instance_type": "{{user \`aws_instance_type\`}}",
    "ssh_username": "{{user \`aws_ssh_username\`}}",
    "ami_name": "{{user \`aws_target_ami\`}}",
    "vpc_id": "$VPC",
    "subnet_id": "$SUBNET",
    "tags": {"appversion": "{{user \`appversion\`}}", "build_host": "{{user \`build_host\`}}"}
  }],
  "provisioners": [{
    "type": "shell",
    "inline": [
      "sleep 30",
      "echo \\"deb $REPO\\" | sudo tee /etc/apt/sources.list.d/spinnaker.list > /dev/null",
      "sudo apt-get update",
      "sudo apt-get install --force-yes -y {{user \`packages\`}}"
    ]
  }]
}
EOT

DOCKER_IMAGE_NAME=spinnaker/rosco
DOCKER_REGISTRY=localhost:5000

docker build -t $DOCKER_IMAGE_NAME .
docker tag -f $DOCKER_IMAGE_NAME $DOCKER_REGISTRY/$DOCKER_IMAGE_NAME
docker push $DOCKER_REGISTRY/$DOCKER_IMAGE_NAME

service rush stop
service rush stop

rm /opt/spinnaker/config/spinnaker-local.yml
touch /opt/spinnaker/config/spinnaker-local.yml

cat <<EOT >> /opt/spinnaker/config/spinnaker-local.yml
# This file is intended to override the default configuration in the spinnaker.yml file.
# In order for Spinnaker to discover it, it must be copied to a file named
# "spinnaker-local.yml" and placed in the \$HOME/.spinnaker directory.

providers:
  aws:
    # If you want to deploy some services to Amazon Web Services (AWS),
    # set enabled and provide primary credentials for deploying.
    # Enabling AWS is independent of other providers.
    enabled: \${SPINNAKER_AWS_ENABLED}
    defaultRegion: \${SPINNAKER_AWS_DEFAULT_REGION}
    defaultIAMRole: spinnakerRole
    primaryCredentials:
      name: my-aws-account
      # You can use a standard \$HOME/.aws/credentials file instead of providing
      # these here. If provided here, then start_spinnaker will set environment
      # variables before starting up the subsystems that interact with AWS.
      access_key_id:
      secret_key:

  google:
    # If you want to deploy some services to Google Cloud Platform (google),
    # set enabled and provide primary credentials for deploying.
    # Enabling google is independent of other providers.
    enabled: false
    defaultRegion: us-central1
    defaultZone: us-central1-f
    primaryCredentials:
      name: my-google-account
      # The project is the Google Project ID for the project to manage with Spinnaker.
      # The jsonPath is a path to the JSON service credentials downloaded from the
      # Google Developer's Console.
      project:
      jsonPath:

services:
  default:
    # These defaults can be modified to change all the spinnaker subsystems
    # (clouddriver, gate, etc) at once, but not external systems (jenkins, etc).
    # Individual systems can still be overriden using their own section entry
    # directly under 'services'.
    protocol: http    # Assume all spinnaker subsystems are using http
    host: localhost   # Assume all spinnaker subsystems are on localhost
    primaryAccountName: \${providers.google.primaryCredentials.name}
    igor_enabled: true

  redis:
    # If you are using a remote redis server, you can set the host here.
    # If the remote server is on a different port or url, you can add
    # a "port" or "baseUrl" field here instead.
    host: localhost

  cassandra:
    # If you are using a remote cassandra server, you can set the host here.
    # If the remote server is on a different port or url, you can add
    # a "port" or "baseUrl" field here instead. You may also need to set
    # the cluster name. See the main spinnaker.yml file for more attributes.
    host: localhost

  docker:
    # Spinnaker's "rush" subsystem uses docker to run internal jobs.
    # Note that docker is not installed with Spinnaker so you must obtain this
    # on your own if you are interested.
    enabled: true
    baseUrl: http://127.0.0.1:7104
    # This target repository is used by the bakery to publish baked docker images.
    # Do not include http://.
    targetRepository: 127.0.0.1:5000
    # Optional, but expected in spinnaker-local.yml if specified.

    # You'll need to provide it a Spinnaker account to use.
    # Here we are assuming the default primary acccount.
    #
    # If you have multiple accounts using docker then you will need to
    # provide a rush-local.yml.
    # For more info see docker.accounts in config/rush.yml.
    primaryAccount:
      name: \${services.default.primaryAccountName}
      url:  \${services.docker.baseUrl}
      registry: \${services.dockerRegistry.baseUrl}

  dockerRegistry:
      baseUrl: http://127.0.0.1:5000

  jenkins:
    # If you are integrating Jenkins, set its location here using the baseUrl
    # field and provide the username/password credentials.
    # You must also enable the "igor" service listed seperately.
    #
    # If you have multiple jenkins servers, you will need to list
    # them in an igor-local.yml. See jenkins.masters in config/igor.yml.
    #
    # Note that jenkins is not installed with Spinnaker so you must obtain this
    # on your own if you are interested.
    defaultMaster:
      name: Jenkins # The display name for this server
      baseUrl: $JENKINSADDRESS
      username: $JENKINSUSERNAME
      password: $JENKINSPASSWORD

  igor:
    # If you are integrating Jenkins then you must also enable Spinnaker's
    # "igor" subsystem.
    enabled: true

  rush:
    # Spinnaker's "rush" subsystem is used by the "rosco" bakery.
    # You'll need to provide it a Spinnaker account to use.
    # Here we are assuming the default primary acccount.
    primaryAccount: \${services.default.primaryAccountName}
EOT

rm /opt/rosco/config/rosco.yml
touch /opt/rosco/config/rosco.yml

cat <<EOT >> /opt/rosco/config/rosco.yml
server:
  port: 8087

rush:
  baseUrl: http://localhost:8085
  credentials: my-account-name
  image: spinnaker/rosco

executionStatusToBakeStates:
  associations:
    - executionStatus: PREPARING
      bakeState: PENDING
    - executionStatus: FETCHING_IMAGE
      bakeState: PENDING
    - executionStatus: RUNNING
      bakeState: RUNNING
    - executionStatus: SUCCESSFUL
      bakeState: COMPLETED
    - executionStatus: FAILED
      bakeState: CANCELLED

executionStatusToBakeResults:
  associations:
    - executionStatus: SUCCESSFUL
      bakeResult: SUCCESS
    - executionStatus: FAILED
      bakeResult: FAILURE

debianRepository: https://dl.bintray.com/moondev/spinnaker trusty main

defaultCloudProviderType: aws

aws:
  enabled: \${AWS_ENABLED:false}
  bakeryDefaults:
    # TODO(duftler): Make aws credentials-handling consistent with that of other spinnaker modules.
    awsAccessKey: \${AWS_ACCESS_KEY:FOO}
    awsSecretKey: \${AWS_SECRET_KEY:BAR}
    templateFile: aws-ebs.json
    defaultVirtualizationType: hvm
    operatingSystemVirtualizationSettings:
    # TODO(duftler): Identify proper base amis.
    # TODO(duftler): Support additional store types beyond ebs.
    # TODO(duftler): Support additional operating systems.
    - os: ubuntu
      virtualizationSettings:
      - region: us-east-1
        virtualizationType: hvm
        instanceType: t2.micro
        sourceAmi: ami-d4aed0bc
        sshUserName: ubuntu
      - region: us-east-1
        virtualizationType: pv
        instanceType: m3.medium
        sourceAmi: ami-8007b2e8
        sshUserName: ubuntu
    - os: trusty
      virtualizationSettings:
      - region: us-east-1
        virtualizationType: hvm
        instanceType: t2.micro
        sourceAmi: ami-9eaa1cf6
        sshUserName: ubuntu
      - region: us-east-1
        virtualizationType: pv
        instanceType: m3.medium
        sourceAmi: ami-98aa1cf0
        sshUserName: ubuntu

docker:
  enabled: \${DOCKER_ENABLED:false}
  bakeryDefaults:
    targetRepository: \${DOCKER_TARGET_REPOSITORY:}
    templateFile: docker.json
    operatingSystemVirtualizationSettings:
    - os: ubuntu
      virtualizationSettings:
        sourceImage: ubuntu:precise
    - os: trusty
      virtualizationSettings:
        sourceImage: ubuntu:trusty

google:
  enabled: \${GOOGLE_ENABLED:false}
  gce:
    bakeryDefaults:
      # TODO(duftler): Parameterize the 'account_file' of the 'googlecompute' packer builder in the
      # gce.json template. At the moment, this project name must match the project associated with
      # the account.json file contained within the spinnaker/rosco image.
      project: shared-spinnaker
      zone: us-central1-a
      templateFile: gce.json
      operatingSystemVirtualizationSettings:
      - os: ubuntu
        virtualizationSettings:
          sourceImage: ubuntu-1204-precise-v20141212
      - os: trusty
        virtualizationSettings:
          sourceImage: ubuntu-1404-trusty-v20141212
EOT

service rush start
service rush start

#add jenkins just for example purposes

wget -q -O - https://jenkins-ci.org/debian/jenkins-ci.org.key | sudo apt-key add -
sh -c 'echo deb http://pkg.jenkins-ci.org/debian binary/ > /etc/apt/sources.list.d/jenkins.list'
apt-get update
apt-get install jenkins -y

echo "HTTP_PORT=9999" >> /etc/default/jenkins

service jenkins start

echo "READY TO ROCK on http://localhost:9000"