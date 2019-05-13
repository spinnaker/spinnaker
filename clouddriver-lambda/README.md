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
         "account": "mylambda",
         "codeSha256": "rHHd9Lk3j7h6MMZKqb3lQzAHKO1eWrmW8Wh/QP1+KuE=",
         "codeSize": 7011514,
         "description": "sample",
         "eventSourceMappings": [],
         "functionArn": "arn:aws:lambda:us-west-2:<acctno>:function:mylambdafunctiontwo",
         "functionName": "mylambdafunctiontwo",
         "functionname": "aws:lambdaFunctions:mylambda:us-west-2:mylambdafunctiontwo",
         "handler": "lambda_function.lambda_handler",
         "lastModified": "2019-03-29T15:52:33.054+0000",
         "layers": [],
         "memorySize": 512,
         "region": "us-west-2",
         "revisionId": "58cb0acc-4a20-4e57-b935-cc97ae1769fd",
         "revisions": {
             "58cb0acc-4a20-4e57-b935-cc97ae1769fd": "$LATEST",
             "ee17b471-d6e3-47a3-9e7b-8cae9b92c626": "2"
         },
         "role": "arn:aws:iam::<acctno>:role/service-role/test",
         "runtime": "python3.6",
         "timeout": 60,
         "tracingConfig": {
             "mode": "PassThrough"
         },
         "version": "$LATEST"
     },
     {
         "account": "mylambda",
         "codeSha256": "rHHd9Lk3j7h6MMZKqb3lQzAHKO1eWrmW8Wh/QP1+KuE=",
         "codeSize": 7011514,
         "description": "sample",
         "eventSourceMappings": [],
         "functionArn": "arn:aws:lambda:us-west-2:<acctno>:function:mylambdafunctionone",
         "functionName": "mylambdafunctionone",
         "functionname": "aws:lambdaFunctions:mylambda:us-west-2:mylambdafunctionone",
         "handler": "lambda_function.lambda_handler",
         "lastModified": "2019-03-29T15:46:04.995+0000",
         "layers": [],
         "memorySize": 512,
         "region": "us-west-2",
         "revisionId": "73e5500a-3751-4073-adc0-877dfc3c720d",
         "revisions": {
             "1e280c63-1bcd-4840-92dc-bef5f1b46028": "1",
             "73e5500a-3751-4073-adc0-877dfc3c720d": "$LATEST"
         },
         "role": "arn:aws:iam::<acctno>:role/service-role/test",
         "runtime": "python3.6",
         "timeout": 68,
         "tracingConfig": {
             "mode": "PassThrough"
         },
         "version": "$LATEST"
     }
 ]`
```

### Purpose

Retrieves details corresponding to a single lambda function.

***Sample Request***

```
curl -X GET --header 'Accept: application/json'
'http://localhost:7002/functions?functionName=mylambdafunctionone&region=us-west-2&account=mylambda'
```

***Sample Response***

```
[
    {
        "account": "mylambda",
        "codeSha256": "rHHd9Lk3j7h6MMZKqb3lQzAHKO1eWrmW8Wh/QP1+KuE=",
        "codeSize": 7011514,
        "description": "sample",
        "eventSourceMappings": [],
        "functionArn": "arn:aws:lambda:us-west-2:<acctno>:function:mylambdafunctionone",
        "functionName": "mylambdafunctionone",
        "functionname": "aws:lambdaFunctions:mylambda:us-west-2:mylambdafunctionone",
        "handler": "lambda_function.lambda_handler",
        "lastModified": "2019-03-29T15:46:04.995+0000",
        "layers": [],
        "memorySize": 512,
        "region": "us-west-2",
        "revisionId": "73e5500a-3751-4073-adc0-877dfc3c720d",
        "revisions": {
            "1e280c63-1bcd-4840-92dc-bef5f1b46028": "1",
            "73e5500a-3751-4073-adc0-877dfc3c720d": "$LATEST"
        },
        "role": "arn:aws:iam::481090335964:role/service-role/test",
        "runtime": "python3.6",
        "timeout": 68,
        "tracingConfig": {
            "mode": "PassThrough"
        },
        "version": "$LATEST"
    }
]

```
### Purpose

Create a lambda function.

***Sample Request***

```
curl -X POST \
  http://localhost:7002/aws/ops/createLambdaFunction \
  -H 'Accept: application/json' \
  -H 'Content-Type: application/json' \
  -d '{
    "region": "us-west-2",
    "functionName": "mylambdafunctiontwo",
    "description": "sample",
    "credentials": "mylambda",
    "handler": "lambda_function.lambda_handler",
      "s3bucket": "my_s3_bucket_name",
    "s3key": "my_s3_object_key",
    "memory": 512,
    "publish": "true",
    "role": "arn:aws:iam::<acctno.>:role/service-role/test",
    "runtime": "python3.6",
    "timeout": "60",
    "tags": [{
        "key":"value"
    }

    ]
}'
```

***Sample Response***

```
{
    "id": "c3bd961d-c951-423e-aad6-918f29e78ccb",
    "resourceUri": "/task/c3bd961d-c951-423e-aad6-918f29e78ccb"
}

You may navigate to
http://localhost:7002/$resourceUri to see
the orchestration details.
In this case, resourceUri generated for my post request is
/task/c3bd961d-c951-423e-aad6-918f29e78ccb. So, I'll have to navigate to
http://localhost:7002/task/c3bd961d-c951-423e-aad6-918f29e78ccb for orchestration details
```

### Purpose

Update lambda function configuration.

***Sample Request***

```
curl -X POST \
  http://localhost:7002/aws/ops/updateLambdaFunctionConfiguration \
  -H 'Accept: application/json' \
  -H 'Content-Type: application/json' \
  -d '{
    "region": "us-west-2",
    "functionName": "mylambdafunctionone",
    "description": "sample",
    "credentials": "mylambda",
    "handler": "lambda_function.lambda_handler",
    "memory": 512,
    "role": "arn:aws:iam::<acctno>:role/service-role/test",
    "runtime": "python3.6",
    "timeout": "68",
    "tags": [{
        "key":"value"
    }

    ]
}'
```
Note: I've changed the timeout from 60 to 68. Naviagate to the aws console to see
if that change is being reflected.

***Sample Response***

```
{
    "id": "bfdb1201-1c31-4a83-84bb-a807d69291fc",
    "resourceUri": "/task/bfdb1201-1c31-4a83-84bb-a807d69291fc"
}

You may navigate to
http://localhost:7002/$resourceUri to see
the orchestration details.
In this case, resourceUri generated for my post request is
/task/bfdb1201-1c31-4a83-84bb-a807d69291fc. So, I'll have to navigate to
http://localhost:7002/task/bfdb1201-1c31-4a83-84bb-a807d69291fc for orchestration details
```

### Purpose

Delete a lambda function.

***Sample Request***

```
curl -X POST \
  http://localhost:7002/aws/ops/deleteLambdaFunction \
  -H 'Accept: application/json' \
  -H 'Content-Type: application/json' \
  -d '{
    "region": "us-west-2",
    "functionName": "mylambdafunctiontwo",
    "credentials": "mylambda"
}'
```

***Sample Response***

```
{
    "id": "4c316ba9-7db8-4675-82d9-5adf118c541c",
    "resourceUri": "/task/4c316ba9-7db8-4675-82d9-5adf118c541c"
}

You may navigate to
http://localhost:7002/$resourceUri to see
the orchestration details.
In this case, resourceUri generated for my post request is
/task/4c316ba9-7db8-4675-82d9-5adf118c541c. So, I'll have to navigate to
http://localhost:7002/task/4c316ba9-7db8-4675-82d9-5adf118c541c for orchestration details
```

<<<<<<< HEAD
### Purpose

Invoke a lambda function.

***Sample Request***

```

curl -X POST \
  http://localhost:7002/aws/ops/invokeLambdaFunction \
  -H 'Accept: application/json' \
  -H 'Content-Type: application/json' \
  -d '{
    "region": "us-west-2",
    "functionName": "mylambdafunctionone",
    "credentials": "mylambda",
    "description": "sample",
    "Invocation-Type": "RequestResponse",
    "log-type": "Tail",
    "qualifier": "$LATEST",
    "outfile": "out.txt"
}'
```

***Sample Response***

```
{
    "id": "e4dfdfa1-0b3c-4980-a745-413eb9806332",
    "resourceUri": "/task/e4dfdfa1-0b3c-4980-a745-413eb9806332"
}

You may navigate to
http://localhost:7002/$resourceUri to see
the orchestration details.
In this case, resourceUri generated for my post request is
/task/4c316ba9-7db8-4675-82d9-5adf118c541c. So, I'll have to navigate to
http://localhost:7002/task/4c316ba9-7db8-4675-82d9-5adf118c541c for orchestration details
```


### Purpose

Update lambda function code.

***Sample Request***

```

curl -X POST \
  http://localhost:7002/aws/ops/updateLambdaFunctionCode \
  -H 'Accept: application/json' \
  -H 'Content-Type: application/json' \
  -d '{
    "region": "us-west-2",
    "functionName": "mylambdafunctiontwo",
    "credentials": "mylambda",
    "s3Bucket": "my_s3_bucket_name",
    "s3Key": "my_s3_object_key",
    "publish": "true"
}'
```

***Sample Response***

```
{
    "id": "3a43157d-7f5d-4077-bc8d-8a21381eb6b7",
    "resourceUri": "/task/3a43157d-7f5d-4077-bc8d-8a21381eb6b7"
}

You may navigate to
http://localhost:7002/$resourceUri to see
the orchestration details.
In this case, resourceUri generated for my post request is
/task/4c316ba9-7db8-4675-82d9-5adf118c541c. So, I'll have to navigate to
http://localhost:7002/task/4c316ba9-7db8-4675-82d9-5adf118c541c for orchestration details
```


### Purpose

Upsert Event Mapping.

***Sample Request***

```
curl -X POST \
  http://localhost:7002/aws/ops/upsertLambdaFunctionEventMapping \
  -H 'Accept: application/json' \
  -H 'Content-Type: application/json' \
  -d '{
    "region": "us-west-2",
    "functionName": "mylambdafunctiontwo",
    "credentials": "mylambda",
    "batchsize" : "10",
    "majorFunctionVersion": "1",
    "enabled": "false",
    "eventSourceArn" : "arn:aws:kinesis:us-west-2:<myacctid>:stream/myteststream"
}'
```

***Sample Response***

```
{
    "id": "451b5171-7050-43b7-9176-483790e77bb6",
    "resourceUri": "/task/50540cf6-5859-44f6-9f13-9c4944386666"
}

You may navigate to
http://localhost:7002/$resourceUri to see
the orchestration details.
In this case, resourceUri generated for my post request is
/task/4c316ba9-7db8-4675-82d9-5adf118c541c. So, I'll have to navigate to
http://localhost:7002/task/4c316ba9-7db8-4675-82d9-5adf118c541c for orchestration details
It is important to capture the UUID from the orchestration details,
If you plan to delete the event mapping later

{
    "history": [
        {
            "phase": "ORCHESTRATION",
            "status": "Initializing Orchestration Task..."
        },
        {
            "phase": "ORCHESTRATION",
            "status": "Processing op: UpsertLambdaEventSourceAtomicOperation"
        },
        {
            "phase": "UPSERT_LAMBDA_FUNCTION_EVENT_MAPPING",
            "status": "Initializing Creation of AWS Lambda Function Event Source Mapping..."
        },
        {
            "phase": "UPSERT_LAMBDA_FUNCTION_EVENT_MAPPING",
            "status": "Finished Creation of AWS Lambda Function Event Mapping Operation..."
        },
        {
            "phase": "ORCHESTRATION",
            "status": "Orchestration completed."
        }
    ],
    "id": "50540cf6-5859-44f6-9f13-9c4944386666",
    "ownerId": "831f24c7-a083-40fa-9b42-c106e6d5edb0@spin-clouddriver-d66d9f79b-tq8mw",
    "resultObjects": [
        {
            "batchSize": 10,
            "eventSourceArn": "arn:aws:kinesis:us-west-2:<acctid>:stream/mytest",
            "functionArn": "arn:aws:lambda:us-west-2:<acctid>:function:mylambdafunctiontwo",
            "lastModified": 1554382614013,
            "lastProcessingResult": "No records processed",
            "sdkHttpMetadata": {
                "httpHeaders": {
                    "Connection": "keep-alive",
                    "Content-Length": "352",
                    "Content-Type": "application/json",
                    "Date": "Thu, 04 Apr 2019 12:56:54 GMT",
                    "x-amzn-RequestId": "1ef75be6-56d9-11e9-8874-479d47ecf826"
                },
                "httpStatusCode": 202
            },
            "sdkResponseMetadata": {
                "requestId": "1ef75be6-56d9-11e9-8874-479d47ecf826"
            },
            "state": "Creating",
            "stateTransitionReason": "User action",
            "uuid": "4101b421-f0fb-4c89-8f99-6c2c153ec8d3"
        }
    ],
    "startTimeMs": 1554382613881,
    "status": {
        "complete": true,
        "completed": true,
        "failed": false,
        "phase": "ORCHESTRATION",
        "status": "Orchestration completed."
    }
}

In my case the UUID is 4101b421-f0fb-4c89-8f99-6c2c153ec8d3
```

### Purpose

Delete lambda function eventmapping

***Sample Request***

```
curl -X POST \
  http://localhost:7002/aws/ops/deleteLambdaFunctionEventMapping \
  -H 'Accept: application/json' \
  -H 'Content-Type: application/json' \
  -d '{
    "region": "us-west-2",
    "functionName": "mylambdafunctiontwo",
    "application": "LAMBDA-PRINT-FUNCTION",
    "credentials": "mylambda",
    "UUID": "0ee2253a-737d-4863-9f19-84627785e85e"
}'
```

***Sample Response***

```
{
    "id": "0a01d76c-7942-46f0-810f-0f879f22e498",
    "resourceUri": "/task/0a01d76c-7942-46f0-810f-0f879f22e498"
}

You may navigate to
http://localhost:7002/$resourceUri to see
the orchestration details.
In this case, resourceUri generated for my post request is
/task/0a01d76c-7942-46f0-810f-0f879f22e498. So, I'll have to navigate to
http://localhost:7002/task/0a01d76c-7942-46f0-810f-0f879f22e498 for orchestration details
```

### Purpose

Upsert Lambda Function alias

***Sample Request***

```
curl -X POST \
  http://localhost:7002/aws/ops/upsertLambdaFunctionAlias \
  -H 'Accept: application/json' \
  -H 'Content-Type: application/json' \
  -d '{
    "region": "us-west-2",
    "functionName": "mylambdafunctionone",
    "credentials": "mylambda",
    "aliasDescription" : "description for alias 1",
    "majorFunctionVersion": "1",
    "aliasName": "spinnaker-alias-2",
    "minorFunctionVersion" : "2",
    "weightToMinorFunctionVersion" : "0.3"
}'
```

***Sample Response***

```
{
    "id": "0a01d76c-7942-46f0-810f-0f879f22e498",
    "resourceUri": "/task/0a01d76c-7942-46f0-810f-0f879f22e498"
}

You may navigate to
http://localhost:7002/$resourceUri to see
the orchestration details.
In this case, resourceUri generated for my post request is
/task/0a01d76c-7942-46f0-810f-0f879f22e498. So, I'll have to navigate to
http://localhost:7002/task/0a01d76c-7942-46f0-810f-0f879f22e498 for orchestration details
```
