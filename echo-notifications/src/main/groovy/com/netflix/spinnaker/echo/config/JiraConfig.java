/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.echo.config;

import static retrofit.Endpoints.newFixedEndpoint;

import com.netflix.spinnaker.echo.jira.JiraProperties;
import com.netflix.spinnaker.echo.jira.JiraService;
import com.netflix.spinnaker.retrofit.Slf4jRetrofitLogger;
import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import retrofit.RestAdapter;
import retrofit.client.Client;
import retrofit.client.OkClient;
import retrofit.converter.JacksonConverter;

@Configuration
@ConditionalOnProperty("jira.enabled")
@EnableConfigurationProperties(JiraProperties.class)
public class JiraConfig {
  private static Logger LOGGER = LoggerFactory.getLogger(JiraConfig.class);

  @Autowired(required = false)
  private OkClient x509ConfiguredClient;

  @Bean
  JiraService jiraService(
      JiraProperties jiraProperties, Client retrofitClient, RestAdapter.LogLevel retrofitLogLevel) {
    if (x509ConfiguredClient != null) {
      LOGGER.info("Using X509 Cert for Jira Client");
      retrofitClient = x509ConfiguredClient;
    }

    RestAdapter.Builder builder =
        new RestAdapter.Builder()
            .setEndpoint(newFixedEndpoint(jiraProperties.getBaseUrl()))
            .setConverter(new JacksonConverter())
            .setClient(retrofitClient)
            .setLogLevel(retrofitLogLevel)
            .setLog(new Slf4jRetrofitLogger(JiraService.class));

    if (x509ConfiguredClient == null) {
      String credentials =
          String.format("%s:%s", jiraProperties.getUsername(), jiraProperties.getPassword());
      final String basic =
          String.format("Basic %s", Base64.encodeBase64String(credentials.getBytes()));
      builder.setRequestInterceptor(
          request -> {
            request.addHeader("Authorization", basic);
            request.addHeader("Accept", "application/json");
          });
    }

    return builder.build().create(JiraService.class);
  }
}
