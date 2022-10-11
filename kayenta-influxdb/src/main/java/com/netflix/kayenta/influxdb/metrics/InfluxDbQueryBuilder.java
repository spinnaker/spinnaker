/*
 * Copyright 2018 Joseph Motha
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.kayenta.influxdb.metrics;

import com.netflix.kayenta.canary.CanaryScope;
import com.netflix.kayenta.canary.providers.metrics.InfluxdbCanaryMetricSetQueryConfig;
import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

@Slf4j
@Component
public class InfluxDbQueryBuilder {

  private static final String ALL_FIELDS = "*::field";
  private static final String SCOPE_INVALID_FORMAT_MSG =
      "Scope expected in the format of 'name:value'. e.g. autoscaling_group:myapp-prod-v002, received: ";

  // TODO(joerajeev): protect against injection. Influxdb is supposed to support binding params,
  // https://docs.influxdata.com/influxdb/v1.5/tools/api/
  public String build(InfluxdbCanaryMetricSetQueryConfig queryConfig, CanaryScope canaryScope) {
    StringBuilder query = new StringBuilder();
    validateMandatoryParams(queryConfig, canaryScope);

    if (!StringUtils.isEmpty(queryConfig.getCustomInlineTemplate())) {
      buildCustomQuery(queryConfig, canaryScope, getTimeFilter(canaryScope), query);
    } else {
      addBaseQuery(queryConfig.getMetricName(), handleFields(queryConfig), query);
      addTimeRangeFilter(getTimeFilter(canaryScope), query);
      addScopeFilter(canaryScope, query);
    }

    String builtQuery = query.toString();
    validateQuery(builtQuery);
    log.debug("Built query: {} config: {} scope: {}", builtQuery, queryConfig, canaryScope);

    return builtQuery;
  }

  private void validateMandatoryParams(
      InfluxdbCanaryMetricSetQueryConfig queryConfig, CanaryScope canaryScope) {
    if (StringUtils.isEmpty(queryConfig.getMetricName())
        && StringUtils.isEmpty(queryConfig.getCustomInlineTemplate())) {
      throw new IllegalArgumentException("Measurement is required to query metrics");
    }
    if (null == canaryScope) {
      throw new IllegalArgumentException("CanaryScope is missing");
    }
    if (null == canaryScope.getStart() || null == canaryScope.getEnd()) {
      throw new IllegalArgumentException("Start and End times are required");
    }
    // required variables when using customInlineTemplate
    if (!StringUtils.isEmpty(queryConfig.getCustomInlineTemplate())) {
      if (!queryConfig.getCustomInlineTemplate().contains("$\\{timeFilter}")) {
        throw new IllegalArgumentException("${timeFilter} is required in query");
      }
      if (!queryConfig.getCustomInlineTemplate().contains("$\\{scope}")) {
        throw new IllegalArgumentException("${scope} is required in query");
      }
    }
  }

  private List<String> handleFields(InfluxdbCanaryMetricSetQueryConfig queryConfig) {
    List<String> fields = queryConfig.getFields();
    if (CollectionUtils.isEmpty(fields)) {
      if (fields == null) {
        fields = new ArrayList<>();
      }
      fields.add(ALL_FIELDS);
    }
    return fields;
  }

  private void addBaseQuery(String measurement, List<String> fields, StringBuilder query) {
    query.append("SELECT ");
    query.append(fields.stream().collect(Collectors.joining(", ")));
    query.append(" FROM " + measurement + " ");
  }

  private void addScopeFilter(CanaryScope canaryScope, StringBuilder query) {
    String scope = canaryScope.getScope();
    if (scope != null) {
      query.append(" AND " + getScope(canaryScope));
    }
  }

  private String[] validateAndExtractScope(String scope) {
    if (!scope.contains(":")) {
      throw new IllegalArgumentException(SCOPE_INVALID_FORMAT_MSG + scope);
    }
    String[] scopeParts = scope.split(":");
    if (scopeParts.length != 2) {
      throw new IllegalArgumentException(SCOPE_INVALID_FORMAT_MSG + scope);
    }
    return scopeParts;
  }

  private String getScope(CanaryScope canaryScope) {
    String scope = canaryScope.getScope();
    String[] scopeParts = validateAndExtractScope(scope);
    return scopeParts[0] + " = '" + scopeParts[1] + "'";
  }

  private void addTimeRangeFilter(String timeFilter, StringBuilder query) {
    query.append("WHERE " + timeFilter);
  }

  private void buildCustomQuery(
      InfluxdbCanaryMetricSetQueryConfig queryConfig,
      CanaryScope canaryScope,
      String timeFilter,
      StringBuilder query) {
    String inlineQuery = queryConfig.getCustomInlineTemplate();
    validateQuery(inlineQuery);
    String queryWithTimeFilter = inlineQuery.replace("$\\{timeFilter}", timeFilter);
    String queryWithScope = queryWithTimeFilter.replace("$\\{scope}", getScope(canaryScope));
    query.append(addOptionalStep(canaryScope, queryWithScope));
  }

  private String addOptionalStep(CanaryScope canaryScope, String query) {
    if (query.contains("$\\{step}")) {
      query = query.replace("$\\{step}", canaryScope.getStep().toString() + "s");
    }
    return query;
  }

  private String getTimeFilter(CanaryScope canaryScope) {
    return "time >= '"
        + canaryScope.getStart().toString()
        + "' AND time < '"
        + canaryScope.getEnd().toString()
        + "'";
  }

  private void validateQuery(String query) {
    List<String> blocked = new ArrayList<>();
    blocked.add("SHOW CONTINUOUS QUERIES");
    blocked.add("SHOW DATABASES");
    blocked.add("SHOW DIAGNOSTICS");
    blocked.add("SHOW FIELD");
    blocked.add("SHOW TAG");
    blocked.add("SHOW USERS");
    blocked.add("SHOW GRANTS");
    blocked.add("SHOW MEASUREMENT");
    blocked.add("SHOW QUERIES");
    blocked.add("SHOW RETENTION POLICIES");
    blocked.add("SHOW SERIES");
    blocked.add("SHOW SHARD");
    blocked.add("SHOW STATS");
    blocked.add("SHOW SUBSCRIPTIONS");
    blocked.add(";"); // prevents having multiple queries that could lead to potential sql injection
    blocked.add("--"); // prevents potential sql injection

    blocked.stream()
        .forEach(
            (stmt) -> {
              if (query.contains(stmt)) {
                throw new IllegalArgumentException("Query type not allowed.");
              }
            });
  }
}
