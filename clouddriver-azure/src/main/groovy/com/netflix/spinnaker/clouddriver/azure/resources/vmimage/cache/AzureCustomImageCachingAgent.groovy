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
import com.netflix.spinnaker.clouddriver.azure.client.AzureStorageClient
import com.netflix.spinnaker.clouddriver.azure.resources.common.cache.Keys
import com.netflix.spinnaker.clouddriver.azure.resources.common.cache.provider.AzureInfrastructureProvider
import com.netflix.spinnaker.clouddriver.azure.resources.vmimage.model.AzureCustomImageStorage
import com.netflix.spinnaker.clouddriver.azure.resources.vmimage.model.AzureCustomVMImage
import com.netflix.spinnaker.clouddriver.azure.security.AzureCredentials
import groovy.util.logging.Slf4j

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE

@Slf4j
class AzureCustomImageCachingAgent implements CachingAgent, AccountAware {

  final AzureCloudProvider azureCloudProvider
  final String accountName
  final AzureCredentials creds
  final String region
  final List<AzureCustomImageStorage> vmCustomImages
  final ObjectMapper objectMapper

  static final Set<AgentDataType> types = Collections.unmodifiableSet([
    AUTHORITATIVE.forType(Keys.Namespace.AZURE_VMIMAGES.ns)
  ] as Set)

  AzureCustomImageCachingAgent(AzureCloudProvider azureCloudProvider,
                               String accountName,
                               AzureCredentials creds,
                               String region,
                               List<AzureCustomImageStorage> vmCustomImages,
                               ObjectMapper objectMapper) {
    this.azureCloudProvider = azureCloudProvider
    this.accountName = accountName
    this.creds = creds
    this.region = region
    this.vmCustomImages = vmCustomImages.findAll {it.region == region}
    this.objectMapper = objectMapper

  }

  @Override
  String getProviderName() {
    AzureInfrastructureProvider.PROVIDER_NAME
  }

  @Override
  String getAgentType() {
    "${accountName}/${region}/${AzureCustomImageCachingAgent.simpleName}"
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
    List<CacheData> data = []

    // return a list of virtual machine images as read from the config.yml file
    def customImages = AzureStorageClient.getCustomImages(vmCustomImages)

    data.addAll( customImages.collect { AzureCustomVMImage vmImage ->
      Map<String, Object> attributes = [vmimage: vmImage]
      new DefaultCacheData(Keys.getCustomVMImageKey(azureCloudProvider, accountName, vmImage.region, vmImage.name),
        attributes,
        [:])
    })


    log.info("Caching ${data.size()} items in ${agentType}")
    new DefaultCacheResult([(Keys.Namespace.AZURE_CUSTOMVMIMAGES.ns): data])
  }
}
