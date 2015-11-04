# Getting Started with Spinnaker

These instructions cover pulling Spinnaker from source and setting up to run locally against Amazon Web Services and/or Google Cloud Platform accounts.

We will clone into `$SPINNAKER_HOME` and create that as our working directory, including this repo for configuration scripts, as well as the various
service repos.

**Note** If you are going to create a Virtual Machine in Amazon EC2 or
Google Compute Engine for your development, then a reasonable machine
type is m4.2xlarge (Amazon) or n1-standard-8 (Google). If using Google,
you will need to add "Read Write" Compute access scope when creating the
instance, and may also wish to add "Full" Storage scope to later write
releases to Google Cloud Storage buckets. The Amazon credentials are
discussed below in [Configure your AWS Account](#configure-your-aws-account).


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
spinnaker/dev/install_development.sh
spinnaker/dev/bootstrap_dev.sh
````

## Configure Spinnaker

We will create a directory for Spinnaker configuration overrides, copy the default configuration template there, and edit to select
the appropriate cloud provider(s).

````bash
cd $SPINNAKER_HOME
mkdir -p $HOME/.spinnaker
cp spinnaker/config/default-spinnaker-local.yml $HOME/.spinnaker/spinnaker-local.yml
chmod 600 $HOME/.spinnaker/spinnaker-local.yml
````

Edit `$HOME/.spinnaker/spinnaker-local.yml` and set the enabled option for the cloud provider(s) of your choice.

## Configure your AWS Account

If you enabled AWS for Spinnaker, there are some requirements for the AWS account:

Decide which region you want Spinnaker to index. In `$HOME/.spinnaker/spinnaker-local.yml` fill in that value in providers.aws.defaultRegion. (The default is us-east-1).

Sign into the AWS console, and select the region Spinnaker will manage.

1. Name your vpc (edit the name tag, and give it a value with no spaces or dots in the name) (e.g. defaultvpc)
2. Name your subnets (edit the name tag and name following the pattern vpcName.internal.\<availabilityZone>)
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

## Configure your Google Cloud Platform Account

If you enabled Google for Spinnaker, there are some requirements for the Google
project and account:

Sign into the [Google Developer's Console](https://console.developers.google.com).

1. Enable APIs in the project that Spinnaker will be managing
   - In the Google Developer's Console, select the project you wish Spinnaker
     to manage.
   - Go to the API Management page.
   - Enable the "Compute Engine" and "Compute Engine Autoscaler" APIs.
2. Add and Obtain Credentials
   - Navigate to the Credentials tab (if using the beta console, it is in API Manager).
   - Select "Service account" and create a JSON key.
   - Download this key to a file.
   - `chmod 400` the file.
   - Set the project and jsonPath for `providers.google.primaryCredentials`
     in `$HOME/.spinnaker/spinnaker-local.yml`.


## Start Spinnaker Services

````bash
cd $SPINNAKER_HOME/build
../spinnaker/dev/run_dev.sh [service]
````

If a service is provided, then just that one service will be started.
If no service is provided, then all the services will be started
(including redis and cassandra unless they are specified with a remote host).
If a service is already running (even if not yet available) then it will
not be restarted.

**Note** `run_dev.sh` might get stuck waiting on a service to start. Hitting CTRL-C just stops the waiting on service it doesn't terminate the services. If it seems stuck
stop and restart run_dev.sh.

## Stop Spinnaker Services
````bash
cd $SPINNAKER_HOME/build
../spinnaker/dev/stop_dev.sh [service]
````

If a service is provided, then just that one service will be stopped.
If no service is provided then all the spinnaker services will be stopped.
Cassandra and redis are not affected by stop_dev.sh



