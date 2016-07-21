# Eureka Health Provider Configuration
You can configure Eureka as a health provider for your account with the following basic configuration in clouddriver:

    eureka:
      provider:
        enabled: true
        accounts:
          - name: my-aws-account
            regions:
              - us-west-2
            readOnlyUrl: "http://10.0.0.1:8080/eureka"

The provider also supports a region placeholder:

    eureka:
      provider:
        enabled: true
        accounts:
          - name: my-aws-account
            regions:
              - us-west-2
              - us-east-1
            readOnlyUrl: "http://myhostname.{{region}}.mycompany.com:8080/eureka"
          - name: my-second-aws-account
            regions:
              - us-west-1
            readOnlyUrl: "http://myhostname.{{region}}.mycompany.com:8080/eureka"
