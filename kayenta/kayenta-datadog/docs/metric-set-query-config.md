### DatadogCanaryMetricSetQueryConfig (CanaryMetricSetQueryConfig)
Datadog specific query configurations.

#### Properties
- `metricName` **avg:system.cpu.user** (string, optional, **deprecated**) - The Datadog metric query, e.g. `avg:system.cpu.user{*}`. The scope is appended automatically. Deprecated in favor of `customInlineTemplate`/`customFilterTemplate`; planned for removal in a future release.
- `customInlineTemplate` (string, optional): Allows you to write your own Datadog query. The `${scope}` and `${location}` variable bindings are implicitly available, in addition to any `extendedScopeParams` supplied on the canary scope. See example below:

   ```
   "customInlineTemplate": "avg:system.cpu.user{autoscaling_group:${scope}}"
   ```

- `customFilterTemplate` (string, optional): Refers by name to an entry in the canary config's top-level `templates` map, expanded with the same variable bindings as `customInlineTemplate` above. Takes a back seat to `customInlineTemplate` if both are set. See example below:

   ```
   "templates": {
     "cpuTemplate": "avg:system.cpu.user{autoscaling_group:${scope}}"
   }
   ```

   ```
   "query": {
     "type": "datadog",
     "customFilterTemplate": "cpuTemplate"
   }
   ```

- `type` (enum[string], required)
    - `datadog`
