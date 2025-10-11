/*
 * Copyright 2025 Harness, Inc.
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

package com.netflix.spinnaker.clouddriver.azure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.Agent;
import com.netflix.spinnaker.clouddriver.azure.AzureCloudProvider;
import com.netflix.spinnaker.clouddriver.azure.resources.common.cache.provider.AzureInfrastructureProvider;
import com.netflix.spinnaker.clouddriver.azure.resources.loadbalancer.cache.AzureLoadBalancerCachingAgent;
import com.netflix.spinnaker.clouddriver.azure.resources.network.cache.AzureNetworkCachingAgent;
import com.netflix.spinnaker.clouddriver.azure.resources.securitygroup.cache.AzureSecurityGroupCachingAgent;
import com.netflix.spinnaker.clouddriver.azure.resources.servergroup.cache.AzureServerGroupCachingAgent;
import com.netflix.spinnaker.clouddriver.azure.resources.subnet.cache.AzureSubnetCachingAgent;
import com.netflix.spinnaker.clouddriver.azure.resources.vmimage.cache.AzureCustomImageCachingAgent;
import com.netflix.spinnaker.clouddriver.azure.resources.vmimage.cache.AzureManagedImageCachingAgent;
import com.netflix.spinnaker.clouddriver.azure.resources.vmimage.cache.AzureVMImageCachingAgent;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import com.netflix.spinnaker.credentials.CredentialsLifecycleHandler;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AzureCredentialsLifecycleHandler
    implements CredentialsLifecycleHandler<AzureNamedAccountCredentials> {

  private final AccountCredentialsProvider accountCredentialsProvider;
  private final AzureInfrastructureProvider azureInfrastructureProvider;
  private final AzureCloudProvider azureCloudProvider;
  private final ObjectMapper objectMapper;
  private final Registry registry;

  public AzureCredentialsLifecycleHandler(
      AccountCredentialsProvider accountCredentialsProvider,
      AzureInfrastructureProvider azureInfrastructureProvider,
      AzureCloudProvider azureCloudProvider,
      ObjectMapper objectMapper,
      Registry registry) {
    this.accountCredentialsProvider = accountCredentialsProvider;
    this.azureInfrastructureProvider = azureInfrastructureProvider;
    this.azureCloudProvider = azureCloudProvider;
    this.objectMapper = objectMapper;
    this.registry = registry;
  }

  @Override
  public void credentialsAdded(AzureNamedAccountCredentials credentials) {
    addAgentsForCredentials(credentials);
  }

  private void addAgentsForCredentials(AzureNamedAccountCredentials credentials) {
    List<Agent> agents = new ArrayList<>();

    // For each region in the account, add the necessary caching agents
    credentials
        .getRegions()
        .forEach(
            azureRegion -> {
              String regionName = azureRegion.getName();
              String accountName = credentials.getName();
              AzureCredentials creds = credentials.getCredentials();

              // Add caching agents for the account
              agents.add(
                  new AzureSecurityGroupCachingAgent(
                      azureCloudProvider, accountName, creds, regionName, objectMapper, registry));

              agents.add(
                  new AzureNetworkCachingAgent(
                      azureCloudProvider, accountName, creds, regionName, objectMapper));

              agents.add(
                  new AzureSubnetCachingAgent(
                      azureCloudProvider, accountName, creds, regionName, objectMapper));

              agents.add(
                  new AzureLoadBalancerCachingAgent(
                      azureCloudProvider, accountName, creds, regionName, objectMapper, registry));

              agents.add(
                  new AzureServerGroupCachingAgent(
                      azureCloudProvider, accountName, creds, regionName, objectMapper, registry));

              agents.add(
                  new AzureVMImageCachingAgent(
                      azureCloudProvider, accountName, creds, regionName, objectMapper));

              agents.add(
                  new AzureCustomImageCachingAgent(
                      azureCloudProvider,
                      accountName,
                      creds,
                      regionName,
                      credentials.getVmCustomImages(),
                      objectMapper));

              agents.add(
                  new AzureManagedImageCachingAgent(
                      azureCloudProvider, accountName, creds, regionName, objectMapper));
            });

    // Register agents using BaseProvider's addAgents method
    azureInfrastructureProvider.addAgents(agents);
    log.info("Added {} Azure caching agents for account {}", agents.size(), credentials.getName());
  }

  @Override
  public void credentialsUpdated(AzureNamedAccountCredentials credentials) {
    credentialsDeleted(credentials);
    credentialsAdded(credentials);
  }

  @Override
  public void credentialsDeleted(AzureNamedAccountCredentials credentials) {
    // Remove all agents for this account using BaseProvider's removeAgentsForAccounts method
    azureInfrastructureProvider.removeAgentsForAccounts(
        Collections.singleton(credentials.getName()));
    log.info("Removed Azure caching agents for account {}", credentials.getName());
  }
}
