/*
 * Copyright 2019 Huawei Technologies Co.,Ltd.
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

package com.netflix.spinnaker.clouddriver.huaweicloud.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.Agent;
import com.netflix.spinnaker.clouddriver.huaweicloud.provider.agent.HuaweiCloudNetworkCachingAgent;
import com.netflix.spinnaker.clouddriver.huaweicloud.provider.agent.HuaweiCloudSecurityGroupCachingAgent;
import com.netflix.spinnaker.clouddriver.huaweicloud.security.HuaweiCloudNamedAccountCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository;
import com.netflix.spinnaker.clouddriver.security.ProviderUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Scope;

@Configuration
public class HuaweiCloudInfrastructureProviderConfig {

  @Bean
  @DependsOn("synchronizeHuaweiCloudNamedAccountCredentials")
  public HuaweiCloudInfrastructureProvider huaweiCloudInfastructureProvider(
      AccountCredentialsRepository accountCredentialsRepository,
      ObjectMapper objectMapper,
      Registry registry) {

    HuaweiCloudInfrastructureProvider provider =
        new HuaweiCloudInfrastructureProvider(
            Collections.newSetFromMap(new ConcurrentHashMap<Agent, Boolean>()));

    synchronizeHuaweiCloudInfrastructureProvider(
        provider, accountCredentialsRepository, objectMapper, registry);

    return provider;
  }

  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  @Bean
  public HuaweiCloudInfrastructureProviderSynchronizer synchronizeHuaweiCloudInfrastructureProvider(
      HuaweiCloudInfrastructureProvider infastructureProvider,
      AccountCredentialsRepository accountCredentialsRepository,
      ObjectMapper objectMapper,
      Registry registry) {

    Set<String> scheduledAccounts = ProviderUtils.getScheduledAccounts(infastructureProvider);

    Set<HuaweiCloudNamedAccountCredentials> allAccounts =
        ProviderUtils.buildThreadSafeSetOfAccounts(
            accountCredentialsRepository, HuaweiCloudNamedAccountCredentials.class);

    List<Agent> newlyAddedAgents = new ArrayList();

    allAccounts.forEach(
        credentials -> {
          if (!scheduledAccounts.contains(credentials.getName())) {
            credentials
                .getRegions()
                .forEach(
                    region -> {
                      newlyAddedAgents.add(
                          new HuaweiCloudNetworkCachingAgent(credentials, objectMapper, region));
                      newlyAddedAgents.add(
                          new HuaweiCloudSecurityGroupCachingAgent(
                              credentials, objectMapper, registry, region));
                    });
          }
        });

    if (infastructureProvider.getAgentScheduler() != null) {
      ProviderUtils.rescheduleAgents(infastructureProvider, newlyAddedAgents);
    }

    if (!newlyAddedAgents.isEmpty()) {
      infastructureProvider.getAgents().addAll(newlyAddedAgents);
    }

    return new HuaweiCloudInfrastructureProviderSynchronizer();
  }

  class HuaweiCloudInfrastructureProviderSynchronizer {}
}
