# Getting Started with Spinnaker

These instructions cover pulling Spinnaker from source and setting up to run locally against either an Amazon Web Services or Google Compute account.

We will clone into `$SPINNAKER_HOME` and create that as our working directory, including this repo for configuration scripts, as well as the various
service repos.

## Get the bootstrap / configuration repo

````bash
#export SPINNAKER_HOME=/path/to/your/Spinnaker/workspace
mkdir -p $SPINNAKER_HOME
cd $SPINNAKER_HOME
git clone git@github.com:spinnaker/spinnaker.git
````

## Configure your environment
Prerequisites: jdk8, redis, cassandra, packer

### MacOSX

````bash
brew install redis cassandra brew-cask packer
brew cask install java
cd $SPINNAKER_HOME
spinnaker/dev/refresh_source.sh --pull_origin --use_ssh --github_user default
````

### Debian-linux

````bash
cd $SPINNAKER_HOME
dev/install_development.sh
dev/bootstrap_dev.sh
````

## Configure Spinnaker

We will create a directory for Spinnaker configuration overrides, copy the default configuration template there, and edit to select
the appropriate cloud provider.

````bash
cd $SPINNAKER_HOME
mkdir -p $HOME/.spinnaker
cp spinnaker/config/default-spinnaker-local.yml $HOME/.spinnaker/spinnaker-local.yml
````

Edit `$HOME/.spinnaker/spinnaker-local.yml` and set the enabled option for the cloud provider(s) of your choice.

## Configure your AWS Account

If you enabled AWS for Spinnaker, there are some requirements for the AWS account:

Decide which region you want Spinnaker to index. In `$HOME/.spinnaker/spinnaker-local.yml` fill in that value in providers.aws.defaultRegion. (The default is us-east-1).

Sign into the AWS console, and select the region Spinnaker will manage.

1. Name your vpc (edit the name tag, and give it a value with no spaces or dots in the name) (e.g. defaultvpc)
2. Name your subnets (edit the name tag and name following the pattern vpcName.internal.<availabilityZone>
    - e.g. defaultvpc.internal.us-east-1a, defaultvpc.internal.us-east-1b, defaultvpc-internal.us-east-1c
3. Create an EC2 role called BaseIAMRole
    - IAM > Roles > Create New Role. Select Amazon EC2.
    - You don't have to apply any policies to this role. EC2 instances launched with Spinnaker will have this role associated.
4. Create an EC2 keyPair for connecting to your instances.
    - EC2 > Key Pairs > Create Key Pair. Name the key pair `my-aws-account-keypair` (this matches the account name in `$HOME/.spinnaker/spinnaker-local.yml`
5. Create AWS credentials for Spinnaker
    - IAM > Users > Create New Users. Enter a username.
    - Create an Access Key for the user. Save the access key and secret key into `~/.aws/credentials` as shown here: http://docs.aws.amazon.com/cli/latest/userguide/cli-chap-getting-started.html#cli-config-files. Alternatively, add the keys to `$HOME/.spinnaker/spinnaker-local.yml`
    - Edit the users Permissions. Attach a Policy to the user granting PowerUserAccess. Create an inline policy for IAM granting PassRole on the resource '*'

## Configure your Google Compute Account

TODO

## Start Spinnaker Services

````bash
cd $SPINNAKER_HOME
spinnaker/dev/run_dev.sh
````

**Note** `run_dev.sh` might get stuck waiting on a service to start. Hitting CTRL-C just stops the waiting on service it doesn't terminate the services. If it seems stuck
stop and restart run_dev.sh.

## Stop Spinnaker Services
````bash
cd $SPINNAKER_HOME
spinnaker/dev/stop_dev.sh
````

