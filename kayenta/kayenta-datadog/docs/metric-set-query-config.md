### DatadogCanaryMetricSetQueryConfig (CanaryMetricSetQueryConfig)
Datadog specific query configurations.

#### Properties
- `metricName` **avg:system.cpu.user** (string, optional, **deprecated**) - The Datadog metric query, e.g. `avg:system.cpu.user{*}`. The scope is appended automatically. Deprecated in favor of `template`; planned for removal in a future release.
- `template` (string, optional): Allows you to write your own Datadog query. The `${scope}` and `${location}` variable bindings are implicitly available, in addition to any `extendedScopeParams` supplied on the canary scope. See example below:

   ```
   "template": "avg:system.cpu.user{autoscaling_group:${scope}}"
   ```
   The legacy JSON key `customInlineTemplate` is still accepted on read (via `@JsonAlias`) and loads into this same field, but is never written by current tooling.

- `customFilterTemplate` (string, optional, **deprecated**): Legacy way to refer by name to an entry in the canary config's top-level `templates` map, expanded with the same variable bindings as `template` above. Still resolved correctly for existing configs (`template` wins if both are set), but no longer written by current tooling -- new configs should set `template` directly. See example below:

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
