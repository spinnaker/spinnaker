/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.lifecycle;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.agent.Agent;
import com.netflix.spinnaker.cats.agent.AgentProvider;
import com.netflix.spinnaker.clouddriver.aws.provider.AwsProvider;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import com.netflix.spinnaker.clouddriver.tags.EntityTagger;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class LaunchFailureNotificationAgentProvider implements AgentProvider {
  private static final String REGION_TEMPLATE_PATTERN = Pattern.quote("{{region}}");
  private static final String ACCOUNT_ID_TEMPLATE_PATTERN = Pattern.quote("{{accountId}}");

  private final ObjectMapper objectMapper;
  private final AmazonClientProvider amazonClientProvider;
  private final AccountCredentialsProvider accountCredentialsProvider;
  private final LaunchFailureConfigurationProperties properties;
  private final EntityTagger entityTagger;

  LaunchFailureNotificationAgentProvider(
      ObjectMapper objectMapper,
      AmazonClientProvider amazonClientProvider,
      AccountCredentialsProvider accountCredentialsProvider,
      LaunchFailureConfigurationProperties properties,
      EntityTagger entityTagger) {
    this.objectMapper = objectMapper;
    this.amazonClientProvider = amazonClientProvider;
    this.accountCredentialsProvider = accountCredentialsProvider;
    this.properties = properties;
    this.entityTagger = entityTagger;
  }

  @Override
  public boolean supports(String providerName) {
    return providerName.equalsIgnoreCase(AwsProvider.PROVIDER_NAME);
  }

  @Override
  public Collection<Agent> agents() {
    NetflixAmazonCredentials credentials =
        (NetflixAmazonCredentials)
            accountCredentialsProvider.getCredentials(properties.getAccountName());

    // an agent for each region in the specified account
    List<Agent> agents =
        credentials.getRegions().stream()
            .map(
                region ->
                    new LaunchFailureNotificationAgent(
                        objectMapper,
                        amazonClientProvider,
                        accountCredentialsProvider,
                        new LaunchFailureConfigurationProperties(
                            properties.getAccountName(),
                            properties
                                .getTopicARN()
                                .replaceAll(REGION_TEMPLATE_PATTERN, region.getName())
                                .replaceAll(
                                    ACCOUNT_ID_TEMPLATE_PATTERN, credentials.getAccountId()),
                            properties
                                .getQueueARN()
                                .replaceAll(REGION_TEMPLATE_PATTERN, region.getName())
                                .replaceAll(
                                    ACCOUNT_ID_TEMPLATE_PATTERN, credentials.getAccountId()),
                            properties.getMaxMessagesPerCycle(),
                            properties.getVisibilityTimeout(),
                            properties.getWaitTimeSeconds()),
                        entityTagger))
            .collect(Collectors.toList());

    // an agent that will cleanup stale notifications across all accounts + region
    agents.add(
        new LaunchFailureNotificationCleanupAgent(
            amazonClientProvider, accountCredentialsProvider, entityTagger));

    return agents;
  }
}
