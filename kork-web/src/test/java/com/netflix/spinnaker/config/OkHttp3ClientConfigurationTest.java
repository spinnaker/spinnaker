/*
 * Copyright 2025 OpsMx, Inc.
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

package com.netflix.spinnaker.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.okhttp.OkHttp3MetricsInterceptor;
import com.netflix.spinnaker.okhttp.OkHttpClientConfigurationProperties;
import com.netflix.spinnaker.okhttp.Retrofit2EncodeCorrectionInterceptor;
import com.netflix.spinnaker.okhttp.SpinnakerRequestHeaderInterceptor;
import okhttp3.logging.HttpLoggingInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.boot.task.TaskExecutorBuilder;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.ObjectPostProcessor;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.configuration.ObjectPostProcessorConfiguration;

public class OkHttp3ClientConfigurationTest {
  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withBean(ObjectMapper.class)
          .withBean(TaskExecutorBuilder.class)
          .withUserConfiguration(OkHttp3ClientConfigurationTestConfig.class);

  @BeforeEach
  void init(TestInfo testInfo) {
    System.out.println("--------------- Test " + testInfo.getDisplayName());
  }

  @Test
  void verifyValidConfiguration() {
    runner.run(
        ctx -> {
          assertThat(ctx).hasSingleBean(OkHttpClientConfigurationProperties.class);
          assertThat(ctx).hasSingleBean(HttpLoggingInterceptor.Level.class);
          assertThat(ctx).hasSingleBean(SpinnakerRequestHeaderInterceptor.class);
          assertThat(ctx).hasSingleBean(Retrofit2EncodeCorrectionInterceptor.class);
          assertThat(ctx).hasSingleBean(OkHttp3MetricsInterceptor.class);
        });
  }

  @Configuration
  @ComponentScan(basePackageClasses = OkHttp3ClientConfiguration.class)
  static class OkHttp3ClientConfigurationTestConfig {

    @Bean
    public ObjectPostProcessor<Object> objectPostProcessor(AutowireCapableBeanFactory beanFactory) {
      return new ObjectPostProcessorConfiguration().objectPostProcessor(beanFactory);
    }

    @Bean
    public AuthenticationConfiguration authenticationConfiguration() {
      return new AuthenticationConfiguration();
    }
  }
}
