# Eureka Health Provider Configuration
You can configure Eureka as a health provider for your account with the following basic configuration in clouddriver:

    eureka:
      provider:
        enabled: true
        accounts:
          - name: my-aws-account
            regions:
              - us-west-2
            readOnlyUrl: "http://10.0.0.1:8080/eureka/v2"

The provider also supports a region placeholder:

    eureka:
      provider:
        enabled: true
        accounts:
          - name: my-aws-account
            regions:
              - us-west-2
              - us-east-1
            readOnlyUrl: "http://myhostname.{{region}}.mycompany.com:8080/eureka/v2"
          - name: my-second-aws-account
            regions:
              - us-west-1
            readOnlyUrl: "http://myhostname.{{region}}.mycompany.com:8080/eureka/v2"

Each account definition in AWS must also define a `discovery` field that denotes the URL for the writeable eureka.

```
- name: test
      environment: test
      accountType: main
      discovery: "http://mywriteableeureka.{{region}}.mycompany:8080/eureka/v2"
      accountId: 1234567899999
      regions:
        - name: us-east-1
```

By default, only one Eureka is supported per AWS account. If you have multiple
`aws.accounts` configured in clouddriver which share an accountId, and wish to
use a separate Eureka for each, then you can enable support by setting
`eureka.provider.allowMultipleEurekaPerAccount` to true. For example:

<pre>
    eureka:
      provider:
        <b>allowMultipleEurekaPerAccount: true</b>
        enabled: true
        accounts:
          - name: my-ci-account
            regions:
              - us-west-2
            readOnlyUrl: "http://10.0.0.1:8080/eureka/v2"
          - name: my-qa-account
            regions:
              - us-west-2
            readOnlyUrl: "http://10.0.0.2:8080/eureka/v2"

    aws:
      enabled: true
      accounts:
        - name: my-ci-account
          accountId: 1234567899999
          defaultKeyPair: 'my-keypair'
          environment: ci
          discovery: "http://10.0.0.1:8080/eureka/v2"
          regions:
            - name: us-west-2
        - name: my-qa-account
          accountId: 1234567899999
          defaultKeyPair: 'my-keypair'
          environment: qa
          discovery: "http://10.0.0.2:8080/eureka/v2"
          regions:
            - name: us-west-2
</pre>

The eureka account name (`eureka.provider.accounts[].name`) must match the
name of the AWS account (`aws.enabled.accounts[].name`) with which it
shares the same Eureka.
Please note that `eureka.provider.allowMultipleEurekaPerAccount` only works
with AWS as the cloud provider. Additionally, this feature is not supported
in the titus integration.
