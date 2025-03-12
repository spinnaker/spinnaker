## Configuring multiple AWS accounts

When Spinnaker is configured to manage multiple AWS accounts it uses AWS STS
assumeRole functionality to connect to and operate on each managed account.

The single set of AWS credentials used by Spinnaker connects to the managing
account. In the managing account, the AWS credentials need to have an IAM policy
that grants the ability to STS assumeRole to the target accounts.

In each managed account there needs to exist an IAM role that provides access to
all the AWS operations that Spinnaker performs against that account. That IAM role
has to have a trust relationship to the IAM ARN in the managing account (either a
user ARN if connecting with an access key, or a role ARN if authenticating with an EC2
instance profile).

As an example, we will use two accounts:
* Managing account (AWS accountId - 1234-5678-9012)
* Managed account (AWS accountId - 9876-5432-1098)

Spinnaker will connect using AWS credentials for the managing account, and will
manage resources in both the managing account and managed account.

### Configuring the Managing Account

In the managing account, we will create an IAM Policy called `SpinnakerAssumeRolePolicy`.

on `SpinnakerAssumeRolePolicy` we allow the permission to assume role into both the managing
account and managed account. Any additional accounts we configure will get added to this list.

````json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Action": "sts:AssumeRole",
      "Resource": [
        "arn:aws:iam::123456789012:role/spinnakerManaged",
        "arn:aws:iam::987654321098:role/spinnakerManaged"
      ],
      "Effect": "Allow"
    }
  ]
}
````

If we are authenticating to the managing account using an access key/secret key, then on
the IAM user, attach the `SpinnakerAssumeRolePolicy`. For example if we had a user named
`spinnaker` in the managing account (ARN `arn:aws:iam::123456789012:user/spinnaker`),
add the `SpinnakerAssumeRolePolicy` to that user.

If we are authenticating via an EC2 instance role, we would create a role in the managing
account, and attach the `SpinnakerAssumeRolePolicy` to it. For example if we had a role
named `SpinnakerInstanceProfile` in the managing account
(ARN `arn:aws:iam::123456789012:role/SpinnakerInstanceProfile`), add the
`SpinnakerAssumeRolePolicy` to that role.

### Configuring the Managed Accounts

In both the managing and each managed account we create an IAM role called `spinnakerManaged`.

On the `spinnakerManaged` role we attach a role policy that grants access to the managed accounts AWS
resources. The standard Amazon PowerUser policy provides more than the necessary permissions to
manage the account. **TODO(cfieber)** - *what is the minimum set of permissions needed against an
AWS account for Spinnaker to manage it*?

Also on the `spinnakerManaged` role, we have to grant a trust relationship back to the ARN we
are using to authenticate to AWS (either the `SpinnakerInstanceProfile` role or `spinnaker` user
in the example above).

The trust relationship needs to include the managing account credentials ARN. For example granting
trust to the `SpinnakerInstanceProfile` in the managing account:

````json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "",
      "Effect": "Allow",
      "Principal": {
        "AWS": "arn:aws:iam::123456789012:role/SpinnakerInstanceProfile"
      },
      "Action": "sts:AssumeRole"
    }
  ]
}
````

### Configuring clouddriver.yml

In the AWS config section for clouddriver, we add each of the accounts:

````yml
aws:
  enabled: true
  defaultAssumeRole: role/spinnakerManaged
  defaultRegions:
    - name: us-east-1
  accounts:
    - name: managing
      accountId: "123456789012"
      regions:
        - name: us-east-1
    - name: managed
      accountId: "987654321098"
      regions:
        - name: us-east-1
````

**NOTE:** In your yaml configuration file, ensure your `accountId` values are quoted. If your `accountId` starts with a `0` and is unquoted it will get interpreted as an octal number and result in an incorrect value, which will manifest as a failure to perform an `sts:assumeRole` command in the AWS SDK, the same as if your IAM configuration is incorrect.

In the AWS provider section of your spinnaker config update `providers.aws.primaryCredentials.name` to match the name of the managing account.

Once the credentials are configured, they will show up at the `/credentials` endpoint.
After adding the accounts you will want to look at the UI settings.js and add appropriate
account configuration there.

Additionally, in orca we identify one account as our `default.bake.account`. The expectation
is that AWS AMI images are registered in that account (using Rosco or some other) and from
there we share those AMIs into the deployment account on deploy. The default value for
`default.bake.account` is `default`, so if you don't have an account named `default` any
more you will want to update the `orca.yml` with whichever account is the source of
custom AWS AMIs. If the default account is missing, the value further falls back to `providers.aws.primaryCredentials.name`.
