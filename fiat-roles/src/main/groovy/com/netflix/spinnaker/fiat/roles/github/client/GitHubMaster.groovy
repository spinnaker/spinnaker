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

package com.netflix.spinnaker.fiat.roles.github.client

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.netflix.spinnaker.fiat.roles.github.GitHubProperties
import org.springframework.context.annotation.Bean
import retrofit.Endpoints
import retrofit.RequestInterceptor
import retrofit.RestAdapter
import retrofit.client.OkClient
import retrofit.converter.SimpleXMLConverter

import javax.validation.Valid

/**
 * Wrapper class for a collection of GitHub clients
 */
class GitHubMaster {
  GitHubClient gitHubClient
  String baseUrl

  @Bean
  GitHubMaster gitHubMasters(@Valid GitHubProperties gitHubProperties) {
    new GitHubMaster(gitHubClient: gitHubClient(gitHubProperties.baseUrl, gitHubProperties.accessToken),
                     baseUrl: gitHubProperties.baseUrl)
  }


  static GitHubClient gitHubClient(String address, String accessToken) {
    new RestAdapter.Builder()
        .setEndpoint(Endpoints.newFixedEndpoint(address))
        .setRequestInterceptor(new BasicAuthRequestInterceptor(accessToken))
        .setClient(new OkClient())
        .setConverter(new SimpleXMLConverter())
        .build()
        .create(GitHubClient)
  }

  static class BasicAuthRequestInterceptor implements RequestInterceptor {
    final String accessToken

    BasicAuthRequestInterceptor(String accessToken) {
      this.accessToken = accessToken
    }

    @Override
    void intercept(RequestInterceptor.RequestFacade request) {
      request.addQueryParam("access_token", accessToken)
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  static class TeamMembership {
    String role
    String state
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  static class Team {
    Long id
    String name
    String slug
  }
}
