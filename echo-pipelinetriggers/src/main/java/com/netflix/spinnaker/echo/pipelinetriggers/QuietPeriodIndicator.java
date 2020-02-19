/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.echo.pipelinetriggers;

import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.echo.config.QuietPeriodIndicatorConfigurationProperties;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Handles quiet periods, where a configurable set of triggers will be suppressed for a configured
 * time interval. Without any configuration, it will default to disabled, and invalid configuration
 * will cause log messages to be generated, and disable this feature.
 */
@Slf4j
@Component
public class QuietPeriodIndicator {
  private final Registry registry;
  private final Id quietPeriodTestId;

  private final QuietPeriodIndicatorConfigurationProperties config;

  @Autowired
  public QuietPeriodIndicator(
      @NonNull Registry registry, @NonNull QuietPeriodIndicatorConfigurationProperties config) {
    this.registry = registry;
    this.config = config;

    quietPeriodTestId = registry.createId("quietPeriod.tests");
  }

  public boolean isEnabled() {
    return config.isEnabled();
  }

  public long getStartTime() {
    return config.getStartTime();
  }

  public long getEndTime() {
    return config.getEndTime();
  }

  public boolean inQuietPeriod(long testTime) {
    boolean result =
        isEnabled() && (testTime >= config.getStartTime() && testTime <= config.getEndTime());
    registry.counter(quietPeriodTestId.withTag("result", result)).increment();

    return result;
  }

  private boolean shouldSuppressType(String triggerType) {
    for (String trigger : config.getSuppressedTriggerTypes()) {
      if (trigger.equalsIgnoreCase(triggerType)) {
        return true;
      }
    }

    return false;
  }

  public boolean inQuietPeriod(long testTime, String triggerType) {
    return inQuietPeriod(testTime) && shouldSuppressType(triggerType);
  }
}
