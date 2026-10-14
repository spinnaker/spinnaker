### StackdriverCanaryMetricSetQueryConfig (CanaryMetricSetQueryConfig)
Stackdriver specific query configurations.

#### Properties
- `resourceType` **gce_instance** (string, optional, **deprecated**) - Monitored resource type. Deprecated in favor of `template`; planned for removal after the 2027.1.x release.
- `metricType` **compute.googleapis.com/instance/cpu/utilization** (string, required) - The Stackdriver metric type. Always required, including when using a `template`, because the query always starts from `metric.type="<metricType>" AND resource.type=<resourceType>`.
- `crossSeriesReducer` (string, optional, **deprecated**) - Aggregation cross-series reducer, e.g. `REDUCE_MEAN`. Deprecated in favor of `template`; planned for removal after the 2027.1.x release.
- `perSeriesAligner` (string, optional, **deprecated**) - Aggregation per-series aligner, e.g. `ALIGN_MEAN`. Deprecated in favor of `template`; planned for removal after the 2027.1.x release.
- `groupByFields` (array[string], optional, **deprecated**) - Fields to group by. Deprecated in favor of `template`; planned for removal after the 2027.1.x release.
- `template` (string, optional): Allows you to write your own Stackdriver monitoring filter. The `${project}`, `${resourceType}`, `${scope}`, and `${location}` variable bindings are implicitly available, in addition to any `extendedScopeParams` supplied on the canary scope. The expanded result is ANDed onto the mandatory `metric.type="..." AND resource.type=...` base filter. See example below:

   ```
   "template": "metadata.user_labels.\"spinnaker-server-group\"=${scope} AND metadata.user_labels.\"spinnaker-region\"=${location}"
   ```
   The legacy JSON key `customInlineTemplate` is still accepted on read (via `@JsonAlias`) and loads into this same field; it is also still written alongside `template` for compatibility with older Kayenta versions.

- `customFilterTemplate` (string, optional, **deprecated**): Legacy way to refer by name to an entry in the canary config's top-level `templates` map, expanded with the same variable bindings as `template` above. Still resolved correctly for existing configs (`template` wins if both are set), but new configs should set `template` directly. See example below:

   ```
   "templates": {
     "scopeTemplate": "metadata.user_labels.\"spinnaker-server-group\"=${scope} AND metadata.user_labels.\"spinnaker-region\"=${location}"
   }
   ```

   ```
   "query": {
     "type": "stackdriver",
     "metricType": "compute.googleapis.com/instance/cpu/utilization",
     "customFilterTemplate": "scopeTemplate"
   }
   ```

- `type` (enum[string], required)
    - `stackdriver`
