### AtlasCanaryMetricSetQueryConfig (CanaryMetricSetQueryConfig)
Atlas specific query configurations.

#### Properties
- `q` (string, required unless using a template, **deprecated**) - The Atlas Stack Language (`:list,...`) query fragment. Combined with the canary scope's derived `cq()` expression (based on `type`/`scope`) at query time. Deprecated in favor of `template`; planned for removal in a future release.
- `template` (string, optional): Allows you to write a complete Atlas query, replacing the `q` + scope composition entirely (there is no separate mandatory filter this gets ANDed onto). The `${type}`, `${deployment}`, `${dataset}`, `${environment}`, `${accountId}`, `${scope}`, and `${location}` variable bindings are implicitly available, in addition to any `extendedScopeParams` supplied on the canary scope. See example below:

   ```
   "template": "name,requestsPerSecond,:eq,:list,(,nf.cluster,${scope},:eq,:cq,),:each"
   ```

   The legacy JSON key `customInlineTemplate` is still accepted on read (via `@JsonAlias`) and loads into this same field, but is never written by current tooling.

- `customFilterTemplate` (string, optional, **deprecated**): Legacy way to refer by name to an entry in the canary config's top-level `templates` map, expanded with the same variable bindings as `template` above. Superseded by `template`; still resolved correctly for existing configs (`template` wins if both are set), but no longer written by current tooling -- new configs should set `template` directly. See example below:

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
