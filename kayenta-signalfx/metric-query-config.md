Example metric configuration for a canary config, in yaml for readability.

See [The integration test canary-config json](src/integration-test/resources/integration-test-canary-config.json) for a real example.

```yaml
name: Error Rate for /v1/some-endpoint
query:
  metricName: kayenta.integration-test.internal-server-errors
  queryPairs: # [Optional] Can be dimensions, properties, or tags (Use tag as key for tags).
  - key: uri
    value: /v1/some-endpoint
  - key: status_code
    value: "5*"
  # Aggregate the N time series across each instance in a cluster to a single series, this gets used in the SignalFlow program
  # Supported options are the stream method that support aggregation see: https://developers.signalfx.com/reference#signalflow-stream-methods-1
  aggregationMethod: sum # [Optional] Defaults to mean
  serviceType: signalfx
  type: signalfx
analysisConfigurations:
  canary:
    direction: increase
    # Fail the canary if server errors increase.
    critical: true
groups:
- Integration Test Group
scopeName: default
```
