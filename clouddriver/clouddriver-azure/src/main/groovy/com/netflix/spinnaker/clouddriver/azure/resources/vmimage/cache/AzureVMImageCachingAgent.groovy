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

package com.netflix.spinnaker.clouddriver.azure.resources.vmimage.cache

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.agent.AccountAware
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.agent.CachingAgent
import com.netflix.spinnaker.cats.agent.DefaultCacheResult
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.azure.AzureCloudProvider
import com.netflix.spinnaker.clouddriver.azure.resources.common.cache.Keys
import com.netflix.spinnaker.clouddriver.azure.resources.common.cache.provider.AzureInfrastructureProvider
import com.netflix.spinnaker.clouddriver.azure.resources.vmimage.model.AzureVMImage
import com.netflix.spinnaker.clouddriver.azure.security.AzureCredentials
import com.netflix.spinnaker.clouddriver.cache.CustomScheduledAgent
import groovy.util.logging.Slf4j

import java.util.concurrent.TimeUnit

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE

@Slf4j
class AzureVMImageCachingAgent implements CachingAgent, CustomScheduledAgent, AccountAware {

  public static final long DEFAULT_POLL_INTERVAL_MILLIS = TimeUnit.HOURS.toMillis(2)
  public static final long DEFAULT_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(30)

  final AzureCloudProvider azureCloudProvider
  final String accountName
  final AzureCredentials creds
  final String region
  final ObjectMapper objectMapper

  final long pollIntervalMillis
  final long timeoutMillis

  static final Set<AgentDataType> types = Collections.unmodifiableSet([
    AUTHORITATIVE.forType(Keys.Namespace.AZURE_VMIMAGES.ns)
  ] as Set)

  AzureVMImageCachingAgent(AzureCloudProvider azureCloudProvider,
                           String accountName,
                           AzureCredentials creds,
                           String region,
                           ObjectMapper objectMapper) {
    this(azureCloudProvider, accountName, creds, region, objectMapper, DEFAULT_POLL_INTERVAL_MILLIS, DEFAULT_TIMEOUT_MILLIS)
  }

  AzureVMImageCachingAgent(AzureCloudProvider azureCloudProvider,
                           String accountName,
                           AzureCredentials creds,
                           String region,
                           ObjectMapper objectMapper,
                           long pollIntervalMillis,
                           long timeoutMillis) {
    this.azureCloudProvider = azureCloudProvider
    this.accountName = accountName
    this.creds = creds
    this.region = region
    this.objectMapper = objectMapper
    this.pollIntervalMillis = pollIntervalMillis
    this.timeoutMillis = timeoutMillis
  }

  @Override
  String getProviderName() {
    AzureInfrastructureProvider.PROVIDER_NAME
  }

  @Override
  String getAgentType() {
    "${accountName}/${region}/${AzureVMImageCachingAgent.simpleName}"
  }

  @Override
  String getAccountName() {
    accountName
  }

  @Override
  Collection<AgentDataType> getProvidedDataTypes() {
    return types
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    log.info("Describing items in ${agentType}")

    def vmImages = creds.computeClient.getVMImagesAll(region)

    List<CacheData> data = vmImages.collect() { AzureVMImage vmImage ->
      Map<String, Object> attributes = [vmimage: vmImage]
      def vmImageName = "${vmImage.offer}-${vmImage.sku}"
      def vmImageVersion = "${vmImage.version}(${vmImage.publisher})"
      new DefaultCacheData(Keys.getVMImageKey(azureCloudProvider, accountName, region, vmImageName, vmImageVersion),
        attributes,
        [:])
    }

    log.info("Caching ${data.size()} items in ${agentType}")
    new DefaultCacheResult([(Keys.Namespace.AZURE_VMIMAGES.ns): data])
  }
}
