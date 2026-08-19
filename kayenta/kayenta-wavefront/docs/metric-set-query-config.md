### WavefrontCanaryMetricSetQueryConfig (CanaryMetricSetQueryConfig)
Wavefront specific query configurations.

#### Properties
- `metricName` **requests.count** (string, required unless using a template, **deprecated**) - The Wavefront metric name. Deprecated in favor of `customInlineTemplate`/`customFilterTemplate`; planned for removal in a future release.
- `summarization` (string, required unless using a template, **deprecated**) - How to summarize points within a Wavefront reporting interval, e.g. `MEAN`. Deprecated in favor of `customInlineTemplate`/`customFilterTemplate`; planned for removal in a future release.
- `aggregate` (string, required unless using a template, **deprecated**) - Wrapping aggregation function applied around `ts(...)`, e.g. `avg`. Deprecated in favor of `customInlineTemplate`/`customFilterTemplate`; planned for removal in a future release.
- `customInlineTemplate` (string, optional): Allows you to write your own Wavefront query, replacing the `ts(...)`/`aggregate(...)` composition entirely (there is no separate mandatory filter this gets ANDed onto). The `${scope}`, `${location}`, and `${granularity}` variable bindings are implicitly available, in addition to any `extendedScopeParams` supplied on the canary scope. See example below:

   ```
   "customInlineTemplate": "avg(ts(requests.count, autoscaling_group=${scope}))"
   ```

- `customFilterTemplate` (string, optional): Refers by name to an entry in the canary config's top-level `templates` map, expanded with the same variable bindings as `customInlineTemplate` above. Takes a back seat to `customInlineTemplate` if both are set. See example below:

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
