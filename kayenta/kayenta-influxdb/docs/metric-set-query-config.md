### InfluxdbCanaryMetricSetQueryConfig (CanaryMetricSetQueryConfig)
Influxdb specific query configurations.

#### Properties
- `metricName` (string, optional): The measurement name where metrics are stored. This field is **required** UNLESS using `customInlineTemplate`.

   ```
   "metricName": "cpu"
   ```
  
- `fields` (array[string], optional): The list of field names that need to be included in query. This field is **required** UNLESS using `customInlineTemplate`. See example below:

   ```
   fields: [
      "count"
   ]
   ```
  
- `customInlineTemplate` (string, optional): This allows you to write your own IQL statement. `${scope}` and `{timeFilter}` variables are **required** in the IQL statement. See example below:

   ```
   customInlineTemplate: "SELECT sum(count) FROM cpu WHERE host = 'value1' AND ${scope} AND ${timeFilter} GROUP BY time(1m)"
   ```
  
- `type` (enum[string], required)
    - `influxdb`
