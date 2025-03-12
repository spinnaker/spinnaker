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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import com.netflix.spinnaker.clouddriver.azure.AzureCloudProvider;
import com.netflix.spinnaker.clouddriver.azure.client.AzureComputeClient;
import com.netflix.spinnaker.clouddriver.azure.resources.common.cache.Keys;
import com.netflix.spinnaker.clouddriver.azure.resources.vmimage.model.AzureManagedVMImage;
import com.netflix.spinnaker.clouddriver.azure.security.AzureCredentials;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AzureManagedImageCachingAgentTest {

  private static final String NAME = "name";
  private static final String RESOURCEGROUP = "resourcegroup";
  private static final String REGION = "eastus";
  private static final String OS_TYPE = "Windows";
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  @DisplayName(
      "Cache for Namespace.AZURE_MANAGEDIMAGES should be empty when no custom VMs returned by Azure Compute Client")
  void shouldNotCacheWhenNoImageReturnedByAzureComputeClient() {
    AzureCredentials azureCredentials = mock(AzureCredentials.class);
    when(azureCredentials.getDefaultResourceGroup()).thenReturn("resource");
    AzureComputeClient azureComputeClient = mock(AzureComputeClient.class);
    when(azureCredentials.getComputeClient()).thenReturn(azureComputeClient);
    when(azureComputeClient.getAllVMCustomImages(anyString(), anyString())).thenReturn(List.of());
    AzureManagedImageCachingAgent region =
        new AzureManagedImageCachingAgent(
            new AzureCloudProvider(), "my-account", azureCredentials, REGION, MAPPER);
    CacheResult cacheResult = region.loadData(mock(ProviderCache.class));

    assertThat(cacheResult).isNotNull();
    Map<String, Collection<CacheData>> cacheResults = cacheResult.getCacheResults();
    assertThat(cacheResults)
        .isNotEmpty()
        .containsKey(Keys.Namespace.AZURE_MANAGEDIMAGES.toString());
    assertThat(cacheResults.get(Keys.Namespace.AZURE_MANAGEDIMAGES.toString())).isEmpty();
  }

  @Test
  @DisplayName("Cache for Namespace.AZURE_MANAGEDIMAGES should return one result")
  void shouldReturnOneCachedResult() {

    AzureCredentials azureCredentials = mock(AzureCredentials.class);
    when(azureCredentials.getDefaultResourceGroup()).thenReturn("resource");
    AzureComputeClient azureComputeClient = mock(AzureComputeClient.class);
    when(azureCredentials.getComputeClient()).thenReturn(azureComputeClient);

    AzureManagedVMImage managedVMImage = new AzureManagedVMImage();

    managedVMImage.setName(NAME);
    managedVMImage.setResourceGroup(RESOURCEGROUP);
    managedVMImage.setRegion(REGION);
    managedVMImage.setOsType(OS_TYPE);

    when(azureComputeClient.getAllVMCustomImages(anyString(), anyString()))
        .thenReturn(List.of(managedVMImage));
    AzureManagedImageCachingAgent region =
        new AzureManagedImageCachingAgent(
            new AzureCloudProvider(), "my-account", azureCredentials, REGION, MAPPER);
    CacheResult cacheResult = region.loadData(mock(ProviderCache.class));

    assertThat(cacheResult).isNotNull();
    Map<String, Collection<CacheData>> cacheResults = cacheResult.getCacheResults();
    assertThat(cacheResults)
        .isNotEmpty()
        .containsKey(Keys.Namespace.AZURE_MANAGEDIMAGES.toString());
    Collection<CacheData> cacheData =
        cacheResults.get((Keys.Namespace.AZURE_MANAGEDIMAGES.toString()));
    assertThat(cacheData).hasSize(1);
    cacheData.forEach(
        data -> {
          AzureManagedVMImage cachedManagedImage =
              MAPPER.convertValue(data.getAttributes().get("vmimage"), AzureManagedVMImage.class);
          assertManagedImage(managedVMImage, cachedManagedImage);
          assertThat(data.getRelationships()).isEmpty();
        });
  }

  private static void assertManagedImage(
      AzureManagedVMImage actualManagedVMImage, AzureManagedVMImage cachedManagedImage) {
    assertThat(cachedManagedImage.getName()).isEqualTo(actualManagedVMImage.getName());
    assertThat(cachedManagedImage.getRegion()).isEqualTo(actualManagedVMImage.getRegion());
    assertThat(cachedManagedImage.getOsType()).isEqualTo(actualManagedVMImage.getOsType());
    assertThat(cachedManagedImage.getResourceGroup())
        .isEqualTo(actualManagedVMImage.getResourceGroup());
  }
}
