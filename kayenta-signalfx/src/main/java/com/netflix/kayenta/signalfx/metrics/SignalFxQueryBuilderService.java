package com.netflix.kayenta.signalfx.metrics;

import com.google.common.annotations.VisibleForTesting;
import com.netflix.kayenta.canary.CanaryConfig;
import com.netflix.kayenta.canary.CanaryScope;
import com.netflix.kayenta.canary.providers.metrics.QueryConfigUtils;
import com.netflix.kayenta.canary.providers.metrics.QueryPair;
import com.netflix.kayenta.canary.providers.metrics.SignalFxCanaryMetricSetQueryConfig;
import com.netflix.kayenta.signalfx.canary.SignalFxCanaryScope;
import com.netflix.kayenta.signalfx.config.SignalFxScopeConfiguration;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class SignalFxQueryBuilderService {

  public String buildQuery(
      CanaryConfig canaryConfig,
      SignalFxCanaryMetricSetQueryConfig queryConfig,
      CanaryScope canaryScope,
      SignalFxScopeConfiguration scopeConfiguration,
      SignalFxCanaryScope signalFxCanaryScope) {

    String aggregationMethod =
        Optional.ofNullable(queryConfig.getAggregationMethod()).orElse("mean");
    List<QueryPair> queryPairs =
        Optional.ofNullable(queryConfig.getQueryPairs()).orElse(new LinkedList<>());

    String[] baseScopeAttributes = new String[] {"scope", "location"};

    try {
      String customFilter =
          QueryConfigUtils.expandCustomFilter(
              canaryConfig, queryConfig, canaryScope, baseScopeAttributes);

      return Optional.ofNullable(customFilter)
          .orElseGet(
              () ->
                  getSimpleProgram(
                      queryConfig,
                      scopeConfiguration,
                      signalFxCanaryScope,
                      aggregationMethod,
                      queryPairs));
    } catch (Exception e) {
      throw new RuntimeException("Failed to generate SignalFx SignalFlow program", e);
    }
  }

  @VisibleForTesting
  protected String getSimpleProgram(
      SignalFxCanaryMetricSetQueryConfig queryConfig,
      SignalFxScopeConfiguration scopeConfiguration,
      SignalFxCanaryScope signalFxCanaryScope,
      String aggregationMethod,
      List<QueryPair> queryPairs) {
    return SimpleSignalFlowProgramBuilder.create(
            queryConfig.getMetricName(), aggregationMethod, scopeConfiguration)
        .withQueryPairs(queryPairs)
        .withScope(signalFxCanaryScope)
        .build();
  }
}
