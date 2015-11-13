/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.config

import com.netflix.spinnaker.clouddriver.core.services.Front50Service
import com.netflix.spinnaker.config.OkHttpClientConfiguration
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import retrofit.RestAdapter
import retrofit.client.OkClient
import retrofit.converter.JacksonConverter

import static retrofit.Endpoints.newFixedEndpoint

@Configuration
class RetrofitConfig {
  @Autowired
  OkHttpClientConfiguration okHttpClientConfig

  @Bean RestAdapter.LogLevel retrofitLogLevel(@Value('${retrofit.logLevel:BASIC}') String retrofitLogLevel) {
    return RestAdapter.LogLevel.valueOf(retrofitLogLevel)
  }

  @Bean
  OkClient okClient() {
    return new OkClient(okHttpClientConfig.create())
  }

  @Bean
  @ConditionalOnExpression('${services.front50.enabled:true}')
  Front50Service front50Service(@Value('${services.front50.baseUrl}') String front50BaseUrl, RestAdapter.LogLevel retrofitLogLevel) {
    def endpoint = newFixedEndpoint(front50BaseUrl)
    new RestAdapter.Builder()
      .setEndpoint(endpoint)
      .setClient(okClient())
      .setConverter(new JacksonConverter())
      .setLogLevel(retrofitLogLevel)
      .setLog(new Slf4jRetrofitLogger(Front50Service))
      .build()
      .create(Front50Service)
  }

  static class Slf4jRetrofitLogger implements RestAdapter.Log {
    private final Logger logger

    public Slf4jRetrofitLogger(Class type) {
      this(LoggerFactory.getLogger(type))
    }

    public Slf4jRetrofitLogger(Logger logger) {
      this.logger = logger
    }

    @Override
    void log(String message) {
      logger.info(message)
    }
  }
}
