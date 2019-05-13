## AWS ECS Clouddriver

The clouddriver-ecs module allows for ECS deployments of dockerized applications.  **You need to enable the AWS cloud provider in order for the ECS cloud provider to work**.

It is a work in progress

## Clouddriver configuration

In order for the ECS cloud provider to work, a corresponding AWS account must be configured and enabled. An ECS account will be tied to a given AWS account by its name. Below is an example snippet of `clouddriver.yml`:

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
Make sure that you allow the `application-autoscaling.amazonaws.com` and `ecs.amazonaws.com` principals to assume the SpinnakerManaged role by adding it as a principal.  See example code below.  Failure to do so will prevent you from deploying ECS server groups:
```
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
                "Service": [
                  "ecs.amazonaws.com",
                  "application-autoscaling.amazonaws.com"
                ],
      },
      "Action": "sts:AssumeRole"
    }
  ]
}
```
##


TODO Wishlist:
1. Perhaps clouddriver should try to add the 2 required trust relationships on startup if they are detected as not being present
