/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.kayenta.retrofit.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.kayenta.atlas.config.KayentaSerializationConfigurationProperties;
import com.netflix.kayenta.config.KayentaConfiguration;
import com.netflix.spinnaker.config.OkHttp3ClientConfiguration;
import com.netflix.spinnaker.orca.retrofit.exceptions.SpinnakerServerExceptionHandler;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

@Configuration
public class RetrofitClientConfiguration {

  @Bean
  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  OkHttpClient okHttpClient(OkHttp3ClientConfiguration okHttp3ClientConfig) {
    return okHttp3ClientConfig.create().build();
  }

  @Bean
  @Order(Ordered.HIGHEST_PRECEDENCE)
  SpinnakerServerExceptionHandler spinnakerServerExceptionHandler() {
    return new SpinnakerServerExceptionHandler();
  }

  @Bean
  @ConditionalOnMissingBean(ObjectMapper.class)
  ObjectMapper retrofitObjectMapper() {
    ObjectMapper objectMapper = new ObjectMapper();
    KayentaSerializationConfigurationProperties kayentaSerializationConfigurationProperties =
        new KayentaSerializationConfigurationProperties();
    KayentaConfiguration.configureObjectMapperFeatures(
        objectMapper, kayentaSerializationConfigurationProperties);
    return objectMapper;
  }
}
