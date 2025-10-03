/*
 * Copyright 2015 The original authors.
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

package com.netflix.spinnaker.clouddriver.azure.security

import com.netflix.spinnaker.clouddriver.azure.client.AzureBaseClient
import com.netflix.spinnaker.clouddriver.azure.client.AzureComputeClient
import com.netflix.spinnaker.clouddriver.azure.client.AzureNetworkClient
import com.netflix.spinnaker.clouddriver.azure.client.AzureResourceManagerClient
import com.netflix.spinnaker.clouddriver.azure.client.AzureStorageClient
import groovy.util.logging.Slf4j

@Slf4j
class AzureCredentials {

  final String tenantId
  final String clientId
  final String appKey
  final String project
  final String subscriptionId
  final String defaultKeyVault
  final String defaultResourceGroup
  final String userAgentApplicationName
  final String configuredAzureEnvironment
  final Boolean useSshPublicKey

  final AzureResourceManagerClient resourceManagerClient
  final AzureNetworkClient networkClient
  final AzureComputeClient computeClient
  final AzureStorageClient storageClient

  AzureCredentials(String tenantId, String clientId, String appKey, String subscriptionId, String defaultKeyVault, String defaultResourceGroup, String userAgentApplicationName, String configuredAzureEnvironment, Boolean useSshPublicKey) {
    this.tenantId = tenantId
    this.clientId = clientId
    this.appKey = appKey
    this.subscriptionId = subscriptionId
    this.project = "AzureProject"
    this.defaultKeyVault = defaultKeyVault
    this.defaultResourceGroup = defaultResourceGroup
    this.userAgentApplicationName = userAgentApplicationName
    this.configuredAzureEnvironment = configuredAzureEnvironment
    this.useSshPublicKey = useSshPublicKey

    // Initialize clients - authentication happens lazily to avoid blocking startup with bad credentials (like AWS/Google)
    def token = null
    def azureProfile = null
    
    try {
      token = AzureBaseClient.getTokenCredentials(this.clientId, this.tenantId, this.appKey, this.configuredAzureEnvironment)
      azureProfile = AzureBaseClient.getAzureProfile(this.configuredAzureEnvironment)
    } catch (Exception e) {
      log.warn("Failed to initialize Azure credentials for account. Will retry on first use.", e)
    }

    resourceManagerClient = token ? new AzureResourceManagerClient(this.subscriptionId, token, azureProfile) : null

    networkClient = token ? new AzureNetworkClient(this.subscriptionId, token, azureProfile) : null

    computeClient = token ? new AzureComputeClient(this.subscriptionId, token, azureProfile) : null

    storageClient = token ? new AzureStorageClient(this.subscriptionId, token, azureProfile) : null

    // Register providers only if authentication succeeded
    if (token) {
      try {
        registerProviders()
      } catch (Exception e) {
        log.error("Failed to register providers with AzureResourceManagerClient", e)
        // Don't throw - allow clouddriver to start even if registration fails
      }
    }
  }

  /**
   * For each client, register the associated provider.
   */
  private void registerProviders() {
    resourceManagerClient.register(resourceManagerClient)
    networkClient.register(resourceManagerClient)
    computeClient.register(resourceManagerClient)
    storageClient.register(resourceManagerClient)
  }
}
