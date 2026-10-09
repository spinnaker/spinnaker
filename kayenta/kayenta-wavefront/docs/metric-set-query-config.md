### WavefrontCanaryMetricSetQueryConfig (CanaryMetricSetQueryConfig)
Wavefront specific query configurations.

#### Properties
- `metricName` **requests.count** (string, required unless using a template, **deprecated**) - The Wavefront metric name. Deprecated in favor of `template`; planned for removal in a future release.
- `summarization` (string, required unless using a template, **deprecated**) - How to summarize points within a Wavefront reporting interval, e.g. `MEAN`. Deprecated in favor of `template`; planned for removal in a future release.
- `aggregate` (string, required unless using a template, **deprecated**) - Wrapping aggregation function applied around `ts(...)`, e.g. `avg`. Deprecated in favor of `template`; planned for removal in a future release.
- `template` (string, optional): Allows you to write your own Wavefront query, replacing the `ts(...)`/`aggregate(...)` composition entirely (there is no separate mandatory filter this gets ANDed onto). The `${scope}`, `${location}`, and `${granularity}` variable bindings are implicitly available, in addition to any `extendedScopeParams` supplied on the canary scope. See example below:

   ```
   "template": "avg(ts(requests.count, autoscaling_group=${scope}))"
   ```
   The legacy JSON key `customInlineTemplate` is still accepted on read (via `@JsonAlias`) and loads into this same field, but is never written by current tooling.

- `customFilterTemplate` (string, optional, **deprecated**): Legacy way to refer by name to an entry in the canary config's top-level `templates` map, expanded with the same variable bindings as `template` above. Still resolved correctly for existing configs (`template` wins if both are set), but no longer written by current tooling -- new configs should set `template` directly. See example below:

   ```
   "templates": {
     "requestsTemplate": "avg(ts(requests.count, autoscaling_group=${scope}))"
   }
   ```

   ```
   "query": {
     "type": "wavefront",
     "customFilterTemplate": "requestsTemplate"
   }
   ```

- `type` (enum[string], required)
    - `wavefront`
