/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.halyard.config.config.v1;

import com.netflix.spinnaker.config.OkHttp3ClientConfiguration;
import com.netflix.spinnaker.config.OkHttpClientComponents;
import com.netflix.spinnaker.config.RetrofitConfiguration;
import com.netflix.spinnaker.config.okhttp3.OkHttpClientProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import({
  OkHttp3ClientConfiguration.class,
  OkHttpClientComponents.class,
  RetrofitConfiguration.class
})
class RetrofitConfig {
  @Autowired OkHttp3ClientConfiguration okHttpClientConfig;

  @Value("${ok-http-client.connection-pool.max-idle-connections:5}")
  int maxIdleConnections;

  @Value("${ok-http-client.connection-pool.keep-alive-duration-ms:300000}")
  int keepAliveDurationMs;

  @Value("${ok-http-client.retry-on-connection-failure:true}")
  boolean retryOnConnectionFailure;

  OkHttpClientProvider okHttpClientProvider;

  @Bean
  OkHttpClientProvider okHttpClientProvider() {
    return okHttpClientProvider;
  }
}
