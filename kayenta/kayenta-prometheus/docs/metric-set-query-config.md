### PrometheusCanaryMetricSetQueryConfig (CanaryMetricSetQueryConfig)
Prometheus specific query configurations.

#### Properties
- `resourceType` (string, optional, **deprecated**) - Resource type (`gce_instance` or `aws_ec2_instance`) used to build the default scope label bindings. Deprecated in favor of `template`; planned for removal in a future release.
- `metricName` **cpu_usage** (string, required unless using a template, **deprecated**) - The Prometheus metric name. Deprecated in favor of `template`; planned for removal in a future release.
- `labelBindings` (array[string], optional, **deprecated**) - Additional PromQL label matchers, e.g. `container_name=~"myapp-.*"`. Deprecated in favor of `template`; planned for removal in a future release.
- `groupByFields` (array[string], optional, **deprecated**) - Fields to group by, e.g. `pod_name`. Deprecated in favor of `template`; planned for removal in a future release.
- `template` (string, optional): Allows you to write your own PromQL query. The `${project}`, `${resourceType}`, `${scope}`, and `${location}` variable bindings are implicitly available, in addition to any `extendedScopeParams` supplied on the canary scope. Prefix the expanded result with `PromQL:` to use it as a complete PromQL expression verbatim. See example below:

   ```
   "template": "PromQL:sum(rate(http_requests_total{autoscaling_group=\"${scope}\"}[5m]))"
   ```
   The legacy JSON key `customInlineTemplate` is still accepted on read (via `@JsonAlias`) and loads into this same field, but is never written by current tooling.

- `customFilterTemplate` (string, optional, **deprecated**): Legacy way to refer by name to an entry in the canary config's top-level `templates` map, expanded with the same variable bindings as `template` above. Still resolved correctly for existing configs (`template` wins if both are set), but no longer written by current tooling -- new configs should set `template` directly. See example below:

   ```
   "templates": {
     "requestRateTemplate": "PromQL:sum(rate(http_requests_total{autoscaling_group=\"${scope}\"}[5m]))"
   }
   ```

   ```
   "query": {
     "type": "prometheus",
     "customFilterTemplate": "requestRateTemplate"
   }
   ```

- `type` (enum[string], required)
    - `prometheus`
