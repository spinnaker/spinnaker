/*
 * Copyright 2026 McIntosh.farm
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

package com.netflix.spinnaker.orca.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.netflix.spinnaker.kork.web.url.UrlRestrictions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Existing {@code user-configured-url-restrictions} configuration must keep binding. */
class UrlRestrictionsConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withUserConfiguration(
              UrlRestrictionsConfiguration.class, ConfigurationPropertiesBinding.class);

  @EnableConfigurationProperties
  static class ConfigurationPropertiesBinding {}

  @Test
  void defaultsApplyWithoutConfiguration() {
    runner.run(
        context -> {
          UrlRestrictions restrictions = context.getBean(UrlRestrictions.class);
          assertThat(restrictions.isRejectLocalhost()).isTrue();
          assertThat(restrictions.getAllowedSchemes()).containsExactlyInAnyOrder("http", "https");
          assertThat(context.getBean(UserConfiguredHttpClientProperties.class).getTimeoutMillis())
              .isEqualTo(30000);
        });
  }

  @Test
  void bindsRestrictionsAndEnforcesThem() {
    runner
        .withPropertyValues(
            "user-configured-url-restrictions.allowedHostnamesRegex=.*\\.example\\.com",
            "user-configured-url-restrictions.rejectVerbatimIps=false",
            "user-configured-url-restrictions.rejectedIps[0]=10.0.0.0/8")
        .run(
            context -> {
              UrlRestrictions restrictions = context.getBean(UrlRestrictions.class);
              assertThat(restrictions.getAllowedHostnames().pattern())
                  .isEqualTo(".*\\.example\\.com");
              assertThatThrownBy(() -> restrictions.validateURI("https://10.1.2.3/"))
                  .isInstanceOf(IllegalArgumentException.class)
                  .hasMessageContaining("Address not allowed");
              assertThat(restrictions.validateURI("https://192.168.1.1/")).isNotNull();
            });
  }

  @Test
  void bindsHttpClientPropertiesUsingEitherKeyStyle() {
    runner
        .withPropertyValues(
            "user-configured-url-restrictions.httpClientProperties.timeoutMillis=1234",
            "user-configured-url-restrictions.http-client-properties.max-retry-attempts=3")
        .run(
            context -> {
              UserConfiguredHttpClientProperties properties =
                  context.getBean(UserConfiguredHttpClientProperties.class);
              assertThat(properties.getTimeoutMillis()).isEqualTo(1234);
              assertThat(properties.getMaxRetryAttempts()).isEqualTo(3);
              // the nested http client keys don't break binding of the restrictions themselves
              assertThat(context).hasNotFailed();
            });
  }
}
