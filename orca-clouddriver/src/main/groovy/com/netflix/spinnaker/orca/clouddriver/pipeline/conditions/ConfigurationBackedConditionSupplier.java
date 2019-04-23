/*
 * Copyright 2019 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.pipeline.conditions;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Allows statically defined conditions
 * Aimed to be used for testing or as a pause-all deployments mechanism
 */
@Component
@ConditionalOnExpression("${tasks.evaluateCondition.enabled:false}")
public class ConfigurationBackedConditionSupplier implements ConditionSupplier {
  private final ConditionConfigurationProperties conditionsConfigurationProperties;

  @Autowired
  public ConfigurationBackedConditionSupplier(ConditionConfigurationProperties conditionsConfigurationProperties) {
    this.conditionsConfigurationProperties = conditionsConfigurationProperties;
  }

  @Override
  public List<Condition> getConditions(String cluster, String region, String account) {
    final List<String> clusters = conditionsConfigurationProperties.getClusters();
    final List<String> activeConditions = conditionsConfigurationProperties.getActiveConditions();

    if (clusters == null || clusters.isEmpty() || activeConditions == null || activeConditions.isEmpty()) {
      return Collections.emptyList();
    }

    if (!clusters.contains(cluster)) {
      return Collections.emptyList();
    }

    return activeConditions.stream()
      .map(conditionName -> new Condition(conditionName, String.format("Active condition applies to: %s", conditionName)))
      .collect(Collectors.toList());
  }
}
