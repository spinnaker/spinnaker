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

import static java.time.format.DateTimeFormatter.ISO_INSTANT;

import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spectator.api.patterns.PolledMeter;
import com.netflix.spinnaker.echo.config.QuietPeriodIndicatorConfigurationProperties;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
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

  private final boolean enabled;
  private final long startEpochMillis;
  private final long endEpochMillis;
  private final List<String> suppressedTriggerTypes;

  private final Id quietPeriodTestId;

  @Autowired
  public QuietPeriodIndicator(
      @NonNull Registry registry, @NonNull QuietPeriodIndicatorConfigurationProperties config) {
    this.registry = registry;

    long startEpochMillis = parseIso(config.getStartIso());
    long endEpochMillis = parseIso(config.getEndIso());
    this.startEpochMillis = startEpochMillis;
    this.endEpochMillis = endEpochMillis;
    this.suppressedTriggerTypes = config.getSuppressedTriggerTypes();

    this.enabled = config.isEnabled() && (startEpochMillis > 0 && endEpochMillis > 0);
    if (this.enabled) {
      log.warn(
          "Enabling quiet periods.  Span in millis: {} to {}, ISO {} to {} (inclusive)",
          startEpochMillis,
          endEpochMillis,
          config.getStartIso(),
          config.getEndIso());
      log.warn(
          "Will suppress triggers of types {} during quiet periods.",
          config.getSuppressedTriggerTypes());
    }

    PolledMeter.using(registry)
        .withName("quietPeriod.enabled")
        .monitorValue(this, QuietPeriodIndicator::gaugeMonitor);
    quietPeriodTestId = registry.createId("quietPeriod.tests");
  }

  public boolean isEnabled() {
    return enabled;
  }

  private double gaugeMonitor() {
    return enabled ? 1.0 : 0.0;
  }

  public boolean inQuietPeriod(long now) {
    boolean result = enabled && (now >= startEpochMillis && now <= endEpochMillis);
    registry.counter(quietPeriodTestId.withTag("result", result)).increment();
    return result;
  }

  private boolean shouldSuppressType(String triggerType) {
    for (String trigger : suppressedTriggerTypes) {
      if (trigger.equalsIgnoreCase(triggerType)) {
        return true;
      }
    }
    return false;
  }

  public boolean inQuietPeriod(long now, String triggerType) {
    return inQuietPeriod(now) && shouldSuppressType(triggerType);
  }

  private long parseIso(String iso) {
    if (iso == null) {
      return -1;
    }
    try {
      Instant instant = Instant.from(ISO_INSTANT.parse(iso));
      return instant.toEpochMilli();
    } catch (DateTimeParseException e) {
      log.warn(
          "Unable to parse {} as an ISO date/time, disabling quiet periods: {}",
          iso,
          e.getMessage());
      return -1;
    }
  }
}
