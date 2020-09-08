/*
 * Copyright 2020 Netflix, Inc.
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

package com.netflix.spinnaker.config.okhttp3;

import brave.Tracing;
import brave.http.HttpTracing;
import com.netflix.spinnaker.okhttp.OkHttpClientConfigurationProperties;
import java.util.List;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(OkHttpClientConfigurationProperties.class)
class RawOkHttpClientConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public HttpTracing httpTracing() {
    return HttpTracing.newBuilder(Tracing.newBuilder().build()).build();
  }

  /**
   * Default {@link OkHttpClient} that is correctly configured for service-to-service communication.
   */
  @Bean
  @ConditionalOnMissingBean
  OkHttpClient okhttp3Client(
      OkHttpClientConfigurationProperties okHttpClientConfigurationProperties,
      List<Interceptor> interceptors,
      HttpTracing httpTracing) {
    return new RawOkHttpClientFactory()
        .create(okHttpClientConfigurationProperties, interceptors, httpTracing);
  }
}
