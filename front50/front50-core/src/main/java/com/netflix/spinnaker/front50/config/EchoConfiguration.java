/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.front50.config;

import com.netflix.spinnaker.config.OkHttp3ClientConfiguration;
import com.netflix.spinnaker.front50.echo.EchoService;
import com.netflix.spinnaker.kork.retrofit.ErrorHandlingExecutorCallAdapterFactory;
import com.netflix.spinnaker.kork.retrofit.util.CustomConverterFactory;
import com.netflix.spinnaker.kork.retrofit.util.RetrofitUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import retrofit2.Retrofit;

/** echo service configuration */
@Configuration
@ConditionalOnProperty("services.echo.enabled")
public class EchoConfiguration {
  @Bean
  EchoService echoService(
      OkHttp3ClientConfiguration okHttpClientConfig,
      @Value("${services.echo.base-url}") String baseUrl) {
    return new Retrofit.Builder()
        .baseUrl(RetrofitUtils.getBaseUrl(baseUrl))
        .client(okHttpClientConfig.createForRetrofit2().build())
        .addConverterFactory(CustomConverterFactory.create())
        .addCallAdapterFactory(ErrorHandlingExecutorCallAdapterFactory.getInstance())
        .build()
        .create(EchoService.class);
  }
}
