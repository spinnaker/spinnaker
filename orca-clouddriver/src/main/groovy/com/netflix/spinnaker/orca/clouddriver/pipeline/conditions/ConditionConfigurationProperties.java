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

import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@ConfigurationProperties("tasks.evaluateCondition")
public class ConditionConfigurationProperties {
  private final DynamicConfigService configService;
  private boolean enabled = false;
  private Long backoffWaitMs = TimeUnit.MINUTES.toMillis(5);
  private Long waitTimeoutMs = TimeUnit.MINUTES.toMillis(120);
  private List<String> clusters;
  private List<String> activeConditions;

  @Autowired
  public ConditionConfigurationProperties(DynamicConfigService configService) {
    this.configService = configService;
  }

  public boolean isEnabled() {
    return configService.getConfig(Boolean.class, "tasks.evaluateCondition", enabled);
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public Long getBackoffWaitMs() {
    return configService.getConfig(Long.class, "tasks.evaluateCondition.backoffWaitMs", backoffWaitMs);
  }

  public void setBackoffWaitMs(Long backoffWaitMs) {
    this.backoffWaitMs = backoffWaitMs;
  }

  public long getWaitTimeoutMs() {
    return configService.getConfig(Long.class, "tasks.evaluateCondition.waitTimeoutMs", waitTimeoutMs);
  }

  public void setWaitTimeoutMs(long waitTimeoutMs) {
    this.waitTimeoutMs = waitTimeoutMs;
  }

  public List<String> getClusters() {
    return configService.getConfig(List.class, "tasks.evaluateCondition.clusters", clusters);
  }

  public List<String> getActiveConditions() {
    return configService.getConfig(List.class, "tasks.evaluateCondition.activeConditions", activeConditions);
  }

  public void setClusters(List<String> clusters) {
    this.clusters = clusters;
  }

  public void setActiveConditions(List<String> activeConditions) {
    this.activeConditions = activeConditions;
  }

  public boolean isSkipWait() {
    return configService.getConfig(Boolean.class, "tasks.evaluateCondition.skipWait", false);
  }
}
