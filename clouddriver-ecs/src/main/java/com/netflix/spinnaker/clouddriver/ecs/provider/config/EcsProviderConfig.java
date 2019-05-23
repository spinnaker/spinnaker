/*
 * Copyright 2017 Lookout, Inc.
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

package com.netflix.spinnaker.clouddriver.ecs.provider.config;

import static com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentials.AWSRegion;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.Agent;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.ecs.EcsCloudProvider;
import com.netflix.spinnaker.clouddriver.ecs.provider.EcsProvider;
import com.netflix.spinnaker.clouddriver.ecs.provider.agent.*;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository;
import com.netflix.spinnaker.clouddriver.security.ProviderUtils;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

@Configuration
public class EcsProviderConfig {

  @Bean
  public IamPolicyReader iamPolicyReader(ObjectMapper objectMapper) {
    return new IamPolicyReader(objectMapper);
  }

  @Bean
  @DependsOn("netflixECSCredentials")
  public EcsProvider ecsProvider(
      AccountCredentialsRepository accountCredentialsRepository,
      AmazonClientProvider amazonClientProvider,
      AWSCredentialsProvider awsCredentialsProvider,
      Registry registry,
      IamPolicyReader iamPolicyReader,
      ObjectMapper objectMapper) {
    EcsProvider provider =
        new EcsProvider(
            accountCredentialsRepository,
            Collections.newSetFromMap(new ConcurrentHashMap<Agent, Boolean>()));
    synchronizeEcsProvider(
        provider,
        accountCredentialsRepository,
        amazonClientProvider,
        awsCredentialsProvider,
        registry,
        iamPolicyReader,
        objectMapper);
    return provider;
  }

  private void synchronizeEcsProvider(
      EcsProvider ecsProvider,
      AccountCredentialsRepository accountCredentialsRepository,
      AmazonClientProvider amazonClientProvider,
      AWSCredentialsProvider awsCredentialsProvider,
      Registry registry,
      IamPolicyReader iamPolicyReader,
      ObjectMapper objectMapper) {

    Set<String> scheduledAccounts = ProviderUtils.getScheduledAccounts(ecsProvider);
    Set<NetflixAmazonCredentials> allAccounts =
        ProviderUtils.buildThreadSafeSetOfAccounts(
            accountCredentialsRepository, NetflixAmazonCredentials.class);
    List<Agent> newAgents = new LinkedList<>();

    for (NetflixAmazonCredentials credentials : allAccounts) {
      if (credentials.getCloudProvider().equals(EcsCloudProvider.ID)) {
        newAgents.add(
            new IamRoleCachingAgent(
                credentials,
                amazonClientProvider,
                awsCredentialsProvider,
                iamPolicyReader)); // IAM is region-agnostic, so one caching agent per account is
        // enough

        for (AWSRegion region : credentials.getRegions()) {
          if (!scheduledAccounts.contains(credentials.getName())) {
            newAgents.add(
                new EcsClusterCachingAgent(
                    credentials, region.getName(), amazonClientProvider, awsCredentialsProvider));
            newAgents.add(
                new ServiceCachingAgent(
                    credentials,
                    region.getName(),
                    amazonClientProvider,
                    awsCredentialsProvider,
                    registry));
            newAgents.add(
                new TaskCachingAgent(
                    credentials,
                    region.getName(),
                    amazonClientProvider,
                    awsCredentialsProvider,
                    registry));
            newAgents.add(
                new ContainerInstanceCachingAgent(
                    credentials,
                    region.getName(),
                    amazonClientProvider,
                    awsCredentialsProvider,
                    registry));
            newAgents.add(
                new TaskDefinitionCachingAgent(
                    credentials,
                    region.getName(),
                    amazonClientProvider,
                    awsCredentialsProvider,
                    registry,
                    objectMapper));
            newAgents.add(
                new TaskHealthCachingAgent(
                    credentials,
                    region.getName(),
                    amazonClientProvider,
                    awsCredentialsProvider,
                    objectMapper));
            newAgents.add(
                new EcsCloudMetricAlarmCachingAgent(
                    credentials, region.getName(), amazonClientProvider, awsCredentialsProvider));
            newAgents.add(
                new ScalableTargetsCachingAgent(
                    credentials,
                    region.getName(),
                    amazonClientProvider,
                    awsCredentialsProvider,
                    objectMapper));
            newAgents.add(
                new SecretCachingAgent(
                    credentials,
                    region.getName(),
                    amazonClientProvider,
                    awsCredentialsProvider,
                    objectMapper));
            newAgents.add(
                new ServiceDiscoveryCachingAgent(
                    credentials,
                    region.getName(),
                    amazonClientProvider,
                    awsCredentialsProvider,
                    objectMapper));
          }
        }
      }
    }

    ecsProvider.getAgents().addAll(newAgents);
    ecsProvider.synchronizeHealthAgents();
  }
}
