# AWS UserData

## What is userdata

See the [AWS EC2 documentation on user data](http://docs.aws.amazon.com/AWSEC2/latest/UserGuide/ec2-instance-metadata.html)

## How to use

`clouddriver-aws` supports the ability to inject userdata into generated LaunchConfigurations. The mechanism is via a template file that is token replaced to provide some specifics about the deployment.

The location of the template file is controlled by the `udf.udfRoot` property and the behaviour is controlled by the `udf.defaultLegacyUdf` property. The defaults are:

````yaml
udf:
  udfRoot: /apps/nflx-udf
  defaultLegacyUdf: true
````

You almost certainly want to change `udf.defaultLegacyUdf=false`, and possibly want to change the location on the filesystem where the template file lives to suit your deployment.

## Template file

In the `udf.udfRoot` directory, create a file called `udf0`. The contents of this file will be token replaced, base64 encoded, and set as the user-data for the LaunchConfiguration when a new Server Group is created.

The list of replacement tokens is:

token             | description
------------------|------------
`%%account%%`     | the name of the account
`%%accounttype%%` | the accountType of the account
`%%env%%`         | the environment of the account
`%%region%%`      | the deployment region
`%%group%%`       | the name of the server group
`%%autogrp%%`     | the name of the server group
`%%cluster%%`     | the name of the cluster
`%%stack%%`       | the stack component of the cluster name
`%%detail%%`      | the detail component of the cluster name
`%%launchconfig%%`| the name of the launch configuration

Typical usage would be replacing these values into a list of environment variables, and using those variables to customize behaviour based on the account/env/stack/etc.

## Example `udf0` template file

````bash
CLOUD_ACCOUNT="%%account%%"
CLOUD_ACCOUNT_TYPE="%%accounttype%%"
CLOUD_ENVIRONMENT="%%env%%"
CLOUD_SERVER_GROUP="%%group%%"
CLOUD_CLUSTER="%%cluster%%"
CLOUD_STACK="%%stack%%"
CLOUD_DETAIL="%%detail%%"
EC2_REGION="%%region%%"
````

If the server group `udf-example-cluster-v001` was deployed using this template in the account `main`, accountType `streaming`, environment `prod`, in the `us-east-1` region, the resulting user data would look like:

````bash
CLOUD_ACCOUNT="main"
CLOUD_ACCOUNT_TYPE="streaming"
CLOUD_ENVIRONMENT="prod"
CLOUD_SERVER_GROUP="udf-example-cluster-v001"
CLOUD_CLUSTER="udf-example-cluster"
CLOUD_STACK="example"
CLOUD_DETAIL="cluster"
EC2_REGION="us-east-1"
````

# Customizing user data per deploy

The AWS create/clone server group operations support a `base64UserData` attribute which is appended to any existing template to allow any custom user data to be injected during a deployment.
