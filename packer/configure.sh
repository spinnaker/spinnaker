 #!/bin/bash

#ssh -L 9000:127.0.0.1:80 -L 9999:127.0.0.1:9999 ubuntu@

service jenkins stop

read -e -p "enter vpc id: " -i "vpc-a6e5a5c3" VPC
read -e -p "enter subnet id: " -i "subnet-ed56219a" SUBNET
#read -e -p "enter package repo: " -i "https://dl.bintray.com/moondev/spinnaker trusty main" REPO
read -e -p "enter rosco trusty base ami: " -i "ami-5189a661" AMI

#read -e -p "enter jenkins address: " -i "http://127.0.0.1:9999" JENKINSADDRESS
#read -e -p "enter jenkins user: " -i "jenkins" JENKINSUSERNAME
#read -e -p "enter jenkins password: " -i "jenkins" JENKINSPASSWORD
REPO=https://dl.bintray.com/moondev/spinnaker trusty main
JENKINSADDRESS=http://127.0.0.1:9999
JENKINSUSERNAME=jenkins
JENKINSPASSWORD=jenkins




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
  <authorizationStrategy class="hudson.security.AuthorizationStrategy$Unsecured"/>
  <securityRealm class="hudson.security.HudsonPrivateSecurityRealm">
    <disableSignup>false</disableSignup>
    <enableCaptcha>false</enableCaptcha>
  </securityRealm>
  <disableRememberMe>false</disableRememberMe>
  <projectNamingStrategy class="jenkins.model.ProjectNamingStrategy$DefaultProjectNamingStrategy"/>
  <workspaceDir>${ITEM_ROOTDIR}/workspace</workspaceDir>
  <buildsDir>${ITEM_ROOTDIR}/builds</buildsDir>
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
      <properties class="hudson.model.View$PropertyList"/>
    </hudson.model.AllView>
  </views>
  <primaryView>All</primaryView>
  <slaveAgentPort>50000</slaveAgentPort>
  <label></label>
  <nodeProperties/>
  <globalNodeProperties/>
</hudson>
EOT
service jenkins start

wget http://127.0.0.1:9999/jnlpJars/jenkins-cli.jar

echo 'jenkins.model.Jenkins.instance.securityRealm.createAccount("jenkins", "jenkins")' | java -jar jenkins-cli.jar -s http://127.0.0.1:9999/ groovy =



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
	 * '// let VARIABLE = VALUE'
	 * and let scripts/reconfigure manage the actual values.
	 */
	// BEGIN reconfigure_spinnaker

	// let gateUrl = \${services.gate.baseUrl};
	var gateUrl = '/gate';
	// let bakeryBaseUrl = \${services.bakery.baseUrl};
	var bakeryBaseUrl = '/bakery';
	// let awsDefaultRegion = \${providers.aws.defaultRegion};
	var awsDefaultRegion = 'us-west-2';
	// let awsPrimaryAccount = \${providers.aws.primaryCredentials.name};
	var awsPrimaryAccount = 'my-aws-account';
	// let googleDefaultRegion = \${providers.google.defaultRegion};
	var googleDefaultRegion = 'us-central1';
	// let googleDefaultZone = \${providers.google.defaultZone};
	var googleDefaultZone = 'us-central1-f';
	// let googlePrimaryAccount = \${providers.google.primaryCredentials.name};
	var googlePrimaryAccount = 'my-google-account';

	// END reconfigure_spinnaker
	/**
	 * Any additional custom let statements can go below without
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
	      },
	      primaryAccounts: ['' + googlePrimaryAccount],
	      challengeDestructiveActions: ['' + googlePrimaryAccount]
	    },
	    aws: {
	      defaults: {
	        account: '' + awsPrimaryAccount,
	        region: '' + awsDefaultRegion
	      },
	      primaryAccounts: ['' + awsPrimaryAccount],
	      primaryRegions: ['eu-west-1', 'us-east-1', 'us-west-1', 'us-west-2'],
	      challengeDestructiveActions: ['' + awsPrimaryAccount],
	      preferredZonesByAccount: {}
	    }
	  },
	  whatsNew: {
	    gistId: '32526cd608db3d811b38',
	    fileName: 'news.md'
	  },
	  authEnabled: false,
	  feature: {
	    pipelines: true,
	    notifications: false,
	    canary: false,
	    parallelPipelines: true,
	    fastProperty: false,
	    vpcMigrator: false
	  }
	};

	window.spinnakerSettings.providers.aws.preferredZonesByAccount['' + awsPrimaryAccount] = {
	  'us-east-1': ['us-east-1a', 'us-east-1b', 'us-east-1d', 'us-east-1e'],
	  'us-west-1': ['us-west-1a', 'us-west-1b', 'us-west-1c'],
	  'us-west-2': ['us-west-2a', 'us-west-2b', 'us-west-2c'],
	  'eu-west-1': ['eu-west-1a', 'eu-west-1b', 'eu-west-1c'],
	  'ap-northeast-1': ['ap-northeast-1a', 'ap-northeast-1b', 'ap-northeast-1c'],
	  'ap-southeast-1': ['ap-southeast-1a', 'ap-southeast-1b'],
	  'ap-southeast-2': ['ap-southeast-2a', 'ap-southeast-2b'],
	  'sa-east-1': ['sa-east-1a', 'sa-east-1b']
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