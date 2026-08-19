/**
 * Per-provider hints shown alongside the unified query template editor (see
 * `metricQueryTemplateEditor.tsx`), telling the user which `${variable}` bindings are implicitly
 * available for that provider's `query.template`, plus a short illustrative (not necessarily
 * byte-perfect) example query.
 *
 * These come from each provider's backend `*MetricsService.java` (or equivalent) call to
 * `QueryConfigUtils.expandCustomFilter(canaryConfig, queryConfig, canaryScope, baseScopeAttributes)`
 * — `baseScopeAttributes` is the authoritative source of truth for a provider's variable names.
 */
export interface ITemplateProviderVariables {
  variables: string[];
  example: string;
  note?: string;
}

// Confirmed from each provider's `baseScopeAttributes` array passed to
// `QueryConfigUtils.expandCustomFilter(...)` server-side (see kayenta-<provider>'s
// `*MetricsService.java`/`*QueryBuilderService.java`), except where noted otherwise below.
export const templateProviderVariables: { [serviceType: string]: ITemplateProviderVariables } = {
  datadog: {
    // kayenta-datadog/.../DatadogMetricsService.java: baseScopeAttributes = {"scope", "location"}
    variables: ['scope', 'location'],
    example: 'avg:trace.express.request.duration{service:${scope},env:${location}}',
  },
  prometheus: {
    // kayenta-prometheus/.../PrometheusMetricsService.java uses the same expandCustomFilter helper.
    variables: ['scope', 'location'],
    example: 'rate(http_requests_total{service="${scope}", region="${location}"}[5m])',
  },
  stackdriver: {
    // kayenta-stackdriver/.../StackdriverMetricsService.java:
    // baseScopeAttributes = {"project", "resourceType", "scope", "location"}
    variables: ['project', 'resourceType', 'scope', 'location'],
    example:
      'metric.type="compute.googleapis.com/instance/cpu/utilization" AND resource.type="${resourceType}" AND resource.label.project_id="${project}"',
  },
  signalfx: {
    // kayenta-signalfx/.../SignalFxQueryBuilderService.java: baseScopeAttributes = {"scope", "location"}
    variables: ['scope', 'location'],
    example:
      "data('request.count', filters=filter('cluster', '${scope}') and filter('region', '${location}')).sum().publish()",
  },
  newrelic: {
    // kayenta-newrelic-insights/.../NewRelicQueryBuilderService.java:
    // baseScopeAttributes = {"scope", "location", "step"}
    variables: ['scope', 'location', 'step'],
    example: "SELECT average(duration) FROM Transaction WHERE appName = '${scope}' SINCE ${step} minutes ago",
  },
  atlas: {
    // kayenta-atlas/.../AtlasMetricsService.java:
    // baseScopeAttributes = {"type", "deployment", "dataset", "environment", "accountId", "scope", "location"}
    variables: ['type', 'deployment', 'dataset', 'environment', 'accountId', 'scope', 'location'],
    example: 'name,requestLatency,:eq,nf.cluster,${scope},:eq,:and,nf.account,${accountId},:eq,:and',
  },
  graphite: {
    // kayenta-graphite/.../GraphiteMetricsService.java: baseScopeAttributes = {"scope", "location"}
    variables: ['scope', 'location'],
    example: 'summarize(scaleToSeconds(stats.${scope}.${location}.request.count, 1), "5min", "sum")',
  },
  wavefront: {
    // kayenta-wavefront/.../WavefrontMetricsService.java:
    // baseScopeAttributes = {"scope", "location", "granularity"}
    variables: ['scope', 'location', 'granularity'],
    example: 'ts(request.count, source=${scope} and region=${location}), ${granularity}',
  },
  influxdb: {
    // InfluxDB's query engine is bespoke (see InfluxDbQueryBuilder.java), not built on the shared
    // QueryConfigUtils.expandCustomFilter helper, so it exposes a fixed, different set of variable
    // names rather than arbitrary CanaryScope bean-property names.
    variables: ['timeFilter', 'scope', 'step'],
    note:
      'InfluxDB uses a fixed, different variable vocabulary than every other provider (whose variables are arbitrary CanaryScope bean-property names) — its query engine is bespoke, not the shared QueryConfigUtils-based one.',
    example: 'SELECT mean("value") FROM "cpu" WHERE ${timeFilter} AND "host" = \'${scope}\' GROUP BY time(${step})',
  },
};

export default templateProviderVariables;
