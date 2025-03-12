/*
 * Copyright 2019 Alibaba Group.
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
package com.netflix.spinnaker.clouddriver.alicloud.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.Agent;
import com.netflix.spinnaker.clouddriver.alicloud.AliCloudProvider;
import com.netflix.spinnaker.clouddriver.alicloud.common.ClientFactory;
import com.netflix.spinnaker.clouddriver.alicloud.provider.agent.*;
import com.netflix.spinnaker.clouddriver.alicloud.security.AliCloudClientProvider;
import com.netflix.spinnaker.clouddriver.alicloud.security.AliCloudCredentials;
import com.netflix.spinnaker.clouddriver.alicloud.security.AliCloudCredentialsProvider;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository;
import com.netflix.spinnaker.clouddriver.security.ProviderUtils;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Scope;

@Configuration
public class AliProviderConfig {

  @Bean
  @DependsOn("synchronizeAliCloudAccounts")
  public AliProvider aliProvider(
      AccountCredentialsRepository accountCredentialsRepository,
      AliCloudClientProvider aliCloudClientProvider,
      AliCloudCredentialsProvider aliCloudCredentialsProvider,
      Registry registry,
      ObjectMapper objectMapper,
      AliCloudProvider aliCloudProvider,
      ApplicationContext ctx,
      ClientFactory clientFactory) {
    AliProvider provider =
        new AliProvider(
            accountCredentialsRepository,
            Collections.newSetFromMap(new ConcurrentHashMap<Agent, Boolean>()));
    synchronizeAliProvider(
        provider,
        accountCredentialsRepository,
        aliCloudClientProvider,
        aliCloudCredentialsProvider,
        registry,
        objectMapper,
        aliCloudProvider,
        ctx,
        clientFactory);
    return provider;
  }

  @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
  @Bean
  public AliProviderSynchronizer synchronizeAliProvider(
      AliProvider aliProvider,
      AccountCredentialsRepository accountCredentialsRepository,
      AliCloudClientProvider aliCloudClientProvider,
      AliCloudCredentialsProvider aliCloudCredentialsProvider,
      Registry registry,
      ObjectMapper objectMapper,
      AliCloudProvider aliCloudProvider,
      ApplicationContext ctx,
      ClientFactory clientFactory) {

    Set<String> scheduledAccounts = ProviderUtils.getScheduledAccounts(aliProvider);
    Set<AliCloudCredentials> allAccounts =
        ProviderUtils.buildThreadSafeSetOfAccounts(
            accountCredentialsRepository, AliCloudCredentials.class);
    List<Agent> newAgents = new LinkedList<>();

    for (AliCloudCredentials credentials : allAccounts) {
      if (credentials.getCloudProvider().equals(AliCloudProvider.ID)) {

        for (String region : credentials.getRegions()) {
          if (!scheduledAccounts.contains(credentials.getName())) {
            newAgents.add(
                new AliCloudLoadBalancerCachingAgent(
                    aliProvider,
                    region,
                    aliCloudClientProvider,
                    aliCloudCredentialsProvider,
                    aliCloudProvider,
                    objectMapper,
                    registry,
                    credentials,
                    clientFactory.createClient(
                        region, credentials.getAccessKeyId(), credentials.getAccessSecretKey())));
            newAgents.add(
                new AliCloudLoadBalancerInstanceStateCachingAgent(
                    ctx,
                    credentials,
                    region,
                    objectMapper,
                    clientFactory.createClient(
                        region, credentials.getAccessKeyId(), credentials.getAccessSecretKey())));
            newAgents.add(
                new AliCloudSecurityGroupCachingAgent(
                    credentials,
                    region,
                    objectMapper,
                    clientFactory.createClient(
                        region, credentials.getAccessKeyId(), credentials.getAccessSecretKey())));
          }
        }
      }
    }

    aliProvider.getAgents().addAll(newAgents);
    return new AliProviderSynchronizer();
  }

  class AliProviderSynchronizer {}
}
