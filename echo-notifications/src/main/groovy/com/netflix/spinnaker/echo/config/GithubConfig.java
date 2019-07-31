/*
 * Copyright 2018 Schibsted ASA
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

package com.netflix.spinnaker.echo.config;

import com.netflix.spinnaker.echo.github.GithubService;
import com.netflix.spinnaker.retrofit.Slf4jRetrofitLogger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import retrofit.Endpoint;
import retrofit.Endpoints;
import retrofit.RestAdapter;
import retrofit.client.Client;
import retrofit.converter.JacksonConverter;

@Configuration
@ConditionalOnProperty("github-status.enabled")
@Slf4j
public class GithubConfig {
  static final String GITHUB_STATUS_URL = "https://api.github.com";

  @Bean
  public Endpoint githubEndpoint() {
    String endpoint = GITHUB_STATUS_URL;
    return Endpoints.newFixedEndpoint(endpoint);
  }

  @Bean
  public GithubService githubService(
      Endpoint githubEndpoint, Client retrofitClient, RestAdapter.LogLevel retrofitLogLevel) {
    log.info("Github service loaded");

    GithubService githubClient =
        new RestAdapter.Builder()
            .setEndpoint(githubEndpoint)
            .setConverter(new JacksonConverter())
            .setClient(retrofitClient)
            .setLogLevel(RestAdapter.LogLevel.FULL)
            .setLog(new Slf4jRetrofitLogger(GithubService.class))
            .build()
            .create(GithubService.class);

    return githubClient;
  }
}
