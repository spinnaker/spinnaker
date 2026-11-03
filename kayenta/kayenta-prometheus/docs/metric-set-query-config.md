### PrometheusCanaryMetricSetQueryConfig (CanaryMetricSetQueryConfig)
Prometheus specific query configurations.

#### Properties
- `resourceType` (string, optional, **deprecated**) - Resource type (`gce_instance` or `aws_ec2_instance`) used to build the default scope label bindings. Deprecated in favor of `template`; planned for removal after the 2027.1.x release.
- `metricName` **cpu_usage** (string, required unless using a template, **deprecated**) - The Prometheus metric name. Deprecated in favor of `template`; planned for removal after the 2027.1.x release.
- `labelBindings` (array[string], optional, **deprecated**) - Additional PromQL label matchers, e.g. `container_name=~"myapp-.*"`. Deprecated in favor of `template`; planned for removal after the 2027.1.x release.
- `groupByFields` (array[string], optional, **deprecated**) - Fields to group by, e.g. `pod_name`. Deprecated in favor of `template`; planned for removal after the 2027.1.x release.
- `template` (string, optional): Allows you to write your own PromQL query. The `${project}`, `${resourceType}`, `${scope}`, and `${location}` variable bindings are implicitly available, in addition to any `extendedScopeParams` supplied on the canary scope. The expanded result is used verbatim as a complete PromQL expression. A leading `PromQL:` marker (required by earlier versions) is still accepted and stripped. One legacy case is preserved: if the config **also** has a guided `metricName` and the template has no `PromQL:` marker, the template is treated as the old comma-separated list of label matchers (e.g. `app="foo",env="prod"`) and combined with the guided fields as before. See example below:

   ```
   "template": "sum(rate(http_requests_total{autoscaling_group=\"${scope}\"}[5m]))"
   ```
   The legacy JSON key `customInlineTemplate` is still accepted on read (via `@JsonAlias`) and loads into this same field; it is also still written alongside `template` for compatibility with older Kayenta versions.

- `customFilterTemplate` (string, optional, **deprecated**): Legacy way to refer by name to an entry in the canary config's top-level `templates` map, expanded with the same variable bindings as `template` above. Still resolved correctly for existing configs (`template` wins if both are set), but new configs should set `template` directly. See example below:

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
