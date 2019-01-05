# Kayenta Graphite

This module adds support for Graphite as Kayenta's metrics source.

## Configuration

### Kayenta
```yaml
  graphite:
    enabled: false
    accounts:
      - name: my-graphite-account
        endpoint:
          baseUrl: http://localhost
        supportedTypes:
          - METRICS_STORE
```

## Development

### Integration Tests

This module has an End to End test with in memory store, embedded redis and embedded graphite server.

It creates three threads that feed data to the embedded graphite server with different values.

- Control: the control cluster
- Healthy Experiment: the experiment cluster acts like the control cluster 
- Unhealthy Experiment: the experiment cluster which will have higher failure rate than the control and healthy cluster

Note: To run the integration test, needs docker and redis-server be installed. 

Starts the integration test with the following command
```bash
./gradlew kayenta-graphite:integrationTest  -Dredis.path=$(which redis-server) -Dgraphite.tag=<Graphite Version to Test on, default: latest>
```

Add new test canary config:
- Add config json file to integration-test/resources folder and run test with file name passed in

```bash
./gradlew kayenta-graphite:integrationTest -Dcanaryconfig.file=integration-test-canary-config.json -Dredis.path=$(which redis-server) -Dgraphite.tag=<Graphite Version to Test on, default: latest>
```

Also, can change the value of marginal and pass by pass in value by
```bash
-Dcanary.marginal=50 
-Dcanary.pass=75
```