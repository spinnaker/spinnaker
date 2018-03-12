# JSON Formats

## Canary configuration

This config defines the metrics and analysis thresholds set for a canary run.
Typically, thresholds can be overridden at execution time as well.

It is normal to have all metrics for a given canary run come from the same source.  In these examples,
Atlas, Stackdriver and Prometheus are used.


```JSON
{
  "name": "MySampleAtlasCanaryConfig",
  "description": "Example Kayenta Configuration using Atlas",
  "configVersion": "1.0",
  "applications": [
    "myapp"
  ],
  "judge": {
    "name": "dredd-v1.0",
    "judgeConfigurations": { }
  },
  "metrics": [
    {
      "name": "cpu",
      "query": {
        "type": "atlas",
        "q": "name,CpuRawUser,:eq,:sum,name,numProcs,:eq,:sum,:div"
      },
      "groups": ["system"],
      "analysisConfigurations": { },
      "scopeName": "default"
    },
    {
      "name": "requests",
      "query": {
        "type": "atlas",
        "q": "name,apache.http.requests,:eq,:sum"
      },
      "groups": ["requests"],
      "analysisConfigurations": { },
      "scopeName": "default"
    }
  ],
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
  "description": "Example Kayenta Configuration using Stackdriver",
  "configVersion": "1.0",
  "applications": [
    "myapp"
  ],
  "judge": {
    "name": "dredd-v1.0",
    "judgeConfigurations": { }
  },
  "metrics": [
    {
      "name": "cpu",
      "query": {
        "type": "stackdriver",
        "metricType": "compute.googleapis.com/instance/cpu/utilization"
      },
      "groups": ["system"],
      "analysisConfigurations": { },
      "scopeName": "default"
    }
  ],
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
```JSON
{
  "name": "MySamplePrometheusCanaryConfig",
  "description": "Example Kayenta Configuration using Prometheus",
  "configVersion": "1.0",
  "applications": [
    "myapp"
  ],
  "judge": {
    "name": "dredd-v1.0",
    "judgeConfigurations": { }
  },
  "metrics": [
    {
      "name": "cpu",
      "query": {
        "type": "prometheus",
        "metricName": "node_cpu",
        "labelBindings": [
          "mode=~\"user|system\""
        ]
      },
      "groups": ["system"],
      "analysisConfigurations": { },
      "scopeName": "default"
    }
  ],
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
```JSON
{
  "name": "MySampleK8SPrometheusCanaryConfigWithCustomFilterTemplate",
  "description": "Example Kayenta Configuration with Custom Filter Template using Prometheus for K8S",
  "configVersion": "1.0",
  "applications": [
    "myapp"
  ],
  "judge": {
    "name": "dredd-v1.0",
    "judgeConfigurations": { }
  },
  "metrics": [
    {
      "name": "cpu_usage_seconds",
      "query": {
        "type": "prometheus",
        "metricName": "container_cpu_usage_seconds_total",
        "labelBindings": [ ],
        "customFilterTemplate": "my-template"
      },
      "groups": ["system"],
      "analysisConfigurations": { },
      "scopeName": "default"
    }
  ],
  "templates": {
    "my-template": "container_name='${container_name}'"
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
[
  {
    "name": "cpu",
    "tags": {"tagName": "tagValue"},
    "values": {
      "control": [ 1, 2, 3, 4, 5 ],
      "experiment": [ 1, 2, 3, 4, 5 ]
    },
    "attributes": {"query": "..."},
    "startTimeMillis": 1516046700000,
    "startTimeIso": "2018-01-15T20:05:00Z",
    "stepMillis": 3600000
  }
]
```
