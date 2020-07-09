## Amazon ECS Clouddriver

The clouddriver-ecs module allows for Amazon ECS deployments of dockerized applications.  **You need to enable the AWS cloud provider in order for the ECS cloud provider to work**.

## Clouddriver configuration

In order for the Amazon ECS cloud provider to work, a corresponding AWS account must be configured and enabled. An ECS account will be tied to a given AWS account by its name. Below is an example snippet of `clouddriver.yml`:

```
aws:
  enabled: true

  accounts:
    - name: aws-account-name
      accountId: "123456789012"
      regions:
        - name: us-east-1
  defaultAssumeRole: role/SpinnakerManaged

ecs:
  enabled: true
  accounts:
    - name: ecs-account-name
      awsAccount: aws-account-name
```


## Spinnaker role

In Spinnaker 1.19 and later, the Amazon ECS cloud provider requires [service-linked roles](https://docs.aws.amazon.com/AmazonECS/latest/userguide/using-service-linked-roles.html) for Amazon ECS and Application Auto Scaling. To create the required service-linked roles, run the following `aws-cli` commands:

```
aws iam create-service-linked-role --aws-service-name ecs.amazonaws.com
aws iam create-service-linked-role --aws-service-name ecs.application-autoscaling.amazonaws.com
```

See the official Spinnaker [Amazon ECS provider setup docs](https://spinnaker.io/setup/install/providers/aws/aws-ecs/#service-linked-iam-roles) for more information.
