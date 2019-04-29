/*
 * Copyright 2017 Netflix, Inc.
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.igor.IgorConfigurationProperties;
import com.netflix.spinnaker.igor.gitlabci.client.GitlabCiClient;
import com.netflix.spinnaker.igor.gitlabci.service.GitlabCiService;
import com.netflix.spinnaker.igor.service.BuildServices;
import com.squareup.okhttp.OkHttpClient;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
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
@ConditionalOnProperty("gitlab-ci.enabled")
@EnableConfigurationProperties(GitlabCiProperties.class)
public class GitlabCiConfig {
  private static final Logger log = LoggerFactory.getLogger(GitlabCiConfig.class);

  @Bean
  public Map<String, GitlabCiService> gitlabCiMasters(
      BuildServices buildServices,
      final IgorConfigurationProperties igorConfigurationProperties,
      GitlabCiProperties gitlabCiProperties,
      ObjectMapper objectMapper) {
    log.info("creating gitlabCiMasters");
    Map<String, GitlabCiService> gitlabCiMasters =
        gitlabCiProperties.getMasters().stream()
            .map(
                gitlabCiHost ->
                    gitlabCiService(
                        igorConfigurationProperties,
                        "gitlab-ci-" + gitlabCiHost.getName(),
                        gitlabCiHost,
                        objectMapper))
            .collect(Collectors.toMap(GitlabCiService::getName, Function.identity()));
    buildServices.addServices(gitlabCiMasters);
    return gitlabCiMasters;
  }

  private static GitlabCiService gitlabCiService(
      IgorConfigurationProperties igorConfigurationProperties,
      String name,
      GitlabCiProperties.GitlabCiHost host,
      ObjectMapper objectMapper) {
    return new GitlabCiService(
        gitlabCiClient(
            host.getAddress(),
            host.getPrivateToken(),
            igorConfigurationProperties.getClient().getTimeout(),
            objectMapper),
        name,
        host.getAddress(),
        host.getLimitByMembership(),
        host.getLimitByOwnership(),
        host.getPermissions().build());
  }

  public static GitlabCiClient gitlabCiClient(
      String address, String privateToken, int timeout, ObjectMapper objectMapper) {
    OkHttpClient client = new OkHttpClient();
    client.setReadTimeout(timeout, TimeUnit.MILLISECONDS);

    // Need this code because without FULL log level, fetching logs will fail. Ref
    // https://github.com/square/retrofit/issues/953.
    RestAdapter.Log fooLog = message -> {};
    return new RestAdapter.Builder()
        .setEndpoint(Endpoints.newFixedEndpoint(address))
        .setRequestInterceptor(new GitlabCiHeaders(privateToken))
        .setClient(new OkClient(client))
        .setLog(fooLog)
        .setLogLevel(RestAdapter.LogLevel.FULL)
        .setConverter(new JacksonConverter(objectMapper))
        .build()
        .create(GitlabCiClient.class);
  }

  public static class GitlabCiHeaders implements RequestInterceptor {
    GitlabCiHeaders(String privateToken) {
      this.privateToken = privateToken;
    }

    @Override
    public void intercept(RequestFacade request) {
      if (!StringUtils.isEmpty(privateToken)) {
        request.addHeader("PRIVATE-TOKEN", privateToken);
      }
    }

    private String privateToken;
  }
}
