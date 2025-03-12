# JSON Formats

## Canary configuration

This config defines the metrics and analysis thresholds set for a canary run.
Typically, thresholds can be overridden at execution time as well.

It is normal to have all metrics for a given canary run come from the same source.  In these examples,
Atlas, Stackdriver, Prometheus, Datadog, SignalFx, and Wavefront are used.


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
```JSON
{
  "name": "MySampleDatadogCanaryConfig",
  "description": "Example Kayenta Configuration using Datadog",
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
      "name": "CPU",
      "query": {
        "type": "datadog",
        "metricName": "avg:system.cpu.user"
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
  "name": "SignalFxIntegrationTestCanaryConfig",
  "description": "A very simple config for integration testing the SignalFx metric source Kayenta module.",
  "judge": {
    "judgeConfigurations": {},
    "name": "NetflixACAJudge-v1.0"
  },
  "metrics": [
    {
      "name": "Cpu Usage Percentage",
      "query": {
        "metricName": "kayenta.integration-test.cpu.avg",
        "aggregationMethod": "avg",
        "serviceType": "signalfx",
        "type": "signalfx"
      },
      "analysisConfigurations": {
        "canary": {
          "direction": "increase"
        }
      },
      "groups": [
        "Integration Test Group"
      ],
      "scopeName": "default"
    },
    {
      "name": "Bad Request Rate for /v1/some-endpoint",
      "query": {
        "metricName": "kayenta.integration-test.request.count",
        "queryPairs": [
          {
            "key": "uri",
            "value": "/v1/some-endpoint"
          },
          {
            "key": "status_code",
            "value": "4*"
          }
        ],
        "aggregationMethod": "sum",
        "serviceType": "signalfx",
        "type": "signalfx"
      },
      "analysisConfigurations": {
        "canary": {
          "direction": "increase",
          "critical": true
        }
      },
      "groups": [
        "Integration Test Group"
      ],
      "scopeName": "default"
    }
  ],
  "classifier": {
    "groupWeights": {
      "Integration Test Group": 100
    },
    "scoreThresholds": {
      "marginal": 50,
      "pass": 75
    }
  }
}
```
```JSON
{
  "name": "MySampleGraphiteCanaryConfig",
  "description": "Example Kayenta Configuration using Graphite",
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
      "name": "CPU",
      "query": {
        "type": "graphite",
        "metricName": "system.cpu.user"
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
  "name": "MySampleWavefrontCanaryConfig",
  "description": "Example Kayenta Configuration using Wavefront",
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
      "name": "CPU",
      "query": {
        "type": "wavefront",
        "metricName": "heapster.pod.cpu.usage_rate",
        "aggregate": "avg",
        "summerization": "MEAN"
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
