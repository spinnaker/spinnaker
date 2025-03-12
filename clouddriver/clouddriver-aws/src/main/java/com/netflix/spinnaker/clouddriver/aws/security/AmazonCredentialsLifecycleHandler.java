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

package com.netflix.spinnaker.clouddriver.aws.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.Agent;
import com.netflix.spinnaker.cats.agent.AgentProvider;
import com.netflix.spinnaker.clouddriver.aws.AmazonCloudProvider;
import com.netflix.spinnaker.clouddriver.aws.AwsConfigurationProperties;
import com.netflix.spinnaker.clouddriver.aws.edda.EddaApiFactory;
import com.netflix.spinnaker.clouddriver.aws.provider.AwsCleanupProvider;
import com.netflix.spinnaker.clouddriver.aws.provider.AwsInfrastructureProvider;
import com.netflix.spinnaker.clouddriver.aws.provider.AwsProvider;
import com.netflix.spinnaker.clouddriver.aws.provider.agent.AmazonCachingAgentFilter;
import com.netflix.spinnaker.clouddriver.aws.provider.agent.ImageCachingAgent;
import com.netflix.spinnaker.clouddriver.aws.provider.agent.ReservationReportCachingAgent;
import com.netflix.spinnaker.clouddriver.aws.provider.config.ProviderHelpers;
import com.netflix.spinnaker.clouddriver.aws.provider.view.AmazonS3DataProvider;
import com.netflix.spinnaker.config.AwsConfiguration.DeployDefaults;
import com.netflix.spinnaker.credentials.CredentialsLifecycleHandler;
import com.netflix.spinnaker.credentials.CredentialsRepository;
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@Lazy
@Slf4j
@RequiredArgsConstructor
public class AmazonCredentialsLifecycleHandler
    implements CredentialsLifecycleHandler<NetflixAmazonCredentials> {
  private final AwsCleanupProvider awsCleanupProvider;
  private final AwsInfrastructureProvider awsInfrastructureProvider;
  private final AwsProvider awsProvider;
  private final AmazonCloudProvider amazonCloudProvider;
  private final AmazonClientProvider amazonClientProvider;
  private final AmazonS3DataProvider amazonS3DataProvider;

  private final AwsConfigurationProperties awsConfigurationProperties;
  private final ObjectMapper objectMapper;
  private final @Qualifier("amazonObjectMapper") ObjectMapper amazonObjectMapper;
  private final EddaApiFactory eddaApiFactory;
  private final ApplicationContext ctx;
  private final Registry registry;
  private final Optional<ExecutorService> reservationReportPool;
  private final Optional<Collection<AgentProvider>> agentProviders;
  private final EddaTimeoutConfig eddaTimeoutConfig;
  private final AmazonCachingAgentFilter amazonCachingAgentFilter;
  private final DynamicConfigService dynamicConfigService;
  private final DeployDefaults deployDefaults;
  private final CredentialsRepository<NetflixAmazonCredentials>
      credentialsRepository; // Circular dependency.
  protected Set<String> publicRegions = new HashSet<>();
  protected Set<String> awsInfraRegions = new HashSet<>();
  protected boolean reservationReportCachingAgentScheduled = false;
  protected boolean hasPreviouslyScheduledCleanupAgents = false;

  @Override
  public void credentialsAdded(@NotNull NetflixAmazonCredentials credentials) {
    scheduleAgents(credentials);
    scheduleReservationReportCachingAgent();
  }

  @Override
  public void credentialsUpdated(@NotNull NetflixAmazonCredentials credentials) {
    unscheduleAgents(credentials);
    scheduleAgents(credentials);
  }

  @Override
  public void credentialsDeleted(@NotNull NetflixAmazonCredentials credentials) {
    replaceCurrentImageCachingAgent(credentials);
    unscheduleAgents(credentials);
  }

  private void replaceCurrentImageCachingAgent(NetflixAmazonCredentials credentials) {
    List<ImageCachingAgent> currentImageCachingAgents =
        awsProvider.getAgents().stream()
            .filter(
                agent ->
                    agent.handlesAccount(credentials.getName())
                        && agent instanceof ImageCachingAgent
                        && ((ImageCachingAgent) agent).getIncludePublicImages())
            .map(agent -> (ImageCachingAgent) agent)
            .collect(Collectors.toList());

    for (ImageCachingAgent imageCachingAgent : currentImageCachingAgents) {
      NetflixAmazonCredentials replacementCredentials =
          credentialsRepository.getAll().stream()
              .filter(cred -> !cred.getName().equals(credentials.getName()))
              .filter(
                  cred ->
                      cred.getRegions().stream()
                          .map(AmazonCredentials.AWSRegion::getName)
                          .collect(Collectors.toSet())
                          .contains(imageCachingAgent.getRegion()))
              .findFirst()
              .orElse(null);
      if (replacementCredentials != null) {
        awsProvider.addAgents(
            Collections.singletonList(
                new ImageCachingAgent(
                    amazonClientProvider,
                    replacementCredentials,
                    imageCachingAgent.getRegion(),
                    objectMapper,
                    registry,
                    true,
                    dynamicConfigService)));
        continue;
      }
      publicRegions.remove(imageCachingAgent.getRegion());
    }
  }

  private void unscheduleAgents(NetflixAmazonCredentials credentials) {
    awsInfrastructureProvider.removeAgentsForAccounts(Collections.singleton(credentials.getName()));
    awsCleanupProvider.removeAgentsForAccounts(Collections.singleton(credentials.getName()));
    awsProvider.removeAgentsForAccounts(Collections.singleton(credentials.getName()));
  }

  private void scheduleAgents(NetflixAmazonCredentials credentials) {
    scheduleAWSProviderAgents(credentials);
    scheduleAwsInfrastructureProviderAgents(credentials);
    scheduleAwsCleanupAgents(credentials);
  }

  private void scheduleAwsInfrastructureProviderAgents(NetflixAmazonCredentials credentials) {
    ProviderHelpers.BuildResult result =
        ProviderHelpers.buildAwsInfrastructureAgents(
            credentials,
            awsInfrastructureProvider,
            credentialsRepository,
            amazonClientProvider,
            amazonObjectMapper,
            registry,
            eddaTimeoutConfig,
            this.awsInfraRegions);
    awsInfrastructureProvider.addAgents(result.getAgents());
    this.awsInfraRegions.addAll(result.getRegionsToAdd());
  }

  private void scheduleAWSProviderAgents(NetflixAmazonCredentials credentials) {
    ProviderHelpers.BuildResult buildResult =
        ProviderHelpers.buildAwsProviderAgents(
            credentials,
            credentialsRepository,
            amazonClientProvider,
            objectMapper,
            registry,
            eddaTimeoutConfig,
            amazonCachingAgentFilter,
            awsProvider,
            amazonCloudProvider,
            dynamicConfigService,
            eddaApiFactory,
            reservationReportPool,
            agentProviders,
            ctx,
            amazonS3DataProvider,
            publicRegions);

    awsProvider.addAgents(buildResult.getAgents());
    this.publicRegions.addAll(buildResult.getRegionsToAdd());
    awsProvider.synchronizeHealthAgents();
  }

  private void scheduleAwsCleanupAgents(NetflixAmazonCredentials credentials) {
    List<Agent> newlyAddedAgents =
        ProviderHelpers.buildAwsCleanupAgents(
            credentials,
            credentialsRepository,
            amazonClientProvider,
            awsCleanupProvider,
            deployDefaults,
            awsConfigurationProperties,
            hasPreviouslyScheduledCleanupAgents);

    awsCleanupProvider.addAgents(newlyAddedAgents);

    log.info(
        "The following cleanup agents have been added: {} (awsCleanupProvider.getAgentScheduler: {})",
        newlyAddedAgents,
        awsCleanupProvider.getAgentScheduler());

    hasPreviouslyScheduledCleanupAgents = true;
  }

  private void scheduleReservationReportCachingAgent() {
    if (reservationReportPool.isPresent() && !reservationReportCachingAgentScheduled) {
      for (Agent agent : awsProvider.getAgents()) {
        if (agent instanceof ReservationReportCachingAgent) {
          reservationReportCachingAgentScheduled = true;
          return;
        }
      }
      awsProvider.addAgents(
          Collections.singleton(
              new ReservationReportCachingAgent(
                  registry,
                  amazonClientProvider,
                  amazonS3DataProvider,
                  credentialsRepository,
                  objectMapper,
                  reservationReportPool.get(),
                  ctx)));
      reservationReportCachingAgentScheduled = true;
    }
  }
}
