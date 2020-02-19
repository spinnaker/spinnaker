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

package com.netflix.spinnaker.echo.config;

import static java.time.format.DateTimeFormatter.ISO_INSTANT;

import com.google.common.base.Strings;
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "quiet-period")
public class QuietPeriodIndicatorConfigurationProperties {

  private List<String> suppressedTriggerTypes = new ArrayList<>();
  private DynamicConfigService dynamicConfigService;
  private Logger log = LoggerFactory.getLogger(this.getClass().getName());

  public QuietPeriodIndicatorConfigurationProperties(DynamicConfigService dynamicConfigService) {
    this.dynamicConfigService = dynamicConfigService;
  }

  public boolean isEnabled() {
    return (dynamicConfigService.isEnabled("quiet-period", true)
        && getStartTime() > 0
        && getEndTime() > 0);
  }

  public long getStartTime() {
    return parseIso(dynamicConfigService.getConfig(String.class, "quiet-period.start-iso", ""));
  }

  public long getEndTime() {
    return parseIso(dynamicConfigService.getConfig(String.class, "quiet-period.end-iso", ""));
  }

  public List<String> getSuppressedTriggerTypes() {
    return suppressedTriggerTypes;
  }

  public void setSuppressedTriggerTypes(List<String> suppressedTriggerTypes) {
    this.suppressedTriggerTypes = suppressedTriggerTypes;
  }

  private long parseIso(String iso) {
    if (Strings.isNullOrEmpty(iso)) {
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
