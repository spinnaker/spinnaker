/*
 * Copyright 2026 spinnaker.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.clouddriver.cloudrun.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class CloudrunConfigurationPropertiesTest {

  @Test
  void waitsTenMinutesPollingEveryTwoSecondsByDefault() {
    CloudrunConfigurationProperties properties = new CloudrunConfigurationProperties();

    assertThat(properties.getServiceReadyTimeout()).isEqualTo(Duration.ofMinutes(10));
    assertThat(properties.getServiceReadyPollInterval()).isEqualTo(Duration.ofSeconds(2));
  }

  @Test
  void bindsTheWaitSettingsFromTheCloudrunPrefix() {
    Binder binder =
        new Binder(
            new MapConfigurationPropertySource(
                Map.of(
                    "cloudrun.service-ready-timeout", "30m",
                    "cloudrun.service-ready-poll-interval", "5s")));

    CloudrunConfigurationProperties properties =
        binder.bind("cloudrun", CloudrunConfigurationProperties.class).get();

    assertThat(properties.getServiceReadyTimeout()).isEqualTo(Duration.ofMinutes(30));
    assertThat(properties.getServiceReadyPollInterval()).isEqualTo(Duration.ofSeconds(5));
  }
}
