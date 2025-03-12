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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.config.DefaultServiceEndpoint;
import com.netflix.spinnaker.gate.services.SlackService;
import com.netflix.spinnaker.kork.client.ServiceClientProvider;
import java.io.IOException;
import java.util.List;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SlackConfigProperties.class)
public class SlackConfig {

  private SlackConfigProperties slackConfigProperties;

  @Autowired
  public SlackConfig(SlackConfigProperties slackConfigProperties) {
    this.slackConfigProperties = slackConfigProperties;
  }

  @Bean
  SlackService slackService(ServiceClientProvider serviceClientProvider) {
    String token = "Token token=" + slackConfigProperties.token;
    return serviceClientProvider.getService(
        SlackService.class,
        new DefaultServiceEndpoint("slack", slackConfigProperties.getBaseUrl()),
        new ObjectMapper(),
        List.of(new RequestHeaderInterceptor(token)));
  }

  static class RequestHeaderInterceptor implements Interceptor {

    private final String token;

    RequestHeaderInterceptor(String token) {
      this.token = token;
    }

    @Override
    public @NotNull Response intercept(Chain chain) throws IOException {
      Request request = chain.request();

      Request newRequest =
          request
              .newBuilder()
              .header("Authorization", token)
              .header("Accept", "application/vnd.pagerduty+json;version=2")
              .build();

      return chain.proceed(newRequest);
    }
  }
}
