/*
 * Copyright 2022 Salesforce, Inc.
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

package com.netflix.spinnaker.gate.interceptors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import com.netflix.spinnaker.kork.common.Header;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

public class ResponseHeaderInterceptorConfigurationPropertiesTest {

  @Test
  public void testResponseHeaderInterceptorSettingsDefault() {
    ApplicationContextRunner runner =
        new ApplicationContextRunner()
            .withUserConfiguration(ResponseHeaderInterceptorTestConfiguration.class);

    runner.run(
        ctx -> {
          ResponseHeaderInterceptorConfigurationProperties properties =
              ctx.getBean(ResponseHeaderInterceptorConfigurationProperties.class);

          assertThat(properties.getFields().size(), equalTo(1));
          assertThat(properties.getFields(), contains(Header.REQUEST_ID.getHeader()));
        });
  }

  @Test
  public void testResponseHeaderInterceptorSettingsAllFields() {
    String[] values = {
      "X-SPINNAKER-REQUEST-ID",
      "X-SPINNAKER-USER",
      "X-SPINNAKER-EXECUTION-ID",
      "X-SPINNAKER-EXECUTION-TYPE",
      "X-SPINNAKER-APPLICATION"
    };
    String value = String.join(",", values);

    ApplicationContextRunner runner =
        new ApplicationContextRunner()
            .withPropertyValues("interceptors.responseHeader.fields=" + value)
            .withUserConfiguration(ResponseHeaderInterceptorTestConfiguration.class);

    runner.run(
        ctx -> {
          ResponseHeaderInterceptorConfigurationProperties properties =
              ctx.getBean(ResponseHeaderInterceptorConfigurationProperties.class);

          assertThat(properties.getFields().size(), equalTo(values.length));
          assertThat(properties.getFields(), containsInAnyOrder(values));
        });
  }

  @Test
  public void testResponseHeaderInterceptorSettingsNoFields() {
    ApplicationContextRunner runner =
        new ApplicationContextRunner()
            .withPropertyValues("interceptors.responseHeader.fields=")
            .withUserConfiguration(ResponseHeaderInterceptorTestConfiguration.class);

    runner.run(
        ctx -> {
          ResponseHeaderInterceptorConfigurationProperties properties =
              ctx.getBean(ResponseHeaderInterceptorConfigurationProperties.class);

          assertThat(properties.getFields(), empty());
        });
  }

  @EnableConfigurationProperties(ResponseHeaderInterceptorConfigurationProperties.class)
  static class ResponseHeaderInterceptorTestConfiguration {}
}
