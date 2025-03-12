### SignalFxCanaryMetricSetQueryConfig (CanaryMetricSetQueryConfig)
SignalFx specific query configurations.
See [The integration test canary-config json](../src/integration-test/resources/integration-test-canary-config.json) for a real example.
#### Properties
- `metricName` **requests.count** (string, required) - Metric name.
- `queryPairs` (array[[QueryPair](#query-pairs)], optional) - List of query pairs. 
- `aggregationMethod` (enum[string], optional) - How to aggregate each time series of collected data to a single data point. Defaults to mean.
  - `bottom`
  - `count`
  - `max`
  - `mean`
  - `mean_plus_stddev`
  - `median`
  - `min`
  - `random`
  - `sample_stddev`
  - `sample_variance`
  - `size`
  - `stddev`
  - `sum`
  - `top`
  - `variance`
- `type` (enum[string], required)
    - `signalfx`

<a name="query-pairs"></a>
### QueryPair (object)
Can be dimensions, properties, or tags (for tags, use tag as key).
#### Properties
- `key` **uri** (string, required) - key
- `value` **/v1/some-endpoint** - value
