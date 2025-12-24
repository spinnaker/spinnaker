/*
 * Copyright 2025 OpsMx, Inc.
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

package com.netflix.spinnaker.clouddriver.artifacts.front50;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.clouddriver.config.RetrofitConfig;
import com.netflix.spinnaker.config.ArtifactConfiguration;
import com.netflix.spinnaker.config.DefaultServiceClientProvider;
import com.netflix.spinnaker.config.okhttp3.DefaultOkHttpClientBuilderProvider;
import com.netflix.spinnaker.config.okhttp3.OkHttpClientProvider;
import com.netflix.spinnaker.config.okhttp3.RawOkHttpClientConfiguration;
import com.netflix.spinnaker.credentials.CredentialsRepository;
import com.netflix.spinnaker.kork.retrofit.Retrofit2ServiceFactory;
import com.netflix.spinnaker.okhttp.OkHttpClientConfigurationProperties;
import com.netflix.spinnaker.okhttp.SpinnakerRequestHeaderInterceptor;
import java.util.Collections;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

public class Front50ArtifactConfigurationTest {
  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(
              UserConfigurations.of(
                  RetrofitConfig.class,
                  Retrofit2ServiceFactory.class,
                  OkHttpClientProvider.class,
                  DefaultOkHttpClientBuilderProvider.class,
                  OkHttpClientConfigurationProperties.class,
                  RawOkHttpClientConfiguration.class,
                  Front50ArtifactConfigurationTest.TestConfig.class,
                  DefaultServiceClientProvider.class,
                  ObjectMapper.class,
                  ArtifactConfiguration.class));

  @Test
  void testFront50ArtifactConfiguration() {
    runner
        .withPropertyValues("services.front50.baseUrl=http://front50-url")
        .run(
            ctx -> {
              assertThat(ctx.getBeanNamesForType(CredentialsRepository.class))
                  .containsExactly("front50ArtifactCredentialsRepository");
              assertThat(ctx).hasSingleBean(OkHttpClient.class);
            });
  }

  @Configuration
  static class TestConfig {

    @Bean
    SpinnakerRequestHeaderInterceptor spinnakerRequestHeaderInterceptor() {
      return new SpinnakerRequestHeaderInterceptor(
          false /* propagateSpinnakerHeaders */, Collections.emptyList() /* additionalHeaders */);
    }
  }
}
