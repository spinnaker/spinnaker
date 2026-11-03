### SignalFxCanaryMetricSetQueryConfig (CanaryMetricSetQueryConfig)
SignalFx specific query configurations.
See [The integration test canary-config json](../src/integration-test/resources/integration-test-canary-config.json) for a real example.
#### Properties
- `metricName` **requests.count** (string, required) - Metric name.
- `queryPairs` (array[[QueryPair](#query-pairs)], optional, **deprecated**) - List of query pairs. Deprecated in favor of `template`; planned for removal after the 2027.1.x release.
- `aggregationMethod` (enum[string], optional, **deprecated**) - How to aggregate each time series of collected data to a single data point. Defaults to mean. Deprecated in favor of `template`; planned for removal after the 2027.1.x release.
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
- `template` (string, optional): Allows you to write your own SignalFlow program. The `${scope}` and `${location}` variable bindings are implicitly available, in addition to any `extendedScopeParams` supplied on the canary scope. The legacy JSON key `customInlineTemplate` is still accepted on read (via `@JsonAlias`) and loads into this same field; it is also still written alongside `template` for compatibility with older Kayenta versions.
- `customFilterTemplate` (string, optional, **deprecated**): Legacy way to refer by name to an entry in the canary config's top-level `templates` map, expanded with the same variable bindings as `template` above. Still resolved correctly for existing configs (`template` wins if both are set), but new configs should set `template` directly.
- `type` (enum[string], required)
    - `signalfx`

<a name="query-pairs"></a>
### QueryPair (object)
Can be dimensions, properties, or tags (for tags, use tag as key).
#### Properties
- `key` **uri** (string, required) - key
- `value` **/v1/some-endpoint** - value
