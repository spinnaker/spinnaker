/*
 * Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License
 * for the specific language governing permissions and limitations under
 * the License.
 */

package com.netflix.spinnaker.clouddriver.ecs.security;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.Agent;
import com.netflix.spinnaker.cats.module.CatsModule;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentials;
import com.netflix.spinnaker.clouddriver.ecs.provider.EcsProvider;
import com.netflix.spinnaker.clouddriver.ecs.provider.agent.ApplicationCachingAgent;
import com.netflix.spinnaker.clouddriver.ecs.provider.agent.ContainerInstanceCachingAgent;
import com.netflix.spinnaker.clouddriver.ecs.provider.agent.EcsCloudMetricAlarmCachingAgent;
import com.netflix.spinnaker.clouddriver.ecs.provider.agent.EcsClusterCachingAgent;
import com.netflix.spinnaker.clouddriver.ecs.provider.agent.IamPolicyReader;
import com.netflix.spinnaker.clouddriver.ecs.provider.agent.IamRoleCachingAgent;
import com.netflix.spinnaker.clouddriver.ecs.provider.agent.ScalableTargetsCachingAgent;
import com.netflix.spinnaker.clouddriver.ecs.provider.agent.SecretCachingAgent;
import com.netflix.spinnaker.clouddriver.ecs.provider.agent.ServiceCachingAgent;
import com.netflix.spinnaker.clouddriver.ecs.provider.agent.ServiceDiscoveryCachingAgent;
import com.netflix.spinnaker.clouddriver.ecs.provider.agent.TargetHealthCachingAgent;
import com.netflix.spinnaker.clouddriver.ecs.provider.agent.TaskCachingAgent;
import com.netflix.spinnaker.clouddriver.ecs.provider.agent.TaskDefinitionCachingAgent;
import com.netflix.spinnaker.clouddriver.ecs.provider.agent.TaskHealthCachingAgent;
import com.netflix.spinnaker.clouddriver.ecs.provider.view.EcsAccountMapper;
import com.netflix.spinnaker.clouddriver.security.ProviderUtils;
import com.netflix.spinnaker.credentials.CredentialsLifecycleHandler;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
@Lazy
public class EcsCredentialsLifeCycleHandler
    implements CredentialsLifecycleHandler<NetflixECSCredentials> {
  protected final EcsProvider ecsProvider;
  protected final AmazonClientProvider amazonClientProvider;
  protected final AWSCredentialsProvider awsCredentialsProvider;
  protected final Registry registry;
  protected final IamPolicyReader iamPolicyReader;
  protected final ObjectMapper objectMapper;
  protected final CatsModule catsModule;
  protected final EcsAccountMapper ecsAccountMapper;

  @Override
  public void credentialsAdded(@NotNull NetflixECSCredentials credentials) {
    log.info("ECS account, {}, was added. Scheduling caching agents", credentials.getName());
    if (credentials instanceof NetflixAssumeRoleEcsCredentials) {
      ecsAccountMapper.addMapEntry(((NetflixAssumeRoleEcsCredentials) credentials));
    }
    scheduleAgents(credentials);
    log.debug("Caching agents scheduled for ECS account {}", credentials.getName());
  }

  @Override
  public void credentialsUpdated(@NotNull NetflixECSCredentials credentials) {
    log.info("ECS account, {}, was updated. Updating caching agents", credentials.getName());
    ecsProvider.removeAgentsForAccounts(Collections.singleton(credentials.getName()));
    scheduleAgents(credentials);
    log.debug("Caching agents rescheduled for ECS account {}", credentials.getName());
  }

  @Override
  public void credentialsDeleted(NetflixECSCredentials credentials) {
    log.info("ECS account, {}, was deleted. Removing caching agents", credentials.getName());
    ecsProvider.removeAgentsForAccounts(Collections.singleton(credentials.getName()));
    ecsAccountMapper.removeMapEntry(credentials.getName());
    ecsProvider.synchronizeHealthAgents();
    log.debug("Caching agents removed for ECS account {}", credentials.getName());
  }

  private void scheduleAgents(NetflixECSCredentials credentials) {
    Set<String> scheduledAccounts = ProviderUtils.getScheduledAccounts(ecsProvider);
    List<Agent> newAgents = new LinkedList<>();
    newAgents.add(new IamRoleCachingAgent(credentials, amazonClientProvider, iamPolicyReader));
    newAgents.add(
        new ApplicationCachingAgent(
            credentials,
            "us-east-1",
            amazonClientProvider,
            awsCredentialsProvider,
            registry,
            objectMapper));
    if (!scheduledAccounts.contains(credentials.getName())) {
      for (AmazonCredentials.AWSRegion region : credentials.getRegions()) {
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
                credentials, region.getName(), amazonClientProvider));
        newAgents.add(
            new ScalableTargetsCachingAgent(
                credentials, region.getName(), amazonClientProvider, objectMapper));
        newAgents.add(new SecretCachingAgent(credentials, region.getName(), amazonClientProvider));
        newAgents.add(
            new ServiceDiscoveryCachingAgent(credentials, region.getName(), amazonClientProvider));
        newAgents.add(
            new TargetHealthCachingAgent(
                credentials,
                region.getName(),
                amazonClientProvider,
                awsCredentialsProvider,
                objectMapper));
      }
    }

    ecsProvider.addAgents(newAgents);
    ecsProvider.synchronizeHealthAgents();
  }
}
