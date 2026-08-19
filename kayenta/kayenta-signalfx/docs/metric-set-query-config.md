### SignalFxCanaryMetricSetQueryConfig (CanaryMetricSetQueryConfig)
SignalFx specific query configurations.
See [The integration test canary-config json](../src/integration-test/resources/integration-test-canary-config.json) for a real example.
#### Properties
- `metricName` **requests.count** (string, required) - Metric name.
- `queryPairs` (array[[QueryPair](#query-pairs)], optional, **deprecated**) - List of query pairs. Deprecated in favor of `customInlineTemplate`/`customFilterTemplate`; planned for removal in a future release.
- `aggregationMethod` (enum[string], optional, **deprecated**) - How to aggregate each time series of collected data to a single data point. Defaults to mean. Deprecated in favor of `customInlineTemplate`/`customFilterTemplate`; planned for removal in a future release.
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
- `customInlineTemplate` (string, optional): Allows you to write your own SignalFlow program. The `${scope}` and `${location}` variable bindings are implicitly available, in addition to any `extendedScopeParams` supplied on the canary scope.
- `customFilterTemplate` (string, optional): Refers by name to an entry in the canary config's top-level `templates` map, expanded with the same variable bindings as `customInlineTemplate` above. Takes a back seat to `customInlineTemplate` if both are set.
- `type` (enum[string], required)
    - `signalfx`

<a name="query-pairs"></a>
### QueryPair (object)
Can be dimensions, properties, or tags (for tags, use tag as key).
#### Properties
- `key` **uri** (string, required) - key
- `value` **/v1/some-endpoint** - value
