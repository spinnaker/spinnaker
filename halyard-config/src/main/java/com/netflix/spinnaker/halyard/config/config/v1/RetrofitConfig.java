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

import com.netflix.spinnaker.config.OkHttpClientConfiguration;
import com.squareup.okhttp.ConnectionPool;
import com.squareup.okhttp.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import retrofit.RequestInterceptor;
import retrofit.RestAdapter;
import retrofit.client.OkClient;

@Configuration
class RetrofitConfig {
  @Autowired
  OkHttpClientConfiguration okHttpClientConfig;

  @Value("${okHttpClient.connectionPool.maxIdleConnections:5}")
  int maxIdleConnections;

  @Value("${okHttpClient.connectionPool.keepAliveDurationMs:300000}")
  int keepAliveDurationMs;

  @Value("${okHttpClient.retryOnConnectionFailure:true}")
  boolean retryOnConnectionFailure;

  @Autowired
  RequestInterceptor spinnakerRequestInterceptor;

  @Bean
  RestAdapter.LogLevel retrofitLogLevel(@Value("${retrofit.logLevel:BASIC}") String retrofitLogLevel) {
    return RestAdapter.LogLevel.valueOf(retrofitLogLevel);
  }

  @Bean
  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  OkClient okClient() {
    OkHttpClient client = okHttpClientConfig.create();
    client.setConnectionPool(new ConnectionPool(maxIdleConnections, keepAliveDurationMs));
    client.setRetryOnConnectionFailure(retryOnConnectionFailure);
    return new OkClient(client);
  }

  static class Slf4jRetrofitLogger implements RestAdapter.Log {
    private final Logger logger;

    public Slf4jRetrofitLogger(Class type) {
      this(LoggerFactory.getLogger(type));
    }

    public Slf4jRetrofitLogger(Logger logger) {
      this.logger = logger;
    }

    @Override
    public void log(String message) {
      logger.info(message);
    }
  }
}

