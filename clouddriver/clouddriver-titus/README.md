## Configuring clouddriver titus

Here is an example configuration for clouddriver-titus

```
titus:
  enabled: true
  awsVpc: vpc0 # this is the default vpc used by titus
  accounts:
    - name: titusdevint
      environment: test
      discovery: "http://discovery.compary.com/v2"
      discoveryEnabled: true
      registry: testregistry # reference to the docker registry being used
      awsAccount: test # aws account underpinning
      autoscalingEnabled: true
      loadBalancingEnabled: false # load balancing will be released at a later date
      regions:
        - name: us-east-1
          url: https://myTitus.us-east-1.company.com/
          port: 7104
          autoscalingEnabled: true
          loadBalancingEnabled: false
        - name: eu-west-1
          url: https://myTitus.eu-west-1.company.com/
          port: 7104
          autoscalingEnabled: true
          loadBalancingEnabled: false
```

By default, Titus will try to create a grpc connection to port 7104.

You need to have an underlying aws connection created and ready to be used and specify this in the awsAccount section.

Aws is used for security groups, iam profiles and autoscaling policies.

There are currently no plans to enable titus in Halyard.
