/*
 * Copyright 2024 Salesforce, Inc.
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

package com.netflix.spinnaker.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.spinnaker.okhttp.OkHttp3MetricsInterceptor;
import com.netflix.spinnaker.okhttp.SpinnakerRequestHeaderInterceptor;
import com.netflix.spinnaker.okhttp.SpinnakerRequestInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.task.TaskExecutorBuilder;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class OkHttpClientComponentsTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withBean(TaskExecutorBuilder.class)
          .withConfiguration(UserConfigurations.of(OkHttpClientComponents.class));

  @BeforeEach
  void init(TestInfo testInfo) {
    System.out.println("--------------- Test " + testInfo.getDisplayName());
  }

  @Test
  void verifyValidConfiguration() {
    runner.run(
        ctx -> {
          assertThat(ctx).hasSingleBean(SpinnakerRequestInterceptor.class);
          assertThat(ctx).hasSingleBean(SpinnakerRequestHeaderInterceptor.class);
          assertThat(ctx).hasSingleBean(OkHttp3MetricsInterceptor.class);
        });
  }
}
