/*
 * Copyright 2020 Netflix, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.aws.provider.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.Agent;
import com.netflix.spinnaker.cats.agent.AgentProvider;
import com.netflix.spinnaker.clouddriver.aws.AmazonCloudProvider;
import com.netflix.spinnaker.clouddriver.aws.AwsConfigurationProperties;
import com.netflix.spinnaker.clouddriver.aws.agent.CleanupAlarmsAgent;
import com.netflix.spinnaker.clouddriver.aws.agent.CleanupDetachedInstancesAgent;
import com.netflix.spinnaker.clouddriver.aws.agent.ReconcileClassicLinkSecurityGroupsAgent;
import com.netflix.spinnaker.clouddriver.aws.edda.EddaApiFactory;
import com.netflix.spinnaker.clouddriver.aws.provider.AwsCleanupProvider;
import com.netflix.spinnaker.clouddriver.aws.provider.AwsInfrastructureProvider;
import com.netflix.spinnaker.clouddriver.aws.provider.AwsProvider;
import com.netflix.spinnaker.clouddriver.aws.provider.agent.*;
import com.netflix.spinnaker.clouddriver.aws.provider.view.AmazonS3DataProvider;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.EddaTimeoutConfig;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.security.ProviderUtils;
import com.netflix.spinnaker.config.AwsConfiguration;
import com.netflix.spinnaker.credentials.CredentialsRepository;
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;

public class ProviderHelpers {

  @Getter
  @RequiredArgsConstructor
  public static class BuildResult {
    private final List<Agent> agents;
    private final Set<String> regionsToAdd;
  }

  public static BuildResult buildAwsInfrastructureAgents(
      NetflixAmazonCredentials credentials,
      AwsInfrastructureProvider awsInfrastructureProvider,
      CredentialsRepository<NetflixAmazonCredentials> credentialsRepository,
      AmazonClientProvider amazonClientProvider,
      ObjectMapper amazonObjectMapper,
      Registry registry,
      EddaTimeoutConfig eddaTimeoutConfig,
      Set<String> regions) {
    Set<String> scheduledAccounts = ProviderUtils.getScheduledAccounts(awsInfrastructureProvider);
    List<Agent> newlyAddedAgents = new ArrayList<>();
    for (NetflixAmazonCredentials.AWSRegion region : credentials.getRegions()) {
      if (!scheduledAccounts.contains(credentials.getName())) {
        if (regions.add(region.getName())) {
          newlyAddedAgents.add(
              new AmazonInstanceTypeCachingAgent(region.getName(), credentialsRepository));
        }
        newlyAddedAgents.add(
            new AmazonElasticIpCachingAgent(amazonClientProvider, credentials, region.getName()));
        newlyAddedAgents.add(
            new AmazonKeyPairCachingAgent(amazonClientProvider, credentials, region.getName()));
        newlyAddedAgents.add(
            new AmazonSecurityGroupCachingAgent(
                amazonClientProvider,
                credentials,
                region.getName(),
                amazonObjectMapper,
                registry,
                eddaTimeoutConfig));
        newlyAddedAgents.add(
            new AmazonSubnetCachingAgent(
                amazonClientProvider, credentials, region.getName(), amazonObjectMapper));
        newlyAddedAgents.add(
            new AmazonVpcCachingAgent(
                amazonClientProvider, credentials, region.getName(), amazonObjectMapper));
      }
    }
    return new BuildResult(newlyAddedAgents, regions);
  }

  public static BuildResult buildAwsProviderAgents(
      NetflixAmazonCredentials credentials,
      CredentialsRepository<NetflixAmazonCredentials> credentialsRepository,
      AmazonClientProvider amazonClientProvider,
      ObjectMapper objectMapper,
      Registry registry,
      EddaTimeoutConfig eddaTimeoutConfig,
      AmazonCachingAgentFilter amazonCachingAgentFilter,
      AwsProvider awsProvider,
      AmazonCloudProvider amazonCloudProvider,
      DynamicConfigService dynamicConfigService,
      EddaApiFactory eddaApiFactory,
      Optional<ExecutorService> reservationReportPool,
      Optional<Collection<AgentProvider>> agentProviders,
      ApplicationContext ctx,
      AmazonS3DataProvider amazonS3DataProvider,
      Set<String> publicRegions) {
    Set<String> scheduledAccounts = ProviderUtils.getScheduledAccounts(awsProvider);
    List<Agent> newlyAddedAgents = new ArrayList<>();
    newlyAddedAgents.add(new ClusterCleanupAgent());
    for (NetflixAmazonCredentials.AWSRegion region : credentials.getRegions()) {
      if (!scheduledAccounts.contains(credentials.getName())) {
        newlyAddedAgents.add(
            new ClusterCachingAgent(
                amazonCloudProvider,
                amazonClientProvider,
                credentials,
                region.getName(),
                objectMapper,
                registry,
                eddaTimeoutConfig,
                amazonCachingAgentFilter));
        newlyAddedAgents.add(
            new LaunchConfigCachingAgent(
                amazonClientProvider, credentials, region.getName(), objectMapper, registry));

        // always index private images per account/region
        newlyAddedAgents.add(
            new ImageCachingAgent(
                amazonClientProvider,
                credentials,
                region.getName(),
                objectMapper,
                registry,
                false,
                dynamicConfigService));

        if (!publicRegions.contains(region.getName())) {
          // only index public images once per region (regardless of account)
          publicRegions.add(region.getName());
          newlyAddedAgents.add(
              new ImageCachingAgent(
                  amazonClientProvider,
                  credentials,
                  region.getName(),
                  objectMapper,
                  registry,
                  true,
                  dynamicConfigService));
        }

        newlyAddedAgents.add(
            new InstanceCachingAgent(
                amazonClientProvider, credentials, region.getName(), objectMapper, registry));
        newlyAddedAgents.add(
            new AmazonLoadBalancerCachingAgent(
                amazonCloudProvider,
                amazonClientProvider,
                credentials,
                region.getName(),
                eddaApiFactory.createApi(credentials.getEdda(), region.getName()),
                objectMapper,
                registry,
                amazonCachingAgentFilter));
        newlyAddedAgents.add(
            new AmazonApplicationLoadBalancerCachingAgent(
                amazonCloudProvider,
                amazonClientProvider,
                credentials,
                region.getName(),
                eddaApiFactory.createApi(credentials.getEdda(), region.getName()),
                objectMapper,
                registry,
                eddaTimeoutConfig,
                amazonCachingAgentFilter));
        newlyAddedAgents.add(
            new ReservedInstancesCachingAgent(
                amazonClientProvider, credentials, region.getName(), objectMapper, registry));
        newlyAddedAgents.add(
            new AmazonCertificateCachingAgent(
                amazonClientProvider, credentials, region.getName(), objectMapper, registry));

        if (dynamicConfigService.isEnabled("aws.features.cloud-formation", false)) {
          newlyAddedAgents.add(
              new AmazonCloudFormationCachingAgent(
                  amazonClientProvider, credentials, region.getName(), registry));
        }
        if (credentials.getEddaEnabled()
            && !eddaTimeoutConfig.getDisabledRegions().contains(region.getName())) {
          newlyAddedAgents.add(
              new EddaLoadBalancerCachingAgent(
                  eddaApiFactory.createApi(credentials.getEdda(), region.getName()),
                  credentials,
                  region.getName(),
                  objectMapper));
        } else {
          newlyAddedAgents.add(
              new AmazonLoadBalancerInstanceStateCachingAgent(
                  amazonClientProvider, credentials, region.getName(), objectMapper, ctx));
        }
        if (dynamicConfigService.isEnabled("aws.features.launch-templates", false)) {
          newlyAddedAgents.add(
              new AmazonLaunchTemplateCachingAgent(
                  amazonClientProvider, credentials, region.getName(), objectMapper, registry));
        }
      }
    }
    agentProviders.ifPresent(
        providers ->
            providers.stream()
                .filter(it -> it.supports(AwsProvider.PROVIDER_NAME))
                .forEach(provider -> newlyAddedAgents.addAll(provider.agents(credentials))));
    return new BuildResult(newlyAddedAgents, publicRegions);
  }

  public static List<Agent> buildAwsCleanupAgents(
      NetflixAmazonCredentials credentials,
      CredentialsRepository<NetflixAmazonCredentials> credentialsRepository,
      AmazonClientProvider amazonClientProvider,
      AwsCleanupProvider awsCleanupProvider,
      AwsConfiguration.DeployDefaults deployDefaults,
      AwsConfigurationProperties awsConfigurationProperties) {
    Set<String> scheduledAccounts = ProviderUtils.getScheduledAccounts(awsCleanupProvider);
    List<Agent> newlyAddedAgents = new ArrayList<>();
    if (!scheduledAccounts.contains(credentials.getName())) {
      for (NetflixAmazonCredentials.AWSRegion region : credentials.getRegions()) {
        if (deployDefaults.isReconcileClassicLinkAccount(credentials)) {
          newlyAddedAgents.add(
              new ReconcileClassicLinkSecurityGroupsAgent(
                  amazonClientProvider, credentials, region.getName(), deployDefaults));
        }
      }
    }
    if (awsCleanupProvider.getAgentScheduler() == null) {
      if (awsConfigurationProperties.getCleanup().getAlarms().getEnabled()) {
        newlyAddedAgents.add(
            new CleanupAlarmsAgent(
                amazonClientProvider,
                credentialsRepository,
                awsConfigurationProperties.getCleanup().getAlarms().getDaysToKeep()));
      }
      newlyAddedAgents.add(
          new CleanupDetachedInstancesAgent(amazonClientProvider, credentialsRepository));
    }
    return newlyAddedAgents;
  }
}
