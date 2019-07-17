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
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

@Slf4j
@Component
public class InfluxDbQueryBuilder {

  private static final String ALL_FIELDS = "*::field";
  private static final String SCOPE_INVALID_FORMAT_MSG =
      "Scope expected in the format of 'name:value'. e.g. autoscaling_group:myapp-prod-v002, received: ";

  // TODO(joerajeev): update to accept tags and groupby fields
  // TODO(joerajeev): protect against injection. Influxdb is supposed to support binding params,
  // https://docs.influxdata.com/influxdb/v1.5/tools/api/
  public String build(InfluxdbCanaryMetricSetQueryConfig queryConfig, CanaryScope canaryScope) {

    validateManadtoryParams(queryConfig, canaryScope);

    StringBuilder query = new StringBuilder();
    addBaseQuery(queryConfig.getMetricName(), handleFields(queryConfig), query);
    addTimeRangeFilter(canaryScope, query);
    addScopeFilter(canaryScope, query);

    String builtQuery = query.toString();
    log.debug("Built query: {} config: {} scope: {}", builtQuery, queryConfig, canaryScope);
    return builtQuery;
  }

  private void validateManadtoryParams(
      InfluxdbCanaryMetricSetQueryConfig queryConfig, CanaryScope canaryScope) {
    if (StringUtils.isEmpty(queryConfig.getMetricName())) {
      throw new IllegalArgumentException("Measurement is required to query metrics");
    }
    if (null == canaryScope) {
      throw new IllegalArgumentException("CanaryScope is missing");
    }
    if (null == canaryScope.getStart() || null == canaryScope.getEnd()) {
      throw new IllegalArgumentException("Start and End times are required");
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
    query.append(" FROM ");
    query.append(measurement);
  }

  private void addScopeFilter(CanaryScope canaryScope, StringBuilder sb) {
    String scope = canaryScope.getScope();
    if (scope != null) {
      String[] scopeParts = validateAndExtractScope(scope);
      sb.append(" AND ");
      sb.append(scopeParts[0] + "='" + scopeParts[1] + "'");
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

  private void addTimeRangeFilter(CanaryScope canaryScope, StringBuilder query) {
    query.append(" WHERE");
    query.append(" time >= '" + canaryScope.getStart().toString() + "'");
    query.append(" AND");
    query.append(" time < '" + canaryScope.getEnd().toString() + "'");
  }
}
