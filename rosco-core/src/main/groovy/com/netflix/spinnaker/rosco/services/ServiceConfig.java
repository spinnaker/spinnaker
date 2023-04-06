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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jakewharton.retrofit.Ok3Client;
import com.netflix.spinnaker.config.OkHttp3ClientConfiguration;
import com.netflix.spinnaker.kork.core.RetrySupport;
import com.netflix.spinnaker.kork.retrofit.ErrorHandlingExecutorCallAdapterFactory;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerRetrofitErrorHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import retrofit.RequestInterceptor;
import retrofit.RestAdapter;
import retrofit.converter.JacksonConverter;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

@Configuration
public class ServiceConfig {
  @Value("${services.clouddriver.base-url:http://localhost:7002}")
  String clouddriverBaseUrl;

  @Value("${retrofit.log-level:BASIC}")
  String retrofitLogLevel;

  @Bean
  Ok3Client okClient(OkHttp3ClientConfiguration okHttpClientConfig) {
    return new Ok3Client(okHttpClientConfig.create().build());
  }

  @Bean
  RetrySupport retrySupport() {
    return new RetrySupport();
  }

  // This should be service-agnostic if more integrations than clouddriver are used
  @Bean
  ClouddriverService clouddriverService(
      Ok3Client ok3Client, RequestInterceptor spinnakerRequestInterceptor) {

    return new RestAdapter.Builder()
        .setEndpoint(clouddriverBaseUrl)
        .setRequestInterceptor(spinnakerRequestInterceptor)
        .setClient(ok3Client)
        .setConverter(new JacksonConverter(getObjectMapper()))
        .setLogLevel(RestAdapter.LogLevel.valueOf(retrofitLogLevel))
        .setErrorHandler(SpinnakerRetrofitErrorHandler.getInstance())
        .build()
        .create(ClouddriverService.class);
  }

  private ObjectMapper getObjectMapper() {
    return new ObjectMapper()
        .enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
  }

  /*As part of retrofit2 changes, creating clouddriverservice with retrofit2 API changes */
  @Bean
  ClouddriverRetrofit2Service clouddriverRetrofit2Service(
      OkHttp3ClientConfiguration okHttpClientConfig) {

    /*
     * ErrorHandlingExecutorCallAdapterFactory handles exceptions globally in retrofit2, similar to SpinnakerRetrofitErrorHandler with retrofit.
     * */
    return new Retrofit.Builder()
        .baseUrl(clouddriverBaseUrl)
        .client(okHttpClientConfig.createForRetrofit2().build())
        .addCallAdapterFactory(ErrorHandlingExecutorCallAdapterFactory.getInstance())
        .addConverterFactory(JacksonConverterFactory.create(getObjectMapper()))
        .build()
        .create(ClouddriverRetrofit2Service.class);
  }
}
