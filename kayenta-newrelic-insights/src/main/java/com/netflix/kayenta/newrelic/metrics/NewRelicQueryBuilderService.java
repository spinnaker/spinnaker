package com.netflix.kayenta.newrelic.metrics;

import com.netflix.kayenta.canary.CanaryConfig;
import com.netflix.kayenta.canary.providers.metrics.NewRelicCanaryMetricSetQueryConfig;
import com.netflix.kayenta.canary.providers.metrics.QueryConfigUtils;
import com.netflix.kayenta.newrelic.canary.NewRelicCanaryScope;
import com.netflix.kayenta.newrelic.config.NewRelicScopeConfiguration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * This is a class to house all the logic for building NRQL queries for getting data from the New
 * Relic Insights API.
 *
 * <p>This class could be a static utils class but having it be a spring component / regular
 * instantiated class will allow it to mocked easier in class unit tests.
 */
@Component
public class NewRelicQueryBuilderService {

  /**
   * Method for taking data from the execution request and metric under evaluation and generating
   * the NRQL statement to send to the New Relic Insights API to fetch the data needed for Kayenta
   * to run statistical analysis.
   *
   * @param canaryConfig The supplied canary configuration for the execution.
   * @param canaryScope The New Relic Canary Scope for the execution.
   * @param queryConfig The New Relic Query Config for the metric.
   * @param scopeConfiguration The default scope configuration for the given metrics account.
   * @return The NRQL statement to use to get the data from the New Relic Insights API.
   */
  String buildQuery(
      CanaryConfig canaryConfig,
      NewRelicCanaryScope canaryScope,
      NewRelicCanaryMetricSetQueryConfig queryConfig,
      NewRelicScopeConfiguration scopeConfiguration) {

    String[] baseScopeAttributes = new String[] {"scope", "location", "step"};

    // New Relic requires the time range to be in the query and for it to be in epoch millis or
    // some other weird timestamp that is not an ISO 8061 TS.
    // You cannot use the keys `start` and `end` here as the template engine tries to read the value
    // out
    // of the canary scope bean instead of the values you supply here and you get an ISO 8061 ts
    // instead of epoch secs.
    // Thus we add the epoch millis to the context to be available in templates as startEpochSeconds
    // and endEpochSeconds
    Map<String, String> originalParams =
        canaryScope.getExtendedScopeParams() == null
            ? new HashMap<>()
            : canaryScope.getExtendedScopeParams();
    Map<String, String> paramsWithExtraTemplateValues = new HashMap<>(originalParams);
    paramsWithExtraTemplateValues.put(
        "startEpochSeconds", String.valueOf(canaryScope.getStart().getEpochSecond()));
    paramsWithExtraTemplateValues.put(
        "endEpochSeconds", String.valueOf(canaryScope.getEnd().getEpochSecond()));
    canaryScope.setExtendedScopeParams(paramsWithExtraTemplateValues);

    String customFilter =
        QueryConfigUtils.expandCustomFilter(
            canaryConfig, queryConfig, canaryScope, baseScopeAttributes);

    return Optional.ofNullable(customFilter)
        .orElseGet(
            () -> {
              // un-mutate the extended scope params, so that the additional values we injected into
              // the map for templates don't make it into the simplified flow query.
              canaryScope.setExtendedScopeParams(originalParams);
              return buildQueryFromSelectAndQ(canaryScope, queryConfig, scopeConfiguration);
            });
  }

  /**
   * This is the original method for assembling the NRQL query for fetching the time series data.
   * This method has logic for automatically appending certain requires segments of the query.
   *
   * @param canaryScope The New Relic Canary Scope for the execution.
   * @param queryConfig The New Relic Query Config for the metric.
   * @param scopeConfiguration The default scope configuration for the given metrics account.
   * @return The complete NRQL query from the select and q query fragments.
   */
  protected String buildQueryFromSelectAndQ(
      NewRelicCanaryScope canaryScope,
      NewRelicCanaryMetricSetQueryConfig queryConfig,
      NewRelicScopeConfiguration scopeConfiguration) {

    String select =
        queryConfig.getSelect().startsWith("SELECT")
            ? queryConfig.getSelect()
            : "SELECT " + queryConfig.getSelect().trim();

    StringBuilder query = new StringBuilder(select);
    query.append(" TIMESERIES ");

    if (canaryScope.getStep() == 0) {
      query.append("MAX");
    } else {
      query.append(canaryScope.getStep());
      query.append(" seconds");
    }

    query.append(" SINCE ");
    query.append(canaryScope.getStart().getEpochSecond());
    query.append(" UNTIL ");
    query.append(canaryScope.getEnd().getEpochSecond());
    query.append(" WHERE ");
    if (!StringUtils.isEmpty(queryConfig.getQ())) {
      query.append(queryConfig.getQ());
      query.append(" AND ");
    }

    for (Map.Entry<String, String> extendedParam :
        canaryScope.getExtendedScopeParams().entrySet()) {
      if (extendedParam.getKey().startsWith("_")) {
        continue;
      }
      query.append(extendedParam.getKey());
      query.append(" LIKE ");
      query.append('\'');
      query.append(extendedParam.getValue());
      query.append('\'');
      query.append(" AND ");
    }

    query.append(getScopeKey(canaryScope, scopeConfiguration));
    query.append(" LIKE '");
    query.append(canaryScope.getScope());
    query.append('\'');

    getLocationKey(canaryScope, scopeConfiguration)
        .ifPresent(
            locationKey -> {
              query.append(" AND ");
              query.append(locationKey);
              query.append(" LIKE ");
              query.append('\'');
              query.append(canaryScope.getLocation());
              query.append('\'');
            });

    return query.toString();
  }

  /**
   * Method that will get the scope key from the extended scope parameters or the default set for an
   * account, throwing an exception if neither are set.
   *
   * @param canaryScope The New Relic Canary Scope for the execution.
   * @param scopeConfiguration The default scope configuration for the given metrics account.
   * @return The scope key to use
   */
  protected String getScopeKey(
      NewRelicCanaryScope canaryScope, NewRelicScopeConfiguration scopeConfiguration) {
    return Optional.ofNullable(canaryScope.getScopeKey())
        .orElseGet(
            () ->
                Optional.ofNullable(scopeConfiguration.getDefaultScopeKey())
                    .orElseThrow(
                        () ->
                            new IllegalArgumentException(
                                "The NewRelic account must define a default scope key or "
                                    + "it must be supplied in the extendedScopeParams in the `_scope_key` key")));
  }

  /**
   * Method that will get the optional location key to use if configured.
   *
   * @param canaryScope The New Relic Canary Scope for the execution.
   * @param scopeConfiguration The default scope configuration for the given metrics account.
   * @return Optional of the location key to use, will be present if configured.
   */
  protected Optional<String> getLocationKey(
      NewRelicCanaryScope canaryScope, NewRelicScopeConfiguration scopeConfiguration) {

    return Optional.ofNullable(
        Optional.ofNullable(canaryScope.getLocationKey())
            .orElse(Optional.ofNullable(scopeConfiguration.getDefaultLocationKey()).orElse(null)));
  }
}
