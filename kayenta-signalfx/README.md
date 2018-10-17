# Kayenta SignalFx
This module adds support to Kayenta to use SignalFx as a metric source.

## Configuration

### Kayenta
```yaml
  signalfx:
    enabled: true
    accounts:
    - name: sfx-integration-test-account
      accessToken: ${kayenta.signalfx.apiKey}
      supportedTypes:
      - METRICS_STORE
```

### Canary Config
See [The metric query config](metric-query-config.md) page.

## Development

### Integration Tests
This module has an End to End test that starts Kayenta with an in-memory config store and embedded Redis.
It spawns threads that act like mock services that report metrics to SignalFx, there are three mock clusters.

- Control - the control cluster
- Healthy Experiment - a healthy experiment
- Unhealthy Experiment - a unhealthy experiment

The data flowing through SignalFx from these clusters can then be used for end to end testing.

Running the end to end integration tests.
Requires that you have a local redis-server installation compatible with Kayenta.

```bash
./gradlew kayenta-signalfx:integrationTest -Dkayenta.signalfx.apiKey=${SIGNALFX_API_TOKEN} -Dredis.path=$(which redis-server)
```
