### GraphiteCanaryMetricSetQueryConfig (CanaryMetricSetQueryConfig)
Graphite specific query configurations.

#### Properties
- `metricName` **servers.\$scope.cpu** (string, required unless using a template, **deprecated**) - The Graphite metric path. The legacy `$scope`/`$location` tokens (no braces) are substituted directly into this field. Deprecated in favor of `template`; planned for removal in a future release.
- `template` (string, optional): Allows you to write your own Graphite target path. The `${scope}` and `${location}` variable bindings are implicitly available, in addition to any `extendedScopeParams` supplied on the canary scope. See example below:

   ```
   "template": "servers.${scope}.cpu"
   ```
   The legacy JSON key `customInlineTemplate` is still accepted on read (via `@JsonAlias`) and loads into this same field, but is never written by current tooling.

- `customFilterTemplate` (string, optional, **deprecated**): Legacy way to refer by name to an entry in the canary config's top-level `templates` map, expanded with the same variable bindings as `template` above. Still resolved correctly for existing configs (`template` wins if both are set), but no longer written by current tooling -- new configs should set `template` directly. See example below:

   ```
   "templates": {
     "cpuTemplate": "servers.${scope}.cpu"
   }
   ```

   ```
   "query": {
     "type": "graphite",
     "customFilterTemplate": "cpuTemplate"
   }
   ```

- `type` (enum[string], required)
    - `graphite`
