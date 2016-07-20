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

package com.netflix.spinnaker.fiat.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.fiat.providers.internal.ClouddriverService;
import com.netflix.spinnaker.fiat.providers.internal.Front50Service;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import retrofit.Endpoints;
import retrofit.RestAdapter;
import retrofit.client.OkClient;
import retrofit.converter.JacksonConverter;

@Configuration
public class ResourcesConfig {
  @Autowired
  @Setter
  private RestAdapter.LogLevel retrofitLogLevel;

  @Autowired
  @Setter
  private ObjectMapper objectMapper;

  @Autowired
  @Setter
  private OkClient okClient;

  @Value("${services.front50.baseUrl}")
  @Setter
  private String front50Endpoint;

  @Value("${services.clouddriver.baseUrl}")
  @Setter
  private String clouddriverEndpoint;

  @Bean
  Front50Service front50Service() {
    return new RestAdapter.Builder()
        .setEndpoint(Endpoints.newFixedEndpoint(front50Endpoint))
        .setClient(okClient)
        .setConverter(new JacksonConverter(objectMapper))
        .setLogLevel(retrofitLogLevel)
        .build()
        .create(Front50Service.class);
  }

  @Bean
  ClouddriverService clouddriverService() {
    return new RestAdapter.Builder()
        .setEndpoint(Endpoints.newFixedEndpoint(clouddriverEndpoint))
        .setClient(okClient)
        .setConverter(new JacksonConverter(objectMapper))
        .setLogLevel(retrofitLogLevel)
        .build()
        .create(ClouddriverService.class);
  }
}
