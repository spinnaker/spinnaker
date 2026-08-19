### StackdriverCanaryMetricSetQueryConfig (CanaryMetricSetQueryConfig)
Stackdriver specific query configurations.

#### Properties
- `resourceType` **gce_instance** (string, optional, **deprecated**) - Monitored resource type. Deprecated in favor of `customInlineTemplate`/`customFilterTemplate`; planned for removal in a future release.
- `metricType` **compute.googleapis.com/instance/cpu/utilization** (string, required unless using a template, **deprecated**) - The Stackdriver metric type. Deprecated in favor of `customInlineTemplate`/`customFilterTemplate`; planned for removal in a future release.
- `crossSeriesReducer` (string, optional, **deprecated**) - Aggregation cross-series reducer, e.g. `REDUCE_MEAN`. Deprecated in favor of `customInlineTemplate`/`customFilterTemplate`; planned for removal in a future release.
- `perSeriesAligner` (string, optional, **deprecated**) - Aggregation per-series aligner, e.g. `ALIGN_MEAN`. Deprecated in favor of `customInlineTemplate`/`customFilterTemplate`; planned for removal in a future release.
- `groupByFields` (array[string], optional, **deprecated**) - Fields to group by. Deprecated in favor of `customInlineTemplate`/`customFilterTemplate`; planned for removal in a future release.
- `customInlineTemplate` (string, optional): Allows you to write your own Stackdriver monitoring filter. The `${project}`, `${resourceType}`, `${scope}`, and `${location}` variable bindings are implicitly available, in addition to any `extendedScopeParams` supplied on the canary scope. The expanded result is ANDed onto the mandatory `metric.type="..." AND resource.type=...` base filter. See example below:

   ```
   "customInlineTemplate": "metadata.user_labels.\"spinnaker-server-group\"=${scope} AND metadata.user_labels.\"spinnaker-region\"=${location}"
   ```

- `customFilterTemplate` (string, optional): Refers by name to an entry in the canary config's top-level `templates` map, expanded with the same variable bindings as `customInlineTemplate` above. Takes a back seat to `customInlineTemplate` if both are set. See example below:

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
