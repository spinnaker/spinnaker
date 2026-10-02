### InfluxdbCanaryMetricSetQueryConfig (CanaryMetricSetQueryConfig)
Influxdb specific query configurations.

**Note:** unlike every other provider in this repo, InfluxDB does **not** use the shared `QueryConfigUtils` template-expansion engine (which exposes each provider's scope bean properties, e.g. `${scope}`/`${location}`/etc., as bindable variables). InfluxDB keeps its own smaller, fixed token vocabulary — only `${timeFilter}`, `${scope}`, and `${step}` are ever substituted, regardless of what other properties the canary scope has. This is intentional, not a gap to be closed.

#### Properties
- `metricName` (string, optional, **deprecated**): The measurement name where metrics are stored. This field is **required** UNLESS using `template` or the legacy `customFilterTemplate`. Deprecated in favor of `template`; planned for removal in a future release.

   ```
   "metricName": "cpu"
   ```
  
- `fields` (array[string], optional, **deprecated**): The list of field names that need to be included in query. This field is **required** UNLESS using `template` or the legacy `customFilterTemplate`. Deprecated in favor of `template`; planned for removal in a future release. See example below:

   ```
   fields: [
      "count"
   ]
   ```
  
- `template` (string, optional): This allows you to write your own IQL statement. `${scope}` and `${timeFilter}` variables are **required** in the IQL statement; `${step}` is substituted if present. Takes precedence over the legacy `customFilterTemplate` if both are set. See example below:

   ```
   template: "SELECT sum(count) FROM cpu WHERE host = 'value1' AND ${scope} AND ${timeFilter} GROUP BY time(1m)"
   ```

   The legacy JSON key `customInlineTemplate` is still accepted on read (via `@JsonAlias`) and loads into this same field, but is never written by current tooling.

- `customFilterTemplate` (string, optional, **deprecated**): Legacy way to refer by name to an entry in the canary config's top-level `templates` map. The resolved string is expanded using the **same** fixed `${timeFilter}`/`${scope}`/`${step}` token engine as `template` above (not the shared `QueryConfigUtils` engine other providers use). Still resolved correctly for existing configs (`template` wins if both are set), but no longer written by current tooling -- new configs should set `template` directly. See example below:

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
