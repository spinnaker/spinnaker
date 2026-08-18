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
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.netflix.spectator.api.NoopRegistry;
import com.netflix.spinnaker.echo.config.QuietPeriodIndicatorConfigurationProperties;
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class QuietPeriodIndicatorTest {

  private final NoopRegistry registry = new NoopRegistry();
  private final DynamicConfigService dynamicConfigService = mock(DynamicConfigService.class);

  private static final String goodStartDate = "2018-01-01T00:00:00Z";
  private static final String goodEndDate = "2018-02-01T00:00:00Z";
  private static final String badDate = "flarg";
  private static final String beforeDate = "2000-01-01T00:00:00Z";
  private static final String afterDate = "2019-01-01T00:00:00Z";
  private static final String inRangeDate = "2018-01-19T00:00:00Z";

  private static long parseIso(String iso) {
    return Instant.from(ISO_INSTANT.parse(iso)).toEpochMilli();
  }

  @Test
  void isDisabledIfEnabledIsFalse() {
    when(dynamicConfigService.isEnabled("quiet-period", true)).thenReturn(false);
    QuietPeriodIndicatorConfigurationProperties config =
        new QuietPeriodIndicatorConfigurationProperties(dynamicConfigService);
    QuietPeriodIndicator quietPeriodIndicator = new QuietPeriodIndicator(registry, config);

    assertThat(quietPeriodIndicator.isEnabled()).isFalse();
  }

  @Test
  void disabledIfStartDateIsInvalid() {
    when(dynamicConfigService.isEnabled("quiet-period", true)).thenReturn(true);
    when(dynamicConfigService.getConfig(eq(String.class), eq("quiet-period.start-iso"), eq("")))
        .thenReturn(badDate);
    QuietPeriodIndicatorConfigurationProperties config =
        new QuietPeriodIndicatorConfigurationProperties(dynamicConfigService);
    QuietPeriodIndicator quietPeriodIndicator = new QuietPeriodIndicator(registry, config);

    assertThat(quietPeriodIndicator.isEnabled()).isFalse();
  }

  @Test
  void disabledIfEndDateIsInvalid() {
    when(dynamicConfigService.isEnabled("quiet-period", true)).thenReturn(true);
    when(dynamicConfigService.getConfig(eq(String.class), eq("quiet-period.start-iso"), eq("")))
        .thenReturn(goodStartDate);
    when(dynamicConfigService.getConfig(eq(String.class), eq("quiet-period.end-iso"), eq("")))
        .thenReturn(badDate);
    QuietPeriodIndicatorConfigurationProperties config =
        new QuietPeriodIndicatorConfigurationProperties(dynamicConfigService);
    QuietPeriodIndicator quietPeriodIndicator = new QuietPeriodIndicator(registry, config);

    assertThat(quietPeriodIndicator.isEnabled()).isFalse();
  }

  @Test
  void rangesWork() {
    when(dynamicConfigService.isEnabled("quiet-period", true)).thenReturn(true);
    when(dynamicConfigService.getConfig(eq(String.class), eq("quiet-period.start-iso"), eq("")))
        .thenReturn(goodStartDate);
    when(dynamicConfigService.getConfig(eq(String.class), eq("quiet-period.end-iso"), eq("")))
        .thenReturn(goodEndDate);
    QuietPeriodIndicatorConfigurationProperties config =
        new QuietPeriodIndicatorConfigurationProperties(dynamicConfigService);
    QuietPeriodIndicator quietPeriodIndicator = new QuietPeriodIndicator(registry, config);

    assertThat(quietPeriodIndicator.inQuietPeriod(parseIso(beforeDate))).isFalse();
    assertThat(quietPeriodIndicator.inQuietPeriod(parseIso(inRangeDate))).isTrue();
    assertThat(quietPeriodIndicator.inQuietPeriod(parseIso(afterDate))).isFalse();
  }

  @Test
  void triggerTypeListIsRespected() {
    List<String> triggerTypes = new ArrayList<>();
    triggerTypes.add("inTheList");
    when(dynamicConfigService.isEnabled("quiet-period", true)).thenReturn(true);
    when(dynamicConfigService.getConfig(eq(String.class), eq("quiet-period.start-iso"), eq("")))
        .thenReturn(goodStartDate);
    when(dynamicConfigService.getConfig(eq(String.class), eq("quiet-period.end-iso"), eq("")))
        .thenReturn(goodEndDate);
    QuietPeriodIndicatorConfigurationProperties config =
        new QuietPeriodIndicatorConfigurationProperties(dynamicConfigService);
    config.setSuppressedTriggerTypes(triggerTypes);
    QuietPeriodIndicator quietPeriodIndicator = new QuietPeriodIndicator(registry, config);

    assertThat(quietPeriodIndicator.inQuietPeriod(parseIso(inRangeDate), "notInTheList")).isFalse();
    assertThat(quietPeriodIndicator.inQuietPeriod(parseIso(inRangeDate), "inTheList")).isTrue();

    // comparison is not case sensitive
    assertThat(quietPeriodIndicator.inQuietPeriod(parseIso(inRangeDate), "inthelist")).isTrue();
  }
}
