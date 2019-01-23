/*
 * Copyright (c) 2018 Nike, inc.
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
 *
 */

package com.netflix.kayenta.signalfx.metrics;

import com.netflix.kayenta.canary.providers.metrics.QueryPair;
import com.netflix.kayenta.signalfx.canary.SignalFxCanaryScope;
import com.netflix.kayenta.signalfx.config.SignalFxScopeConfiguration;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Builds simple signal flow programs.
 */
public class SimpleSignalFlowProgramBuilder {

  public static final String FILTER_TEMPLATE = "filter('%s', '%s')";
  private final String metricName;
  private final String aggregationMethod;
  private final SignalFxScopeConfiguration scopeConfiguration;

  private List<QueryPair> queryPairs;
  private List<String> filterSegments;
  private List<String> scopeKeys;

  private SimpleSignalFlowProgramBuilder(String metricName, String aggregationMethod, SignalFxScopeConfiguration scopeConfiguration) {
    this.metricName = metricName;
    this.aggregationMethod = aggregationMethod;
    this.scopeConfiguration = scopeConfiguration;
    queryPairs = new LinkedList<>();
    filterSegments = new LinkedList<>();
    scopeKeys = new LinkedList<>();
  }

  public static SimpleSignalFlowProgramBuilder create(String metricName,
                                                      String aggregationMethod,
                                                      SignalFxScopeConfiguration scopeConfiguration) {

    return new SimpleSignalFlowProgramBuilder(metricName, aggregationMethod, scopeConfiguration);
  }

  public SimpleSignalFlowProgramBuilder withQueryPair(QueryPair queryPair) {
    queryPairs.add(queryPair);
    return this;
  }

  public SimpleSignalFlowProgramBuilder withQueryPairs(Collection<QueryPair> queryPairs) {
    this.queryPairs.addAll(queryPairs);
    return this;
  }

  public SimpleSignalFlowProgramBuilder withScope(SignalFxCanaryScope canaryScope) {
    scopeKeys.addAll(Optional.ofNullable(canaryScope.getExtendedScopeParams()).orElse(new HashMap<>())
        .keySet().stream().filter(key -> !key.startsWith("_")).collect(Collectors.toList()));

    canaryScope.setScopeKey(Optional.ofNullable(canaryScope.getScopeKey())
        .orElseGet(() -> Optional.ofNullable(scopeConfiguration.getDefaultScopeKey())
            .orElseThrow(() -> new IllegalArgumentException("The SignalFx account must define a default scope key or " +
                "it must be supplied in the extendedScopeParams in the `_scope_key` key"))));

    Optional.ofNullable(Optional.ofNullable(canaryScope.getLocationKey())
        .orElse(Optional.ofNullable(scopeConfiguration.getDefaultLocationKey()).orElse(null)))
        .ifPresent( locationKey -> {
          canaryScope.setLocationKey(locationKey);
          scopeKeys.add(locationKey);
        });

    scopeKeys.add(canaryScope.getScopeKey());
    filterSegments.add(buildFilterSegmentFromScope(canaryScope));
    return this;
  }

  private String buildFilterSegmentFromScope(SignalFxCanaryScope canaryScope) {
    List<String> filters = new LinkedList<>();
    Map<String, String> extendedScopeParams = Optional.ofNullable(canaryScope.getExtendedScopeParams())
        .orElse(new HashMap<>());

    filters.add(String.format(FILTER_TEMPLATE, canaryScope.getScopeKey(), canaryScope.getScope()));
    Optional.ofNullable(canaryScope.getLocationKey()).ifPresent(locationKey ->
        filters.add(String.format(FILTER_TEMPLATE, locationKey, canaryScope.getLocation())));

    if (extendedScopeParams.size() > 0) {
      filters.addAll(extendedScopeParams.entrySet().stream()
          .filter(entry -> !entry.getKey().startsWith("_")) // filter out keys that start with _
          .map(entry -> String.format(FILTER_TEMPLATE, entry.getKey(), entry.getValue()))
          .collect(Collectors.toList()));
    }

    return String.join(" and ", filters);
  }

  public String build() {

    StringBuilder program = new StringBuilder("data('").append(metricName).append("', filter=");
    List<String> filters = new LinkedList<>();

    if (queryPairs.size() > 0) {
      filters.add(queryPairs.stream()
          .map(qp -> String.format(FILTER_TEMPLATE, qp.getKey(), qp.getValue()))
          .collect(Collectors.joining(" and ")));
    }
    filters.addAll(filterSegments);

    if (!filters.isEmpty()) {
      program.append(String.join(" and ", filters));
    }

    program.append(")");

    program.append('.').append(aggregationMethod)
        .append("(by=['")
        .append(String.join("', '", scopeKeys))
        .append("'])");

    program.append(".publish()");
    return program.toString();
  }
}
