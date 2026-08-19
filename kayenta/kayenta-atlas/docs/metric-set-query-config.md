### AtlasCanaryMetricSetQueryConfig (CanaryMetricSetQueryConfig)
Atlas specific query configurations.

#### Properties
- `q` (string, required unless using a template, **deprecated**) - The Atlas Stack Language (`:list,...`) query fragment. Combined with the canary scope's derived `cq()` expression (based on `type`/`scope`) at query time. Deprecated in favor of `customInlineTemplate`/`customFilterTemplate`; planned for removal in a future release.
- `customInlineTemplate` (string, optional): Allows you to write a complete Atlas query, replacing the `q` + scope composition entirely (there is no separate mandatory filter this gets ANDed onto). The `${type}`, `${deployment}`, `${dataset}`, `${environment}`, `${accountId}`, `${scope}`, and `${location}` variable bindings are implicitly available, in addition to any `extendedScopeParams` supplied on the canary scope. See example below:

   ```
   "customInlineTemplate": "name,requestsPerSecond,:eq,:list,(,nf.cluster,${scope},:eq,:cq,),:each"
   ```

- `customFilterTemplate` (string, optional): Refers by name to an entry in the canary config's top-level `templates` map, expanded with the same variable bindings as `customInlineTemplate` above. Takes a back seat to `customInlineTemplate` if both are set. See example below:

   ```
   "templates": {
     "requestsTemplate": "name,requestsPerSecond,:eq,:list,(,nf.cluster,${scope},:eq,:cq,),:each"
   }
   ```

   ```
   "query": {
     "type": "atlas",
     "customFilterTemplate": "requestsTemplate"
   }
   ```

- `type` (enum[string], required)
    - `atlas`
