/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.rosco.services;

import com.netflix.spinnaker.config.OkHttp3ClientConfiguration;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.kork.retrofit.ErrorHandlingExecutorCallAdapterFactory;
import com.netflix.spinnaker.kork.retrofit.util.CustomConverterFactory;
import com.netflix.spinnaker.kork.retrofit.util.RetrofitUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import retrofit2.Retrofit;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class ServiceConfig {
  @Value("${services.clouddriver.base-url:http://localhost:7002}")
  String clouddriverBaseUrl;

  @Bean
  RetrySupport retrySupport() {
    return new RetrySupport();
  }

  // This should be service-agnostic if more integrations than clouddriver are used
  @Bean
  ClouddriverService clouddriverService(OkHttp3ClientConfiguration okHttpClientConfig) {

    ObjectMapper objectMapper =
        JsonMapper.builder()
            .enable(EnumFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    /*
     * ErrorHandlingExecutorCallAdapterFactory handles exceptions globally in retrofit2
     * */
    return new Retrofit.Builder()
        .baseUrl(RetrofitUtils.getBaseUrl(clouddriverBaseUrl))
        .client(okHttpClientConfig.createForRetrofit2().build())
        .addCallAdapterFactory(ErrorHandlingExecutorCallAdapterFactory.getInstance())
        .addConverterFactory(CustomConverterFactory.create(objectMapper))
        .build()
        .create(ClouddriverService.class);
  }
}
