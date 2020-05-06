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
 *
 */
package com.netflix.spinnaker.igor.config;

import com.netflix.spinnaker.config.OkHttpClientConfiguration;
import com.netflix.spinnaker.igor.IgorConfigurationProperties;
import com.netflix.spinnaker.igor.plugins.front50.Front50Service;
import com.netflix.spinnaker.retrofit.Slf4jRetrofitLogger;
import com.squareup.okhttp.OkHttpClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import retrofit.Endpoints;
import retrofit.RestAdapter;
import retrofit.client.OkClient;

@Configuration
@ConditionalOnProperty("services.front50.base-url")
public class Front50Config {

  @Bean
  Front50Service front50Service(
      OkHttpClientConfiguration okHttpClientConfiguration, IgorConfigurationProperties properties) {
    String address = properties.getServices().getFront50().getBaseUrl();

    OkHttpClient client = okHttpClientConfiguration.create();

    return new RestAdapter.Builder()
        .setEndpoint(Endpoints.newFixedEndpoint(address))
        .setClient(new OkClient(client))
        .setLogLevel(RestAdapter.LogLevel.BASIC)
        .setLog(new Slf4jRetrofitLogger(Front50Service.class))
        .build()
        .create(Front50Service.class);
  }
}
