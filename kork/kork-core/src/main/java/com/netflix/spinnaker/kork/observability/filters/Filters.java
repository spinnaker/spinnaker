package com.netflix.spinnaker.kork.observability.filters;

import io.micrometer.core.instrument.config.MeterFilter;

public class Filters {

  /*
   * Removes the spinnaker created 'controller.invocations' metric to prefer the micrometer created 'http.server.requests' metric
   * They both contain http metrics, however http.server.requests is non-java / sb specific and allows for dashboards that can interoperate x-framework
   *
   * https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/#production-ready-metrics-spring-mvc
   *
   * Extra details are off by default and opt in
   * The preferred way to add percentiles, percentile histograms, and SLA boundaries is to apply the general
   * purpose property-based meter filter mechanism to this timer:
   *
   * management.metrics.distribution:
   *  percentiles[http.server.requests]: 0.95, 0.99
   *  percentiles-histogram[http.server.requests]: true
   *  sla[http.server.requests]: 10ms, 100ms
   *
   * See: https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/#per-meter-properties
   */
  public static final MeterFilter DENY_CONTROLLER_INVOCATIONS_METRICS =
      MeterFilter.denyNameStartsWith("controller.invocations");
}
