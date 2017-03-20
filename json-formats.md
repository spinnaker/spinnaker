# JSON Formats

## Canary configuration

This config defines the metrics and analysis thresholds set for a canary run.
Typically, thresholds can be overridden at execution time as well.

It is normal to have all metrics come from the same source.  In this example,
Atlas is used.

** TODO: Add examples for other drivers **

```JSON
{
  "configVersion": 1.0,
  "description": "Example Automated Canary Analysis (ACA) Configuration",
  "metrics": [
    {
      "service": "atlas",
      "name": "cpu",
      "query": "name,CpuRawUser,:eq,:sum,name,numProcs,:eq,:sum,:div",
      "extensions": {
        "canary": { }
      },
      "groups": ["system"]
    },
    {
      "service": "atlas",
      "name": "requests",
      "query": "name,apache.http.requests,:eq,:sum",
      "extensions": {
        "canary": { }
      },
      "groups": ["requests"]
    }
  ],
  "services": {
    "atlas": {
      "type": "atlas",
      "region": "us-east-1",
      "env": "prod",
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
        "baseline": [ 1, 2, 3, 4, 5 ],
        "canary": [ 1, 2, 3, 4, 5 ]
      }
    }
  ]
}
```
