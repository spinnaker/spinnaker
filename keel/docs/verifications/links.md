# Verification links

A VerificationState object has a `link` field.
This represents a URL that is exposed via Deck that allows the user to view more information about the state of the verification (e.g., test log output).

## Serving the link over the API

Verification links appear in the response to the `ApplicationController.get` call (`/application/{application}`).
When served over the API, the links appear inside of `VerificationSummary` objects.

## Generating the link

The VerificationEvaluator subclass is responsible for computing the link in the `evaluate` method.

## Test container link implementation

At Netflix, there's an internal Titus UI that shows the state of running tasks.
This is the service we want our link to point to when we run a test container verification at Netflix.

Since Titus UI is specific to our deployment, we don't want to hard-code the logic for generating Titus UI urls in keel itself.
We add a layer of indirection by injecting a list of `LinkStrategy` objects into the `TestContainerVerificationEvaluator` constructor.
This enables us to implement an internal `LinkStrategy` implementation for generating the Netflix-specific URLs.

The strategy object expects a map that contains a "jobStatus" payload, which is defined as the `jobStatus` variable in the orca response.
This payload contains the information we need to construct the Titus UI URL.

Example jobStatus payload:

```json
{
  "application": "appname",
  "provider": "titus",
  "completionDetails": {
    "instanceId": "0ae23953-5bd5-4d8d-a1aa-01654ae60799",
    "taskId": "0ae23953-5bd5-4d8d-a1aa-01654ae60799"
  },
  "jobState": "Failed",
  "name": "appname",
  "createdTime": 1617230480174,
  "id": "d32712dd-95ee-5ec9-a807-83689aef4330",
  "type": "titus",
  "region": "us-west-2",
  "account": "titusaccount"
  }
```