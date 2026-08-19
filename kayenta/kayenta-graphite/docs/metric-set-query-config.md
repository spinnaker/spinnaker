### GraphiteCanaryMetricSetQueryConfig (CanaryMetricSetQueryConfig)
Graphite specific query configurations.

#### Properties
- `metricName` **servers.\$scope.cpu** (string, required unless using a template, **deprecated**) - The Graphite metric path. The legacy `$scope`/`$location` tokens (no braces) are substituted directly into this field. Deprecated in favor of `customInlineTemplate`/`customFilterTemplate`; planned for removal in a future release.
- `customInlineTemplate` (string, optional): Allows you to write your own Graphite target path. The `${scope}` and `${location}` variable bindings are implicitly available, in addition to any `extendedScopeParams` supplied on the canary scope. See example below:

   ```
   "customInlineTemplate": "servers.${scope}.cpu"
   ```

- `customFilterTemplate` (string, optional): Refers by name to an entry in the canary config's top-level `templates` map, expanded with the same variable bindings as `customInlineTemplate` above. Takes a back seat to `customInlineTemplate` if both are set. See example below:

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
