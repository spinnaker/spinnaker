### These instructions take you from scratch all the way through making simple calls via the various controllers.

#### Configuration, Startup and Testing Stackdriver
1. Clone the repo do your local workstation.

1. Retrieve a GCP service account json key and store it locally somewhere. Note this location since you will need it for your configuration. It needs permissions for stackdriver and for GCS.

1. Create `~/.kayenta/kayenta-local.yml` with the following contents:
```yaml
logging:
  level:
    com.netflix.kayenta: DEBUG

kayenta:
  redis:
    enabled: true

  google:
    enabled: true
    accounts:
      - name: my-google-account
        project: my-project-id
        jsonPath: /full/path/to/my/service-account-json-key.json
        # The bucket will be created it it does not exist.
        bucket: mjd-my-test-bucket
        rootFolder: kayenta
        supportedTypes:
          - METRICS_STORE
          - OBJECT_STORE

  gcs:
    enabled: true

  stackdriver:
    enabled: true

queue.redis.enabled: true
executionLog.redis.enabled: true
```

1. Start `redis-server` (must be accessible via `localhost:6379` or you need to specify the correct endpoint via something like `redis.connection: redis://localhost:6379` ).

1. `cd` into your `kayenta` directory and run `./gradlew`. Watch the console on startup to verify there are no errors.

1. Once kayenta starts up, `curl localhost:8090/health` to ensure kayenta is healthy and that redis is reachable.

1. Navigate to the [Kayenta swagger ui](http://localhost:8090/swagger-ui.html).

1. Exercise the [Credentials Controller](http://localhost:8090/swagger-ui.html#!/credentials45controller/listUsingGET_1) endpoint to ensure that your credentials were properly configured.

1. Modify the default parameters values provided by [Stackdriver Fetch Controller](http://localhost:8090/swagger-ui.html#!/stackdriver45fetch45controller/queryMetricsUsingPOST) to match an instance group and start/end times that make sense for your project.

1. Exercise the endpoint and capture the UUID returned in the response.

1. Pass that UUID to the [Metric Set List Controller](http://localhost:8090/swagger-ui.html#!/metric45set45list45controller/loadMetricSetListUsingGET) to query the results from the stackdriver call. 

#### Publishing a Canary Config
1. Pass the contents of `kayenta/scratch/stackdriver_canary_config.json` to the [Canary Config Controller](http://localhost:8090/swagger-ui.html#!/canary45config45controller/storeCanaryConfigUsingPOST).

1. Modify the default parameter values provided by [Canary Controller](http://localhost:8090/swagger-ui.html#!/canary45controller/initiateCanaryUsingPOST) to match two instance groups and start/end times that make sense for your project.

1. Exercise the endpoint and capture the UUID returned in the response.

1. Pass that UUID to the [Pipeline Controller](http://localhost:8090/swagger-ui.html#!/pipeline45controller/getPipelineUsingGET) to query the in-flight pipeline execution. Should take just over a minute to complete. You can see the various metric set list and metric set pair list UUIDs accumulating in the pipeline execution.

1. You can pass that same UUID to the [Pipeline Controller/Execution Logs](http://localhost:8090/swagger-ui.html#!/pipeline45controller/logsUsingGET) endpoint to query the redis-backed execution log.
