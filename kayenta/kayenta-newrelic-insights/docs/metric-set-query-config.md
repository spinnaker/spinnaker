### NewRelicCanaryMetricSetQueryConfig (CanaryMetricSetQueryConfig)
New Relic Insights specific query configurations.
#### Properties
- `select` **SELECT count(\*) FROM Transaction** (string, optional, **deprecated**) - NRQL query segment for WHERE clause. Deprecated in favor of `template`; planned for removal in a future release.
- `q` **httpStatusCode LIKE '5%'** (string, optional) - The full select query component of the NRQL statement. See the [NRQL Docs](https://docs.newrelic.com/docs/query-data/nrql-new-relic-query-language/getting-started/nrql-syntax-components-functions)
- `template` **SELECT count(\*) FROM Transaction TIMESERIES 60 seconds SINCE ${startEpochSeconds} UNTIL ${endEpochSeconds} WHERE httpStatusCode LIKE '5%' AND someKeyThatIsSetDuringDeployment LIKE '${someKeyThatWasProvidedInExtendedScopeParams}' AND autoScalingGroupName LIKE '${scope}' AND region LIKE '${location}'** (string, optional) - Use this or `select` + `q`, this allows you to write your own NRQL, please note that your NRQL must use the TIMESERIES keyword. Takes precedence over the legacy `customFilterTemplate` if both are set. The legacy JSON key `customInlineTemplate` is still accepted on read (via `@JsonAlias`) and loads into this same field, but is never written by current tooling.
- `customFilterTemplate` (string, optional, **deprecated**) - Legacy way to refer by name to an entry in the canary config's top-level `templates` map, expanded with the same variable bindings as `template` above (plus `${startEpochSeconds}`/`${endEpochSeconds}`). Still resolved correctly for existing configs (`template` wins if both are set), but no longer written by current tooling -- new configs should set `template` directly.
- `type` (enum[string], required)
    - `newrelic`

