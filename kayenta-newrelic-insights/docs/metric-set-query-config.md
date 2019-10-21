### NewRelicCanaryMetricSetQueryConfig (CanaryMetricSetQueryConfig)
New Relic Insights specific query configurations.
#### Properties
- `select` **SELECT count(\*) FROM Transaction** (string, optional) - NRQL query segment for WHERE clause.
- `q` **httpStatusCode LIKE '5%'** (string, optional) - The full select query component of the NRQL statement. See the [NRQL Docs](https://docs.newrelic.com/docs/query-data/nrql-new-relic-query-language/getting-started/nrql-syntax-components-functions)
- `customInlineTemplate` **SELECT count(\*) FROM Transaction TIMESERIES 60 seconds SINCE ${startEpochSeconds} UNTIL ${endEpochSeconds} WHERE httpStatusCode LIKE '5%' AND someKeyThatIsSetDuringDeployment LIKE '${someKeyThatWasProvidedInExtendedScopeParams}' AND autoScalingGroupName LIKE '${scope}' AND region LIKE '${location}'** (string, optional) - Custom inline template use this or `select` + `q`, this allows you to write your own NRQL, please note that your NRQL must use the TIMESERIES keyword.
<!-- - `customFilterTemplate` (string, optional) **todo** // Need to consult with @duftler on how this works -->
- `type` (enum[string], required)
    - `newrelic`

