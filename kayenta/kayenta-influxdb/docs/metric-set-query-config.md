### InfluxdbCanaryMetricSetQueryConfig (CanaryMetricSetQueryConfig)
Influxdb specific query configurations.

**Note:** unlike every other provider in this repo, InfluxDB does **not** use the shared `QueryConfigUtils` template-expansion engine (which exposes each provider's scope bean properties, e.g. `${scope}`/`${location}`/etc., as bindable variables). InfluxDB keeps its own smaller, fixed token vocabulary — only `${timeFilter}`, `${scope}`, and `${step}` are ever substituted, regardless of what other properties the canary scope has. This is intentional, not a gap to be closed.

#### Properties
- `metricName` (string, optional, **deprecated**): The measurement name where metrics are stored. This field is **required** UNLESS using `customInlineTemplate` or `customFilterTemplate`. Deprecated in favor of `customInlineTemplate`/`customFilterTemplate`; planned for removal in a future release.

   ```
   "metricName": "cpu"
   ```
  
- `fields` (array[string], optional, **deprecated**): The list of field names that need to be included in query. This field is **required** UNLESS using `customInlineTemplate` or `customFilterTemplate`. Deprecated in favor of `customInlineTemplate`/`customFilterTemplate`; planned for removal in a future release. See example below:

   ```
   fields: [
      "count"
   ]
   ```
  
- `customInlineTemplate` (string, optional): This allows you to write your own IQL statement. `${scope}` and `${timeFilter}` variables are **required** in the IQL statement; `${step}` is substituted if present. Takes precedence over `customFilterTemplate` if both are set. See example below:

   ```
   customInlineTemplate: "SELECT sum(count) FROM cpu WHERE host = 'value1' AND ${scope} AND ${timeFilter} GROUP BY time(1m)"
   ```

- `customFilterTemplate` (string, optional): Refers by name to an entry in the canary config's top-level `templates` map. The resolved string is expanded using the **same** fixed `${timeFilter}`/`${scope}`/`${step}` token engine as `customInlineTemplate` above (not the shared `QueryConfigUtils` engine other providers use). See example below:

   ```
   "templates": {
     "cpuTemplate": "SELECT sum(count) FROM cpu WHERE host = 'value1' AND ${scope} AND ${timeFilter} GROUP BY time(1m)"
   }
   ```

   ```
   "query": {
     "type": "influxdb",
     "customFilterTemplate": "cpuTemplate"
   }
   ```

- `type` (enum[string], required)
    - `influxdb`
