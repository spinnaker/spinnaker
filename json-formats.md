# JSON Formats

## Canary configuration

This config defines the metrics and analysis thresholds set for a canary run.
Typically, thresholds can be overridden at execution time as well.

It is normal to have all metrics for a given canary run come from the same source.  In these examples,
Atlas and Stackdriver are used.


```JSON
{
  "name": "MySampleAtlasCanaryConfig",
  "description": "Example Automated Canary Analysis (ACA) Configuration using Atlas",
  "configVersion": 1.0,
  "metrics": [
    {
      "serviceName": "atlas",
      "name": "cpu",
      "query": {
        "type": "atlas",
        "q": "name,CpuRawUser,:eq,:sum,name,numProcs,:eq,:sum,:div"
      },
      "analysisConfigurations": {
        "canary": { }
      },
      "groups": ["system"]
    },
    {
      "serviceName": "atlas",
      "name": "requests",
      "query": {
        "type": "atlas",
        "q": "name,apache.http.requests,:eq,:sum"
      },
      "analysisConfigurations": {
        "canary": { }
      },
      "groups": ["requests"]
    }
  ],
  "services": {
    "atlas": {
      "type": "atlas",
      "name": "atlas",
      "region": "us-east-1",
      "environment": "prod",
      "backend": {
        "deployment": "main",
        "dataset": "regional"
      }
    }
  },
  "classifier": {
    "groupWeights": {
      "requests": 50.0,
      "system": 50.0
    },
    "scoreThresholds": {
      "pass": 95.0,
      "marginal": 75.0
    }
  }
}
```
```JSON
{
  "name": "MySampleStackdriverCanaryConfig",
  "description": "Example Automated Canary Analysis (ACA) Configuration using Stackdriver",
  "configVersion": 1.0,
  "metrics": [
    {
      "serviceName": "stackdriver",
      "name": "cpu",
      "query": {
        "type": "stackdriver",
        "metricType": "compute.googleapis.com/instance/cpu/utilization"
      },
      "analysisConfigurations": {
        "canary": { }
      },
      "groups": ["system"]
    }
  ],
  "services": {
    "stackdriver": {
      "type": "stackdriver",
      "name": "stackdriver"
    }
  },
  "classifier": {
    "groupWeights": {
      "system": 100.0
    },
    "scoreThresholds": {
      "pass": 95.0,
      "marginal": 75.0
    }
  }
}
```

## Canary Data Archival Format

This format is used to store the results from a specific canary run.
Data retrieved is immutable; however, it could be copied and used again
to re-run with different thresholds.

```JSON
{
  "results": [
    {
      "name": "cpu",
      "tags": {"tagName": "tagValue"},
      "values": {
        "control": [ 1, 2, 3, 4, 5 ],
        "experiment": [ 1, 2, 3, 4, 5 ]
      }
    }
  ]
}
```
