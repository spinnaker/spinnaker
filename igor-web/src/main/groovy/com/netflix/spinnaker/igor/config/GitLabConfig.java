/*
 * Copyright 2017 bol.com
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

package com.netflix.spinnaker.igor.config;

import com.netflix.spinnaker.igor.scm.gitlab.client.GitLabClient;
import com.netflix.spinnaker.igor.scm.gitlab.client.GitLabMaster;
import javax.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import retrofit.Endpoints;
import retrofit.RequestInterceptor;
import retrofit.RestAdapter;
import retrofit.client.OkClient;
import retrofit.converter.JacksonConverter;

@Configuration
@ConditionalOnProperty("gitlab.base-url")
@EnableConfigurationProperties(GitLabProperties.class)
public class GitLabConfig {
  private static final Logger log = LoggerFactory.getLogger(GitLabConfig.class);

  @Bean
  public GitLabMaster gitLabMasters(@Valid GitLabProperties gitLabProperties) {
    log.info("bootstrapping {} as gitlab", gitLabProperties.getBaseUrl());
    return new GitLabMaster(
        gitLabClient(gitLabProperties.getBaseUrl(), gitLabProperties.getPrivateToken()),
        gitLabProperties.getBaseUrl());
  }

  public GitLabClient gitLabClient(String address, String privateToken) {
    return new RestAdapter.Builder()
        .setEndpoint(Endpoints.newFixedEndpoint(address))
        .setRequestInterceptor(new PrivateTokenRequestInterceptor(privateToken))
        .setClient(new OkClient())
        .setConverter(new JacksonConverter())
        .build()
        .create(GitLabClient.class);
  }

  static class PrivateTokenRequestInterceptor implements RequestInterceptor {
    private final String privateToken;

    PrivateTokenRequestInterceptor(String privateToken) {
      this.privateToken = privateToken;
    }

    @Override
    public void intercept(RequestInterceptor.RequestFacade request) {
      request.addHeader("Private-Token", privateToken);
    }
  }
}
