/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.fiat.config

import com.netflix.spinnaker.fiat.roles.github.GitHubProperties
import com.netflix.spinnaker.fiat.roles.github.client.GitHubClient
import com.netflix.spinnaker.fiat.roles.github.client.GitHubMaster
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import retrofit.Endpoints
import retrofit.RequestInterceptor
import retrofit.RestAdapter
import retrofit.client.OkClient
import retrofit.converter.JacksonConverter

import javax.validation.Valid

/**
 * Converts the list of GitHub Configuration properties a collection of clients to access the GitHub hosts
 */
@Configuration
@ConditionalOnProperty(value = "auth.groupMembership.service", havingValue = "github")
@Slf4j
@CompileStatic
class GitHubConfig {

  @Bean
  GitHubMaster gitHubMasters(@Valid GitHubProperties gitHubProperties) {
    log.info "bootstrapping ${gitHubProperties.baseUrl} as github"
    new GitHubMaster(gitHubClient: gitHubClient(gitHubProperties.baseUrl, gitHubProperties.accessToken),
                     baseUrl: gitHubProperties.baseUrl)
  }

  static GitHubClient gitHubClient(String address, String accessToken) {
    new RestAdapter.Builder()
        .setEndpoint(Endpoints.newFixedEndpoint(address))
        .setRequestInterceptor(new BasicAuthRequestInterceptor(accessToken: accessToken))
        .setClient(new OkClient())
        .setConverter(new JacksonConverter())
        .build()
        .create(GitHubClient)
  }

  static class BasicAuthRequestInterceptor implements RequestInterceptor {
    String accessToken

    @Override
    void intercept(RequestInterceptor.RequestFacade request) {
      request.addQueryParam("access_token", accessToken)
    }
  }
}
