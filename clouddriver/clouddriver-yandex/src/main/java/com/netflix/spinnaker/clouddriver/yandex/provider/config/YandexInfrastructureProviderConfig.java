/*
 * Copyright 2020 YANDEX LLC
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

package com.netflix.spinnaker.clouddriver.yandex.provider.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.Agent;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository;
import com.netflix.spinnaker.clouddriver.security.ProviderUtils;
import com.netflix.spinnaker.clouddriver.yandex.provider.YandexInfrastructureProvider;
import com.netflix.spinnaker.clouddriver.yandex.provider.agent.*;
import com.netflix.spinnaker.clouddriver.yandex.security.YandexCloudCredentials;
import com.netflix.spinnaker.clouddriver.yandex.service.YandexCloudFacade;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

@Configuration
public class YandexInfrastructureProviderConfig {
  @Bean
  @DependsOn("yandexCloudCredentials")
  public YandexInfrastructureProvider yandexInfrastructureProvider(
      AccountCredentialsRepository accountCredentialsRepository,
      YandexCloudFacade yandexCloudFacade,
      ObjectMapper objectMapper,
      Registry registry) {
    objectMapper.enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    Set<YandexCloudCredentials> allAccounts =
        ProviderUtils.buildThreadSafeSetOfAccounts(
            accountCredentialsRepository, YandexCloudCredentials.class);
    List<Agent> agents = new ArrayList<>(7 * allAccounts.size());
    for (YandexCloudCredentials credentials : allAccounts) {
      agents.add(new YandexNetworkCachingAgent(credentials, objectMapper, yandexCloudFacade));
      agents.add(new YandexSubnetCachingAgent(credentials, objectMapper, yandexCloudFacade));
      agents.add(new YandexInstanceCachingAgent(credentials, objectMapper, yandexCloudFacade));
      agents.add(
          new YandexServerGroupCachingAgent(
              credentials, registry, objectMapper, yandexCloudFacade));
      agents.add(
          new YandexNetworkLoadBalancerCachingAgent(
              credentials, objectMapper, registry, yandexCloudFacade));
      agents.add(new YandexImageCachingAgent(credentials, objectMapper, yandexCloudFacade));
      agents.add(
          new YandexServiceAccountCachingAgent(credentials, objectMapper, yandexCloudFacade));
    }
    return new YandexInfrastructureProvider(agents);
  }
}
