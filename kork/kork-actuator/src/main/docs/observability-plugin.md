# Observability Module

## Attribution

This module was originally developed as the **Armory Observability Plugin**
by [Armory, Inc.](https://armory.io) and donated to the Spinnaker open-source
project in 2025.

**Breaking Change Notice:** If migrating from the Armory Observability Plugin,
note the following configuration changes:
- Endpoint: `/actuator/aop-prometheus` → `/actuator/prometheus`
- Property: `armory-recommended-filters-enabled` → `recommended-filters-enabled`
- Configuration prefix: `spinnaker.extensibility.plugins.Armory.ObservabilityPlugin.config` → `observability.config`
- Tag `lib` value: `aop` → `kork-observability`

---

## Overview
The observability functionality in `kork-actuator` provides Micrometer-based metrics collection and export for Prometheus, Datadog, and New Relic. It also applies common tags and optional filters to help manage metric cardinality.

This document describes how to enable and configure it safely. Configuration keys and endpoint names are taken directly from the code under `kork/kork-actuator/src/main/java/com/netflix/spinnaker/kork/actuator/observability/`.


## Configuration

All properties use the `observability` prefix. Spring Boot’s relaxed binding lets you use hyphen-case in YAML (e.g., `step-in-seconds`) for camelCase fields (`stepInSeconds`).


### Enable the Module
```yaml
observability:
  enabled: true  # Required to activate
  config:
    metrics:
      # Additional common tags added to every meter
      additional-tags:
        environment: production
        team: platform
```

### Prometheus
```yaml
observability:
  config:
    metrics:
      prometheus:
        enabled: true
        step-in-seconds: 30
        descriptions: false
        registry:
          recommended-filters-enabled: true
          default-tags-disabled: false
          excluded-metrics-prefix:
            - jvm.gc
```

- **Scrape endpoint**: `/actuator/prometheus`
- Ensure Spring Boot Actuator web exposure includes the endpoint id. Optionally, you can remap the path or serve on a dedicated management port:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
      path-mapping:
        prometheus: observability/metrics
  # Optional: run actuator on a separate port
  server:
    port: 9006
```
#### Per-meter distribution (optional)
Configure percentiles and histograms for specific meters, e.g. HTTP latency:

```yaml
management:
  metrics:
    distribution:
      percentiles[http.server.requests]: 0.95, 0.99
      percentiles-histogram[http.server.requests]: true
```


### Datadog
```yaml
observability:
  config:
    metrics:
      datadog:
        enabled: true
        api-key: ${DATADOG_API_KEY}          # REQUIRED for metrics ingestion
        application-key: ${DATADOG_APP_KEY}  # Optional for API operations; not required to ingest metrics
        uri: https://api.datadoghq.com       # Optional; default is https://api.datadoghq.com
        batch-size: 10000
        connect-duration-seconds: 5          # Default 5
        read-duration-seconds: 5             # Default 5
        registry:
          default-tags-disabled: false
          # Note: Datadog step and proxy settings are not currently applied by the registry wrapper. Batch size and connect/read timeouts are applied; step uses Micrometer defaults.
```

- **Security**: Friendly reminder not to commit API keys. Prefer environment variables or a secrets manager.


### New Relic
```yaml
observability:
  config:
    metrics:
      newrelic:
        enabled: true
        api-key: ${NEWRELIC_API_KEY}         # REQUIRED
        uri: https://metric-api.newrelic.com/ # Optional; default shown
        step-in-seconds: 30                  # Optional; default 30
        batch-size: 10000
        # Optional controls
        num-threads: 2
        connect-duration-seconds: 5
        read-duration-seconds: 5
        enable-audit-mode: false              # Logs payloads sent by the Telemetry SDK
        # Optional proxy settings
        proxy-host: your-proxy.example
        proxy-port: 8080
        registry:
          default-tags-disabled: false
```

Note: There is no `account-id` property in the current implementation.


## Default Tags
Added by `TagsService` unless disabled per provider via `registry.default-tags-disabled`:
- `spinSvc` : Application name (from `spring.application.name` or build info)
- `ossSpinSvcVer` : Application version (from `META-INF/build-info.properties` when present)
- `version` : Resolved version (falls back to resolver when available)
- `hostname` : System hostname
- `lib` : `kork-observability` (library identifier)

Disable per provider:
```yaml
observability.config.metrics.<provider>.registry.default-tags-disabled: true
```

User-supplied `additional-tags` are merged with the defaults.


## Filters

### Recommended Filters
Enable to reduce metric cardinality by removing legacy Spinnaker controller metrics in favor of standard Spring metrics (e.g., `http.server.requests`). Specifically includes `deny-controller-invocations`.
```yaml
observability:
  config:
    metrics:
      <provider>:
        registry:
          recommended-filters-enabled: true
```

### Custom Prefix Exclusions
Deny metrics that start with specific prefixes.
```yaml
observability:
  config:
    metrics:
      <provider>:
        registry:
          excluded-metrics-prefix:
            - jvm.gc
            - tomcat.sessions
```


## Integration with Spinnaker

### Kork services
If a service includes `kork-actuator`, enabling `observability.enabled=true` activates the configuration.

### Spectator compatibility
The composite registry (`ObservabilityCompositeRegistry`) collects all enabled registries. If none are enabled, it falls back to `SimpleMeterRegistry`, which maintains compatibility with Spectator-based setups.


## Migration from Spinnaker Monitoring Daemon
When moving from the Spinnaker Monitoring Daemon to this module:
- **Scrape path**: Switch to `/actuator/prometheus` (or your custom mapping).
- **Metric names/types**: Micrometer’s Prometheus registry uses naming and types that differ from the monitoring daemon (for example, `summary` metrics, and standardized `http.server.requests`). Review and update dashboards and alerts accordingly.
- **Controller metrics**: Prefer `http.server.requests`. If you previously relied on `controller.invocations`, enable recommended filters or adjust dashboards.
- **Actuator exposure**: Ensure `management.endpoints.web.exposure.include` contains `prometheus`.
- **Disable old pipeline**: Remove monitoring-daemon scraping or port exposure for services where this module is enabled to avoid duplicate ingestion and extra costs.


## Troubleshooting

### Metrics not appearing
- **Check** `observability.enabled=true`.
- **Verify** provider-specific `enabled: true`.
- **Validate** API keys (Datadog/New Relic) are present and non-empty.
- **Inspect logs** for initialization errors from suppliers or the composite registry.

### Endpoint not found
- Ensure Actuator web exposure includes the endpoint id:
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
```
- If you remapped the path, confirm `management.endpoints.web.path-mapping.prometheus` matches your scrape config.


## Security Considerations
- **API Keys**: Use environment variables or a secrets manager; never commit credentials.
- **Endpoint Access**: Restrict `/actuator/prometheus` via Spring Security or network policy.
- **Sensitive Metrics**: Use filters to exclude metrics that may contain PII.
- **Transport**: Use TLS when metrics traverse untrusted networks.


## Performance Tuning
- **Scrape/step alignment**: Set `step-in-seconds` to align with your scrape interval.
- **Prefix exclusions**: Use `excluded-metrics-prefix` to reduce high-cardinality metrics.
- **Recommended filters**: Enable `recommended-filters-enabled` to reduce DPM and unnecessary series.


## Package Structure
- config/ : Spring configuration and auto-configuration
- model/ : Configuration properties POJOs
- registry/ : Composite registry and registry customizers
- service/ : Tag generation and meter filter assembly
- filters/ : Predefined meter filters (e.g., deny controller.invocations)
- prometheus/ : Prometheus registry and scrape endpoint implementation
- datadog/ : Datadog registry supplier and config
- newrelic/ : New Relic registry supplier and config
- version/ : Version resolution utilities


## Related Resources
- Community dashboards: https://github.com/uneeq-oss/spinnaker-mixin
  - Community-maintained; metric names and labels may differ. Validate against your deployment.
