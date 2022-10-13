/*
 * Copyright 2022 Armory, Inc.
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
 */

package com.netflix.spinnaker.clouddriver.azure.resources.vmimage.cache;

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.agent.*;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.azure.AzureCloudProvider;
import com.netflix.spinnaker.clouddriver.azure.resources.common.cache.Keys;
import com.netflix.spinnaker.clouddriver.azure.resources.common.cache.provider.AzureInfrastructureProvider;
import com.netflix.spinnaker.clouddriver.azure.resources.vmimage.model.AzureManagedVMImage;
import com.netflix.spinnaker.clouddriver.azure.security.AzureCredentials;
import com.netflix.spinnaker.clouddriver.cache.CustomScheduledAgent;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AzureManagedImageCachingAgent
    implements CachingAgent, CustomScheduledAgent, AccountAware {
  private final Logger log = LoggerFactory.getLogger(getClass());
  private static final long DEFAULT_POLL_INTERVAL_MILLIS = TimeUnit.HOURS.toMillis(2);
  private static final long DEFAULT_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(30);

  private final AzureCloudProvider azureCloudProvider;
  private final String accountName;
  private final AzureCredentials creds;
  private final String region;
  private final ObjectMapper objectMapper;

  private final long pollIntervalMillis;
  private final long timeoutMillis;

  private static final java.util.Set<AgentDataType> types =
      Set.of(AUTHORITATIVE.forType(Keys.Namespace.AZURE_MANAGEDIMAGES.toString()));

  public AzureManagedImageCachingAgent(
      AzureCloudProvider azureCloudProvider,
      String accountName,
      AzureCredentials creds,
      String region,
      ObjectMapper objectMapper) {
    this(
        azureCloudProvider,
        accountName,
        creds,
        region,
        objectMapper,
        DEFAULT_POLL_INTERVAL_MILLIS,
        DEFAULT_TIMEOUT_MILLIS);
  }

  AzureManagedImageCachingAgent(
      AzureCloudProvider azureCloudProvider,
      String accountName,
      AzureCredentials creds,
      String region,
      ObjectMapper objectMapper,
      long pollIntervalMillis,
      long timeoutMillis) {
    this.azureCloudProvider = azureCloudProvider;
    this.accountName = accountName;
    this.creds = creds;
    this.region = region;
    this.objectMapper = objectMapper;
    this.pollIntervalMillis = pollIntervalMillis;
    this.timeoutMillis = timeoutMillis;
  }

  @Override
  public String getProviderName() {
    return AzureInfrastructureProvider.PROVIDER_NAME;
  }

  @Override
  public String getAgentType() {
    return new StringJoiner("/")
        .add(accountName)
        .add(creds.getDefaultResourceGroup())
        .add(region)
        .add(this.getClass().getSimpleName())
        .toString();
  }

  @Override
  public String getAccountName() {
    return accountName;
  }

  @Override
  public Collection<AgentDataType> getProvidedDataTypes() {
    return types;
  }

  @Override
  public CacheResult loadData(ProviderCache providerCache) {
    log.info("Describing items in {}", getAgentType());

    List<AzureManagedVMImage> vmImages =
        creds.getComputeClient().getAllVMCustomImages(creds.getDefaultResourceGroup(), region);
    TypeReference<HashMap<String, Object>> typeRef = new TypeReference<>() {};

    List<CacheData> data =
        vmImages.stream()
            .map(
                vmImage -> {
                  Map<String, Object> attributes = objectMapper.convertValue(vmImage, typeRef);
                  return new DefaultCacheData(
                      Keys.getManagedVMImageKey(
                          azureCloudProvider,
                          accountName,
                          vmImage.getRegion(),
                          vmImage.getResourceGroup(),
                          vmImage.getName(),
                          vmImage.getOsType()),
                      attributes,
                      Map.of());
                })
            .collect(Collectors.toList());

    log.info("Caching {} items in {}", data.size(), getAgentType());

    return new DefaultCacheResult(Map.of(Keys.Namespace.AZURE_MANAGEDIMAGES.toString(), data));
  }

  @Override
  public long getPollIntervalMillis() {
    return this.pollIntervalMillis;
  }

  @Override
  public long getTimeoutMillis() {
    return this.timeoutMillis;
  }
}
