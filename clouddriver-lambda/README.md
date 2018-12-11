# AWS Lambda Support

### **Background **

Spinnaker CloudDriver has been enhanced to add support for AWS Lambda. Below lists the API contract input that have been coded in this repository.

## clouddriver.yml override ##

```yaml
aws:
  lambda:
    enabled: true
  accounts:
    - name: test
      lambdaEnabled: true
```

# Controller calls

## Get all lambda functions

### Purpose

Retrieves all cached lambda functions.

***Sample Request***

```
curl -X GET --header 'Accept: application/json'
'http://localhost:7002/functions'
```

***Sample Response***

```
`[
  {
    "accountName": "spinnaker-lambda",
    "codeSha256": null,
    "codeSize": null,
    "deadLetterConfig": null,
    "description": "Encryption",
    "environment": null,
    "functionArn": "arn:aws:lambda:us-west-2:123456789012:function:Encryption",
    "functionName": "Encryption",
    "handler": "lambda_function.lambda_handler",
    "kmskeyArn": null,
    "lastModified": "2017-01-12T18:44:57.457+0000",
    "masterArn": null,
    "memorySize": null,
    "region": "us-west-2",
    "revisionId": "6ee650df-804b-4a7b-a9a4-b14fb316a358",
    "role": null,
    "runtime": "python2.7",
    "timeout": null,
    "tracingConfig": null,
    "version": null,
    "vpcConfig": null
  }
]`
```
