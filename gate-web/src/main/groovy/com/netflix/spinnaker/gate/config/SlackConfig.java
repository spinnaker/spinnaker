/*
 * Copyright 2019 Netflix, Inc.
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

package com.netflix.spinnaker.gate.config;

import static retrofit.Endpoints.newFixedEndpoint;

import com.jakewharton.retrofit.Ok3Client;
import com.netflix.spinnaker.gate.services.SlackService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import retrofit.Endpoint;
import retrofit.RequestInterceptor;
import retrofit.RestAdapter;
import retrofit.converter.JacksonConverter;

@Configuration
@EnableConfigurationProperties(SlackConfigProperties.class)
public class SlackConfig {

  private SlackConfigProperties slackConfigProperties;

  @Autowired
  public SlackConfig(SlackConfigProperties slackConfigProperties) {
    this.slackConfigProperties = slackConfigProperties;
  }

  @Bean
  public Endpoint slackEndpoint() {
    return newFixedEndpoint(slackConfigProperties.getBaseUrl());
  }

  private RequestInterceptor requestInterceptor =
      new RequestInterceptor() {
        @Override
        public void intercept(RequestInterceptor.RequestFacade request) {
          String value = "Token token=" + slackConfigProperties.token;
          request.addHeader("Authorization", value);
        }
      };

  @Bean
  SlackService slackService(Endpoint slackEndpoint) {
    return new RestAdapter.Builder()
        .setEndpoint(slackEndpoint)
        .setClient(new Ok3Client())
        .setConverter(new JacksonConverter())
        .setRequestInterceptor(requestInterceptor)
        .build()
        .create(SlackService.class);
  }
}
