### NewRelicCanaryMetricSetQueryConfig (CanaryMetricSetQueryConfig)
New Relic Insights specific query configurations.
#### Properties
- `select` **SELECT count(\*) FROM Transaction** (string, optional, **deprecated**) - NRQL query segment for WHERE clause. Deprecated in favor of `customInlineTemplate`/`customFilterTemplate`; planned for removal in a future release.
- `q` **httpStatusCode LIKE '5%'** (string, optional) - The full select query component of the NRQL statement. See the [NRQL Docs](https://docs.newrelic.com/docs/query-data/nrql-new-relic-query-language/getting-started/nrql-syntax-components-functions)
- `customInlineTemplate` **SELECT count(\*) FROM Transaction TIMESERIES 60 seconds SINCE ${startEpochSeconds} UNTIL ${endEpochSeconds} WHERE httpStatusCode LIKE '5%' AND someKeyThatIsSetDuringDeployment LIKE '${someKeyThatWasProvidedInExtendedScopeParams}' AND autoScalingGroupName LIKE '${scope}' AND region LIKE '${location}'** (string, optional) - Custom inline template use this or `select` + `q`, this allows you to write your own NRQL, please note that your NRQL must use the TIMESERIES keyword. Takes precedence over `customFilterTemplate` if both are set.
- `customFilterTemplate` (string, optional) - Refers by name to an entry in the canary config's top-level `templates` map, expanded with the same variable bindings as `customInlineTemplate` above (plus `${startEpochSeconds}`/`${endEpochSeconds}`).
- `type` (enum[string], required)
    - `newrelic`

