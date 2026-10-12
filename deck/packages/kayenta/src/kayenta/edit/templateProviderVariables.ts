/**
 * Per-provider hints shown alongside the unified query template editor (see
 * `metricQueryTemplateEditor.tsx`), telling the user which `${variable}` bindings are implicitly
 * available for that provider's `query.template`, plus a short illustrative (not necessarily
 * byte-perfect) example query.
 *
 * The `example` is the single source of truth for starter text: it is the editor placeholder and is
 * also rendered into the help popovers (canary.help.ts). Each provider's backend module has a test
 * that runs its example through the real query builder, so keep them in sync.
 *
 * These come from each provider's backend `*MetricsService.java` (or equivalent) call to
 * `QueryConfigUtils.expandCustomFilter(canaryConfig, queryConfig, canaryScope, baseScopeAttributes)`
 * — `baseScopeAttributes` is the authoritative source of truth for a provider's variable names.
 */
export interface ITemplateProviderVariables {
  // Matches the object key below and the provider's HelpContentsRegistry key
  // (`canary.config.template.<serviceType>`, registered in canary.help.ts) -- kept as an explicit
  // field, rather than derived by reverse-lookup against the map, so callers that already have an
  // ITemplateProviderVariables value in hand can compute the right help id without needing the map.
  serviceType: string;
  variables: string[];
  example: string;
  note?: string;
}

// Confirmed from each provider's `baseScopeAttributes` array passed to
// `QueryConfigUtils.expandCustomFilter(...)` server-side (see kayenta-<provider>'s
// `*MetricsService.java`/`*QueryBuilderService.java`), except where noted otherwise below.
export const templateProviderVariables: { [serviceType: string]: ITemplateProviderVariables } = {
  datadog: {
    serviceType: 'datadog',
    // kayenta-datadog/.../DatadogMetricsService.java: baseScopeAttributes = {"scope", "location"}
    variables: ['scope', 'location'],
    example: 'avg:trace.express.request.duration{service:${scope},env:${location}}',
  },
  prometheus: {
    serviceType: 'prometheus',
    // kayenta-prometheus/.../PrometheusMetricsService.java uses the same expandCustomFilter helper.
    variables: ['project', 'resourceType', 'scope', 'location'],
    example: 'sum(rate(http_requests_total{autoscaling_group="${scope}"}[5m]))',
  },
  stackdriver: {
    serviceType: 'stackdriver',
    // kayenta-stackdriver/.../StackdriverMetricsService.java:
    // baseScopeAttributes = {"project", "resourceType", "scope", "location"}
    variables: ['project', 'resourceType', 'scope', 'location'],
    example:
      'metadata.user_labels."spinnaker-server-group"=${scope} AND metadata.user_labels."spinnaker-region"=${location}',
  },
  signalfx: {
    serviceType: 'signalfx',
    // kayenta-signalfx/.../SignalFxQueryBuilderService.java: baseScopeAttributes = {"scope", "location"}
    variables: ['scope', 'location'],
    example:
      "data('request.count', filters=filter('cluster', '${scope}') and filter('region', '${location}')).sum().publish()",
  },
  newrelic: {
    serviceType: 'newrelic',
    // kayenta-newrelic-insights/.../NewRelicQueryBuilderService.java:
    // baseScopeAttributes = {"scope", "location", "step"}
    variables: ['scope', 'location', 'step', 'startEpochSeconds', 'endEpochSeconds'],
    example:
      "SELECT count(*) FROM Transaction TIMESERIES 60 seconds SINCE ${startEpochSeconds} UNTIL ${endEpochSeconds} WHERE autoScalingGroupName LIKE '${scope}' AND region LIKE '${location}'",
  },
  atlas: {
    serviceType: 'atlas',
    // kayenta-atlas/.../AtlasMetricsService.java:
    // baseScopeAttributes = {"type", "deployment", "dataset", "environment", "accountId", "scope", "location"}
    variables: ['type', 'deployment', 'dataset', 'environment', 'accountId', 'scope', 'location'],
    example: 'name,requestsPerSecond,:eq,:list,(,nf.cluster,${scope},:eq,:cq,),:each',
  },
  graphite: {
    serviceType: 'graphite',
    // kayenta-graphite/.../GraphiteMetricsService.java: baseScopeAttributes = {"scope", "location"}
    variables: ['scope', 'location'],
    example: 'servers.${scope}.cpu',
  },
  wavefront: {
    serviceType: 'wavefront',
    // kayenta-wavefront/.../WavefrontMetricsService.java:
    // baseScopeAttributes = {"scope", "location", "granularity"}
    variables: ['scope', 'location', 'granularity'],
    example: 'avg(ts(requests.count, autoscaling_group=${scope}))',
  },
  influxdb: {
    serviceType: 'influxdb',
    // InfluxDB's query engine is bespoke (see InfluxDbQueryBuilder.java), not built on the shared
    // QueryConfigUtils.expandCustomFilter helper, so it exposes a fixed, different set of variable
    // names rather than arbitrary CanaryScope bean-property names.
    variables: ['timeFilter', 'scope', 'step'],
    note:
      'InfluxDB uses a fixed, different variable vocabulary than every other provider (whose variables are arbitrary CanaryScope bean-property names) — its query engine is bespoke, not the shared QueryConfigUtils-based one.',
    example: 'SELECT sum(count) FROM web_requests WHERE ${scope} AND ${timeFilter} GROUP BY time(${step})',
  },
};

export default templateProviderVariables;
