# Summary

These Spinnaker integration tests depend on the [Cloud Integration Testing]
(https://github.com/google/citest) python package. You'll need to run the
setup script to install the dependencies into your environment, then can
run the tests at will.

To write new tests refer to existing tests and see
[the citest README.md](https://github.com/google/citest/blob/master/README.md)
gifor the time being, and ask questions on the [Spinnaker #dev slack channel]
(https://spinnakerteam.slack.com/messages/dev/).

# About This Subtree

This part of the repository is divided into the following parts:

Directory | Purpose
----------|--------
'tests'  | The actual Spinnaker integration tests.
'spinnaker_testing' | Support modules for the Spinnaker integration tests.
'unittests'        | Unit tests for parts of spinnaker_testing.


# Installing dependencies

These tests are written in Python. Instead of installing the dependencies
into system folders consider using something like [virtualenv]
(https://virtualenv.pypa.io/en/stable/). This is optional,
but a suggestion.


    # Install the python package manager if you dont already have it
    sudo apt-get install python-pip
    
    # Instal FFI package needed by pyopenssl
    sudo apt-get install libffi-dev libssl-dev python-dev

    # Install virtualenv tools  and setup a Virtual Environment.
    pip install virtualenv

    # Create an environment to use.
    MYENV=my-virtualenv   # to clarify documentation
    virtualenv $MYENV     # create a new environment directory

    # Use the environment for a session (in the current shell).
    source $MYENV/bin/activate  # use $MYENV in the current shell
    ...
    deactivate            # leave the virtualenv when you are done


If you are using Virtual Env, then all the remaining instructions
would be performed within the virtual environment, entered by
`source $MYENV/bin/activate` within the shell you run the commands in.

## Install Platform Dependencies
These are not yet automated. Depending on the platforms involved either
by hosting Spinnaker or being the platform the test is running against,
you may need additional tools.

Platform | Tools | Installation Command
---------|-------|---------------------
Amazon Web Services | awscli | ```sudo apt-get install -y awscli```
Microsoft Azure | az | [See instructions](https://docs.microsoft.com/cli/azure/install-azure-cli)
Google Cloud Platform | gcloud | ```curl https://sdk.cloud.google.com | bash```
Kubernetes | kubectl | [See instructions](http://kubernetes.io/docs/user-guide/prereqs/)
OpenStack | openstack | [See instructions](https://docs.openstack.org/user-guide/common/cli-install-openstack-command-line-clients.html)


## Defining Environment Variables
### OpenStack

Below mentioned environment variables are needed in order for OpenStack client to work.
 
Variable | Description
---------|------------
OS_AUTH_URL | Keystone authentication server URL. (https://identityHost:portNumber/version)
OS_PROJECT_ID | OpenStack project ID.
OS_PROJECT_NAME | OpenStack project name.
OS_USER_DOMAIN_NAME | OpenStack user domain name.
OS_USERNAME | OpenStack user name.
OS_PASSWORD | OpenStack user password.
OS_REGION_NAME | OpenStack region name.
OS_IDENTITY_API_VERSION | Keystone service endpoint version number.
OS_PROJECT_DOMAIN_NAME | OpenStack project domain name.


## Install Spinnaker citest Dependencies
citest is not yet published to a pip repository so you need to clone
the citest repository and install it from there.
```
# From another directoy, such as the sibling directory to this repository.
git clone https://github.com/google/citest.git
cd citest
pip install -r requirements.txt
```

Then come back here and install the requirements for these tests.
`Run pip install -r requirements.txt.`


# Preparing a deployment to test against
Normally these tests do not require any particular type of deployment.
The tests themselves can be run from anywhere provided they can gain
access to the microservices and backend services they need to talk to.

You can run the tests against any standard deployment. The tests will 
typically inspect your configuration and adapt themselves if possible
while providing command-line flags for remaining parameters or overrides
for what it may find.

Some tests require unique parameters, or may require certain features
enabled in order to test them, but even so do not typically dictate any
particular values beyond the feature being properly enabled in standard
ways.

2016-09-22 The documentation is sparse at this time. Currently only the
bake_and_deploy test requires special configuration, which is basically
a Jenkins job that it can trigger. Even there, the triggering information is
passed to the test via command-line arguments. Instructions for this can be
found in the test itself: [bake_and_deploy_test.py]
(https://github.com/google/citest/blob/master/spinnaker/spinnaker_system/bake_and_deploy_test.py)

## Providing access
If your deployment is firewalled in a way that the machine you are running
from cannot normally access the service endpoints then you will need to tunnel
in or use one of the other suggestions recommended for users to gain access
to the Spinnaker UI. While these tests do not use the UI, the networking
techniques are the same.

If Spinnaker is deployed on the Google Cloud Platform, the tests will
tunnel into them if you use the --gce_[project|zone|instance] parameters.
Otherwise, if you use --native_hostname then you must tunnel in (or provide
some other means).

### Providing the SSH passphrase
If spinnaker tunnels on your behalf, it will need your ssh passphrase.
Since the tests are meant to be automated, these can be implicit by
running something like ssh-agent in the shell you are running the tests from,
or you can create a file containing the passphrase and pass the path on the
command-line using --ssh_passphrase_file. If using the file, be sure to protect
it using `chmod 200`.


# Running the tests

The following parameters can be passed on the command-line using a flag
`--`*<parameter_name>*`=`*<parameter_value>*.

## Standard Parameters For Talking To Spinnaker

### Platform Independent Parameters
Flag | Description
-----|------------
native_hostname | The hostname (or IP address) for the Spinnaker endpoint.
native_port | The portnumber for the Spinnaker endpoint.


### When Spinnaker Is Deployed on GCE
When spinnaker is on GCE you can use the `native_hostname` flag, or
these custom flags which will locate the instance for you. If using the
flags below, citest will automatically attempt to tunnel into the instance
if it is not already reachable.

Flag | Description
-----|------------
gce_project | The Google Cloud Project *running spinnaker* is used to locate spinnaker on GCE.
gce_instance | The name of the Google Cloud Instance that the instance is running on.
gce_zone | The name of the Google Cloud Platform zone the instanve is in.
gce_ssh_passphrase_file | The path to the passphrase file for tunneling. Alternatively, you can run something like ssh-agent.


## Standard Parameters Controlling Created Resources
These parameters are typically obtained from the deployed Spinnaker where
possible, and if not have default values so do not need to be specified.

Flag | Description
-----|------------
test_stack | The default Spinnaker Stack to specify in test operations. Setting this could be helpful to identify where resources have come from if you have a shared environment where multiple people or accounts can be running tests independently.
test_app | The Spinnaker application name to use for the test. The tests usually set a default unique name.
managed_gce_project | The Google Cloud Platform for the project that GCE is
managing. 
test_gce_zone | The Google Cloud Platform zone you prefer to deploy test
resources into.
test_gce_region | The Google Cloud Platform region you prefer to deploy test
resources into.
test_gce_image_name | The default Google Compute Engine image name to use
when creating test instances requiring an image.
aws_iam_role | The Spinnaker IAM role name for test operations.
test_aws_zone | The Amazon Web Services zone you prefer to deploy test
resources into.
test_aws_region | The Amazon Web Services region you prefer to deploy test
resources into.
test_aws_ami | The default Amazon Web Services Machine Image name to use
when creating test instances requiring an image.
test_aws_vpc_id' | The default AWS VpcId to use when creating test resources.
test_aws_security_group_id: The default AWS SecurityGroupId to use when
creating test resources.
test_azure_rg_location | The azure location where the test resources should be created. Default to westus.
azure_storage_account_key | The key used to read the content from the Azure storage account used by Spinnaker when Spinnaker is configured to use Azure Storage for front50.
azure_storage_account_name | The name of the Azure storage account used by Spinnaker when Spinnaker is configured to use Azure Storage for front50.



### Account Information
Flag | Description
-----|------------
spinnaker_google_credentials | The name of the Spinnaker [clouddriver] account that you wish to use for Google operations. If not specified, this will use the configured primary account.
spinnaker_kubernetes_credentials |  The name of the Spinnaker [clouddriver] account that you wish to use for Kubernetes operations. If not specified, this will use the configured primary account.
spinnaker_aws_credentials |  The name of the Spinnaker [clouddriver] account that you wish to use for Amazon Web Services operations. If not specified, this will use the configured primary account.
spinnaker_os_account | The name of the Spinnaker [clouddriver] account that you wish to use for OpenStack operations. If not specified, this will use the configured primary account.
spinnaker_azure_account | The name of the Spinnaker [clouddriver] account that you wish to use for the Azure operations. If not specified, this will use the configured primary account.


## Standard Parameters For Configuring Observers
Flag | Description
-----|------------
gce_credentials_path | The path to a service account JSON credentials file used by the test to verify effects on GCE. The permissions needed on the account may vary depending on what the test is doing. You can use the same service account that you have configured spinnaker with,
aws_profile | The name of the awscli profile to use when verifying effects on AWS. The permissions needed in the profile may vary depending on what the test is doing. You can use the same AWS credentials as those you configured spinnaker to use.
os_cloud | The name of the cloud. OpenStack will look for a clouds.yaml file that contains a cloud configuration to use for authentication.
azure | For Azure, you must login with the following command on the test environment ```az login -u SPN_Client_ID -p SPN_Application_key --tenant Tenant_Name --service-principal``` prior to test execution.


## Typical Invocations

Typical tests assume that the platform running Spinnaker is independent of
the platform providing the resources that Spinnaker is managing. Therefore,
a typical invocation has three sets of commandline parameters.
One set specifies "spinnaker" things, another specifies "managed resource"
things, and lastly, additional "observer" things.

    PYTHONPATH=.:spinnaker \
    python spinnaker/tests/<fixture name>.py \
    <Talking to Spinnaker Parameters> \
    <Managing Resource Parameters> \
    <Observer Parameters>

### Talking to Spinnaker Parameters
#### Google
        --gce_project=$PROJECT \
        --gce_zone=$ZONE \
        --gce_instance=$INSTANCE \
        --gce_ssh_passphrase_file=<path to optional passphrase file>

#### Aws
        --native_hostname=$HOST
        
#### Other
        --native_hostname=$HOST


### Managed Resource Parameters

### Observer Parameters
#### Google
        --gce_credentials_path=<path to downloaded JSON credentials>

### Aws
        --aws_profile=$PROFILE

### Kubernetes
        None (when on GCE)

### OpenStack
        --os_cloud=$OS_CLOUD

### Azure
        --azure_storage_account_key=Key to access the storage account used by Spinnaker
        --azure_storage_account_name=Name of the Azure storage account used by Spinnaker

# Usage Examples

Assuming you are in the repository root directory and testing against GCE
where:

    PROJECT_ID=ewiseblatt-spinnaker-test
    INSTANCE=ewiseblatt-20150805
    ZONE=us-central1-c

then:

    PYTHONPATH=.:spinnaker python \
      spinnaker/spinnaker_system/google_kato_test.py \
      --gce_project=$PROJECT_ID \
      --gce_instance=$INSTANCE \
      --gce_zone=$ZONE \
      --gce_ssh_passphrase_file=$HOME/.ssh/google_compute_engine.passphrase

If you were testing against some "native" host:

    PYTHONPATH=.:spinnaker python \
      spinnaker/spinnaker_system/google_kato_test.py \
      --native_host=$HOSTNAME \
      --managed_gce_project=$PROJECT_ID \
      --test_gce_zone=$ZONE

Note that `google_kato_test.py` is written to specifically test managing GCE
instances regardless of where Spinnaker is running from. So you can run
it against an AWS deployment, but will still be observing changes on GCE.

An example testing managing AWS instances, with Spinnaker running on
a "native" host where:

    HOSTNAME=localhost  # IP address where spinnaker is running.
    PROFILE=test        # user creates profile for aws cli tool outside citest.
    ZONE=us-east-1a     # an AWS zone.

then:

    PYTHONPATH=.:spinnaker python \
      spinnaker/spinnaker_system/aws_kato_test.py \
      --native_host=$HOSTNAME \
      --aws_profile=$PROFILE \
      --test_aws_zone=$ZONE

Note that `aws_kato_test.py` is written to specifically test managing AWS
instances regardless of where Spinnaker is running from. So you can run
it against a GCE deployment, but will still be observing changes on AWS.

