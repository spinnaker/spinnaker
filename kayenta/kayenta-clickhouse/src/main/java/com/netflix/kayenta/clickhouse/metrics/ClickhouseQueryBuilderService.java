package com.netflix.kayenta.clickhouse.metrics;

import com.netflix.kayenta.canary.CanaryConfig;
import com.netflix.kayenta.canary.CanaryScope;
import com.netflix.kayenta.canary.providers.metrics.ClickhouseCanaryMetricSetQueryConfig;
import com.netflix.kayenta.canary.providers.metrics.QueryConfigUtils;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Resolves the SQL statement for a Clickhouse canary metric. There is no structured/programmatic
 * query builder here - {@link QueryConfigUtils#expandCustomFilter} is the only mechanism used, so a
 * {@code customInlineTemplate} or {@code customFilterTemplate} is required.
 */
@Component
public class ClickhouseQueryBuilderService {

  private static final String[] BASE_SCOPE_ATTRIBUTES = {"scope", "location", "step"};

  public String buildQuery(
      CanaryConfig canaryConfig,
      CanaryScope canaryScope,
      ClickhouseCanaryMetricSetQueryConfig queryConfig) {

    // `start`/`end` can't be used as base scope attribute names here since the template engine
    // would read the raw ISO-8601 Instant off the CanaryScope bean. Inject epoch-second values
    // into the extended scope params instead, so templates can reference ${startEpochSeconds} /
    // ${endEpochSeconds} directly in ClickHouse-friendly form.
    Map<String, String> originalParams =
        canaryScope.getExtendedScopeParams() == null
            ? new HashMap<>()
            : canaryScope.getExtendedScopeParams();
    Map<String, String> paramsWithEpochSeconds = new HashMap<>(originalParams);
    paramsWithEpochSeconds.put(
        "startEpochSeconds", String.valueOf(canaryScope.getStart().getEpochSecond()));
    paramsWithEpochSeconds.put(
        "endEpochSeconds", String.valueOf(canaryScope.getEnd().getEpochSecond()));
    canaryScope.setExtendedScopeParams(paramsWithEpochSeconds);

    String query;
    try {
      query =
          QueryConfigUtils.expandCustomFilter(
              canaryConfig, queryConfig, canaryScope, BASE_SCOPE_ATTRIBUTES);
    } finally {
      canaryScope.setExtendedScopeParams(originalParams);
    }

    if (!StringUtils.hasText(query)) {
      throw new IllegalArgumentException(
          "Clickhouse canary metrics must define either customInlineTemplate or "
              + "customFilterTemplate containing the SQL query to execute.");
    }

    return query;
  }
}
