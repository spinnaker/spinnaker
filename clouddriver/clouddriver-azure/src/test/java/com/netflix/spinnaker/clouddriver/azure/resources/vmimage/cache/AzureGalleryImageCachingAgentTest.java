/*
 * Copyright 2024 Moderne, Inc.
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
import com.netflix.spinnaker.clouddriver.azure.resources.vmimage.model.AzureGalleryVMImage;
import com.netflix.spinnaker.clouddriver.azure.security.AzureCredentials;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AzureGalleryImageCachingAgentTest {

  private static final String REGION = "eastus";
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  @DisplayName(
      "Cache for Namespace.AZURE_GALLERYIMAGES should be empty when no gallery images returned")
  void shouldNotCacheWhenNoGalleryImageReturnedByAzureComputeClient() {
    AzureCredentials azureCredentials = mock(AzureCredentials.class);
    when(azureCredentials.getDefaultResourceGroup()).thenReturn("resource");
    AzureComputeClient azureComputeClient = mock(AzureComputeClient.class);
    when(azureCredentials.getComputeClient()).thenReturn(azureComputeClient);
    when(azureComputeClient.getAllGalleryImages(anyString(), anyString())).thenReturn(List.of());

    AzureGalleryImageCachingAgent agent =
        new AzureGalleryImageCachingAgent(
            new AzureCloudProvider(), "my-account", azureCredentials, REGION, MAPPER);
    CacheResult cacheResult = agent.loadData(mock(ProviderCache.class));

    assertThat(cacheResult).isNotNull();
    Map<String, Collection<CacheData>> cacheResults = cacheResult.getCacheResults();
    assertThat(cacheResults)
        .isNotEmpty()
        .containsKey(Keys.Namespace.AZURE_GALLERYIMAGES.toString());
    assertThat(cacheResults.get(Keys.Namespace.AZURE_GALLERYIMAGES.toString())).isEmpty();
  }

  @Test
  @DisplayName("Cache for Namespace.AZURE_GALLERYIMAGES should return one result")
  void shouldReturnOneCachedGalleryResult() {
    AzureCredentials azureCredentials = mock(AzureCredentials.class);
    when(azureCredentials.getDefaultResourceGroup()).thenReturn("resource");
    AzureComputeClient azureComputeClient = mock(AzureComputeClient.class);
    when(azureCredentials.getComputeClient()).thenReturn(azureComputeClient);

    AzureGalleryVMImage galleryImage = new AzureGalleryVMImage();
    galleryImage.setName("my-image-def");
    galleryImage.setGalleryName("my-gallery");
    galleryImage.setImageDefinitionName("my-image-def");
    galleryImage.setVersion("1.0.0");
    galleryImage.setResourceGroup("resourcegroup");
    galleryImage.setRegion(REGION);
    galleryImage.setOsType("Linux");
    galleryImage.setResourceId(
        "/subscriptions/sub/resourceGroups/rg/providers/Microsoft.Compute/galleries/my-gallery/images/my-image-def/versions/1.0.0");

    when(azureComputeClient.getAllGalleryImages(anyString(), anyString()))
        .thenReturn(List.of(galleryImage));

    AzureGalleryImageCachingAgent agent =
        new AzureGalleryImageCachingAgent(
            new AzureCloudProvider(), "my-account", azureCredentials, REGION, MAPPER);
    CacheResult cacheResult = agent.loadData(mock(ProviderCache.class));

    assertThat(cacheResult).isNotNull();
    Map<String, Collection<CacheData>> cacheResults = cacheResult.getCacheResults();
    assertThat(cacheResults)
        .isNotEmpty()
        .containsKey(Keys.Namespace.AZURE_GALLERYIMAGES.toString());
    Collection<CacheData> cacheData =
        cacheResults.get(Keys.Namespace.AZURE_GALLERYIMAGES.toString());
    assertThat(cacheData).hasSize(1);
    cacheData.forEach(
        data -> {
          AzureGalleryVMImage cached =
              MAPPER.convertValue(data.getAttributes().get("vmimage"), AzureGalleryVMImage.class);
          assertThat(cached.getGalleryName()).isEqualTo(galleryImage.getGalleryName());
          assertThat(cached.getImageDefinitionName())
              .isEqualTo(galleryImage.getImageDefinitionName());
          assertThat(cached.getVersion()).isEqualTo(galleryImage.getVersion());
          assertThat(cached.getRegion()).isEqualTo(galleryImage.getRegion());
          assertThat(cached.getOsType()).isEqualTo(galleryImage.getOsType());
          assertThat(cached.getResourceId()).isEqualTo(galleryImage.getResourceId());
          assertThat(data.getRelationships()).isEmpty();
        });
  }
}
