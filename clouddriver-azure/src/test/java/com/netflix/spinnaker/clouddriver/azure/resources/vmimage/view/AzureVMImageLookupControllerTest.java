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

package com.netflix.spinnaker.clouddriver.azure.resources.vmimage.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.CacheFilter;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.clouddriver.azure.AzureCloudProvider;
import com.netflix.spinnaker.clouddriver.azure.resources.common.cache.Keys;
import com.netflix.spinnaker.clouddriver.azure.resources.vmimage.model.AzureCustomVMImage;
import com.netflix.spinnaker.clouddriver.azure.resources.vmimage.model.AzureManagedVMImage;
import com.netflix.spinnaker.clouddriver.azure.resources.vmimage.model.AzureNamedImage;
import com.netflix.spinnaker.clouddriver.azure.resources.vmimage.model.AzureVMImage;
import com.netflix.spinnaker.clouddriver.azure.resources.vmimage.view.AzureVMImageLookupController.LookupOptions;
import com.netflix.spinnaker.clouddriver.security.DefaultAccountCredentialsProvider;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AzureVMImageLookupControllerTest {

  public static final String REGION = "eastus";
  public static final String VM_IMAGE_NAME = "imagename";
  public static final String OS_TYPE = "windows";
  public static final String AZURE_ACCOUNT = "azure";
  public static final String RESOURCE_GROUP = "testgroup";
  public static final String NOT_AVAILABLE = "na";
  public static final String CUSTOM_IMAGE_PATH = "path/to/image";
  public static final String OFFER = "offer";
  public static final String SKU = "sku";
  public static final String PUBLISHER = "publisher";
  public static final String VERSION = "1";
  private Cache cache;
  private DefaultAccountCredentialsProvider accountCredentialsProvider;
  private static final AzureCloudProvider azureCloudProvider = new AzureCloudProvider();

  private static final ObjectMapper objectMapper = new ObjectMapper();
  private AzureVMImageLookupController lookupController;

  @BeforeEach
  public void setUp() {
    this.cache = mock(Cache.class);
    this.accountCredentialsProvider = mock(DefaultAccountCredentialsProvider.class);
    lookupController =
        new AzureVMImageLookupController(
            this.accountCredentialsProvider, azureCloudProvider, this.cache, objectMapper);
  }

  @AfterEach
  public void tearDown() {
    cache = null;
    lookupController = null;
  }

  @Test
  @DisplayName("Should throw exception when no image type found")
  void shouldThrowExceptionWhenNoImageFound() {

    // prepare
    given(cache.filterIdentifiers(eq(Keys.Namespace.AZURE_CUSTOMVMIMAGES.getNs()), anyString()))
        .willReturn(List.of());
    given(cache.filterIdentifiers(eq(Keys.Namespace.AZURE_MANAGEDIMAGES.getNs()), anyString()))
        .willReturn(List.of());
    given(cache.filterIdentifiers(eq(Keys.Namespace.AZURE_VMIMAGES.getNs()), anyString()))
        .willReturn(List.of());
    given(accountCredentialsProvider.getAll()).willReturn(Set.of());
    given(
            cache.getAll(
                eq(Keys.Namespace.AZURE_CUSTOMVMIMAGES.getNs()), anyList(), any(CacheFilter.class)))
        .willReturn(List.of());
    given(
            cache.getAll(
                eq(Keys.Namespace.AZURE_MANAGEDIMAGES.getNs()), anyList(), any(CacheFilter.class)))
        .willReturn(List.of());
    given(
            cache.getAll(
                eq(Keys.Namespace.AZURE_VMIMAGES.getNs()), anyList(), any(CacheFilter.class)))
        .willReturn(List.of());

    // act and assert
    assertThatExceptionOfType(AzureVMImageLookupController.ImageNotFoundException.class)
        .isThrownBy(() -> lookupController.getVMImage(AZURE_ACCOUNT, REGION, VM_IMAGE_NAME))
        .withMessage(VM_IMAGE_NAME + " not found in " + AZURE_ACCOUNT + "/" + REGION);
  }

  @Test
  @DisplayName(
      "When custom flag is set to true and managed imaged to false it should only return VM custom images")
  void shouldReturnCustomImage() {

    // prepare
    LookupOptions lookupOptions = getLookupOptions(false, true, false);

    String key = Keys.getCustomVMImageKey(azureCloudProvider, AZURE_ACCOUNT, REGION, VM_IMAGE_NAME);

    Map<String, Object> azureImageAsJson = getVmCustomImageAsJsonMap(VM_IMAGE_NAME);

    CacheData c = new DefaultCacheData(key, Map.of("vmimage", azureImageAsJson), Map.of());

    given(cache.filterIdentifiers(eq(Keys.Namespace.AZURE_CUSTOMVMIMAGES.getNs()), anyString()))
        .willReturn(List.of(key));
    given(
            cache.getAll(
                eq(Keys.Namespace.AZURE_CUSTOMVMIMAGES.getNs()), anyList(), any(CacheFilter.class)))
        .willReturn(List.of(c));

    // act
    List<AzureNamedImage> list = lookupController.list(lookupOptions);

    // assert
    ArgumentCaptor<String> namespace = ArgumentCaptor.forClass(String.class);
    assertThat(list).isNotEmpty().hasSize(1);
    AzureNamedImage namedImage = list.get(0);
    assertThat(namedImage)
        .isNotNull()
        .returns(VM_IMAGE_NAME, AzureNamedImage::getImageName)
        .returns(REGION, AzureNamedImage::getRegion)
        .returns(AZURE_ACCOUNT, AzureNamedImage::getAccount)
        .returns(OS_TYPE, AzureNamedImage::getOstype)
        .returns(true, AzureNamedImage::getIsCustom)
        .returns(NOT_AVAILABLE, AzureNamedImage::getOffer)
        .returns(NOT_AVAILABLE, AzureNamedImage::getSku)
        .returns(NOT_AVAILABLE, AzureNamedImage::getVersion)
        .returns(CUSTOM_IMAGE_PATH, AzureNamedImage::getUri);

    verify(cache, times(1)).getAll(namespace.capture(), anyList(), any(CacheFilter.class));
    List<String> keyNamespace = namespace.getAllValues();
    assertThat(keyNamespace).containsOnly(Keys.Namespace.AZURE_CUSTOMVMIMAGES.getNs());
  }

  @Test
  @DisplayName("Should Return only custom images if found in the cache")
  void shouldReturnOnlyCustomImageWhenSearchedByAccountRegionAndName() {

    // prepare

    String key = Keys.getCustomVMImageKey(azureCloudProvider, AZURE_ACCOUNT, REGION, VM_IMAGE_NAME);

    Map<String, Object> azureImageAsJson = getVmCustomImageAsJsonMap(VM_IMAGE_NAME);

    CacheData c = new DefaultCacheData(key, Map.of("vmimage", azureImageAsJson), Map.of());

    given(cache.filterIdentifiers(eq(Keys.Namespace.AZURE_CUSTOMVMIMAGES.getNs()), anyString()))
        .willReturn(List.of(key));
    given(
            cache.getAll(
                eq(Keys.Namespace.AZURE_CUSTOMVMIMAGES.getNs()), anyList(), any(CacheFilter.class)))
        .willReturn(List.of(c));

    // act
    List<AzureNamedImage> list = lookupController.getVMImage(AZURE_ACCOUNT, REGION, VM_IMAGE_NAME);

    // assert
    ArgumentCaptor<String> namespace = ArgumentCaptor.forClass(String.class);
    assertThat(list).isNotEmpty().hasSize(1);
    AzureNamedImage namedImage = list.get(0);
    assertThat(namedImage)
        .isNotNull()
        .returns(VM_IMAGE_NAME, AzureNamedImage::getImageName)
        .returns(REGION, AzureNamedImage::getRegion)
        .returns(AZURE_ACCOUNT, AzureNamedImage::getAccount)
        .returns(OS_TYPE, AzureNamedImage::getOstype)
        .returns(true, AzureNamedImage::getIsCustom)
        .returns(NOT_AVAILABLE, AzureNamedImage::getOffer)
        .returns(NOT_AVAILABLE, AzureNamedImage::getSku)
        .returns(NOT_AVAILABLE, AzureNamedImage::getVersion)
        .returns(CUSTOM_IMAGE_PATH, AzureNamedImage::getUri);

    verify(cache, times(1)).filterIdentifiers(anyString(), anyString());
    verify(cache, times(1)).getAll(namespace.capture(), anyList(), any(CacheFilter.class));
    List<String> keyNamespace = namespace.getAllValues();
    assertThat(keyNamespace).containsOnly(Keys.Namespace.AZURE_CUSTOMVMIMAGES.getNs());
  }

  @Test
  @DisplayName(
      "When custom flag is set to false and managed imaged to false it should return VM custom images and images from yaml file if any defined")
  void shouldReturnCustomImagesAndImagesConfiguredInYaml() {

    // prepare
    LookupOptions lookupOptions = getLookupOptions(false, false, true);

    String key = Keys.getCustomVMImageKey(azureCloudProvider, AZURE_ACCOUNT, REGION, VM_IMAGE_NAME);

    Map<String, Object> azureImageAsJson = getVmCustomImageAsJsonMap(VM_IMAGE_NAME);

    CacheData c = new DefaultCacheData(key, Map.of("vmimage", azureImageAsJson), Map.of());

    given(cache.filterIdentifiers(eq(Keys.Namespace.AZURE_CUSTOMVMIMAGES.getNs()), anyString()))
        .willReturn(List.of(key));
    given(
            cache.getAll(
                eq(Keys.Namespace.AZURE_CUSTOMVMIMAGES.getNs()), anyList(), any(CacheFilter.class)))
        .willReturn(List.of(c));

    given(accountCredentialsProvider.getAll()).willReturn(Set.of());
    // act
    List<AzureNamedImage> list = lookupController.list(lookupOptions);

    // assert
    ArgumentCaptor<String> namespace = ArgumentCaptor.forClass(String.class);
    assertThat(list).isNotEmpty().hasSize(1);
    AzureNamedImage namedImage = list.get(0);
    assertThat(namedImage)
        .isNotNull()
        .returns(VM_IMAGE_NAME, AzureNamedImage::getImageName)
        .returns(REGION, AzureNamedImage::getRegion)
        .returns(AZURE_ACCOUNT, AzureNamedImage::getAccount)
        .returns(OS_TYPE, AzureNamedImage::getOstype)
        .returns(true, AzureNamedImage::getIsCustom)
        .returns(NOT_AVAILABLE, AzureNamedImage::getOffer)
        .returns(NOT_AVAILABLE, AzureNamedImage::getSku)
        .returns(NOT_AVAILABLE, AzureNamedImage::getVersion)
        .returns(CUSTOM_IMAGE_PATH, AzureNamedImage::getUri);

    verify(this.accountCredentialsProvider, times(1)).getAll();
    verify(this.cache, times(1)).getAll(namespace.capture(), anyList(), any(CacheFilter.class));
    List<String> keyNamespace = namespace.getAllValues();
    assertThat(keyNamespace).containsOnly(Keys.Namespace.AZURE_CUSTOMVMIMAGES.getNs());
  }

  @Test
  @DisplayName(
      "When custom flag is set to false and managed imaged to false it should return VM custom images, images from yaml file if any defined and Azure VM images if matched the filter")
  void shouldReturnCustomImagesAndImagesConfiguredInYamlAndAzureVMImages() {

    // prepare
    LookupOptions lookupOptions = getLookupOptions(false, false, false);
    lookupOptions.setQ(OFFER + "-" + SKU);

    String key = Keys.getCustomVMImageKey(azureCloudProvider, AZURE_ACCOUNT, REGION, VM_IMAGE_NAME);

    String vmImageKey =
        Keys.getVMImageKey(
            azureCloudProvider,
            AZURE_ACCOUNT,
            REGION,
            OFFER + "-" + SKU,
            VERSION + "-" + PUBLISHER);

    Map<String, Object> azureCustomImageAsJson = getVmCustomImageAsJsonMap(OFFER + "-" + SKU);

    Map<String, Object> azureVmImageAsJson = getAzureVMImageAsJsonMap();

    CacheData customImage =
        new DefaultCacheData(key, Map.of("vmimage", azureCustomImageAsJson), Map.of());
    CacheData vmImage =
        new DefaultCacheData(vmImageKey, Map.of("vmimage", azureVmImageAsJson), Map.of());

    given(cache.filterIdentifiers(eq(Keys.Namespace.AZURE_CUSTOMVMIMAGES.getNs()), anyString()))
        .willReturn(List.of(key));
    given(cache.filterIdentifiers(eq(Keys.Namespace.AZURE_VMIMAGES.getNs()), anyString()))
        .willReturn(List.of(vmImageKey));
    given(
            cache.getAll(
                eq(Keys.Namespace.AZURE_CUSTOMVMIMAGES.getNs()), anyList(), any(CacheFilter.class)))
        .willReturn(List.of(customImage));
    given(
            cache.getAll(
                eq(Keys.Namespace.AZURE_VMIMAGES.getNs()), anyList(), any(CacheFilter.class)))
        .willReturn(List.of(vmImage));

    given(accountCredentialsProvider.getAll()).willReturn(Set.of());
    // act
    List<AzureNamedImage> list = lookupController.list(lookupOptions);

    // assert
    ArgumentCaptor<String> namespace = ArgumentCaptor.forClass(String.class);
    assertThat(list).isNotEmpty().hasSize(2);
    AzureNamedImage namedImage = list.get(0);
    assertThat(namedImage)
        .isNotNull()
        .returns(OFFER + "-" + SKU, AzureNamedImage::getImageName)
        .returns(REGION, AzureNamedImage::getRegion)
        .returns(AZURE_ACCOUNT, AzureNamedImage::getAccount)
        .returns(OS_TYPE, AzureNamedImage::getOstype)
        .returns(true, AzureNamedImage::getIsCustom)
        .returns(NOT_AVAILABLE, AzureNamedImage::getOffer)
        .returns(NOT_AVAILABLE, AzureNamedImage::getSku)
        .returns(NOT_AVAILABLE, AzureNamedImage::getVersion)
        .returns(CUSTOM_IMAGE_PATH, AzureNamedImage::getUri);

    AzureNamedImage resultVmImage = list.get(1);
    assertThat(resultVmImage)
        .isNotNull()
        .returns(
            OFFER + "-" + SKU + "(" + PUBLISHER + "_" + VERSION + ")",
            AzureNamedImage::getImageName)
        .returns(REGION, AzureNamedImage::getRegion)
        .returns(AZURE_ACCOUNT, AzureNamedImage::getAccount)
        .returns(NOT_AVAILABLE, AzureNamedImage::getOstype)
        .returns(false, AzureNamedImage::getIsCustom)
        .returns(OFFER, AzureNamedImage::getOffer)
        .returns(SKU, AzureNamedImage::getSku)
        .returns(VERSION, AzureNamedImage::getVersion)
        .returns(NOT_AVAILABLE, AzureNamedImage::getUri);

    verify(this.accountCredentialsProvider, times(1)).getAll();
    verify(this.cache, times(2)).filterIdentifiers(anyString(), anyString());
    verify(this.cache, times(2)).getAll(namespace.capture(), anyList(), any(CacheFilter.class));
    List<String> keyNamespace = namespace.getAllValues();
    assertThat(keyNamespace)
        .containsOnly(
            Keys.Namespace.AZURE_CUSTOMVMIMAGES.getNs(), Keys.Namespace.AZURE_VMIMAGES.getNs());
  }

  @Test
  @DisplayName(
      "Should return only AzureVMImages when searched by account, region and name AND no custom/managed/yaml images found")
  void shouldReturnOnlyAzureVMImages() {

    // prepare

    String vmImageKey =
        Keys.getVMImageKey(
            azureCloudProvider,
            AZURE_ACCOUNT,
            REGION,
            OFFER + "-" + SKU,
            VERSION + "-" + PUBLISHER);

    Map<String, Object> azureVmImageAsJson = getAzureVMImageAsJsonMap();

    CacheData vmImage =
        new DefaultCacheData(vmImageKey, Map.of("vmimage", azureVmImageAsJson), Map.of());

    given(cache.filterIdentifiers(eq(Keys.Namespace.AZURE_CUSTOMVMIMAGES.getNs()), anyString()))
        .willReturn(List.of());
    given(cache.filterIdentifiers(eq(Keys.Namespace.AZURE_MANAGEDIMAGES.getNs()), anyString()))
        .willReturn(List.of());
    given(cache.filterIdentifiers(eq(Keys.Namespace.AZURE_VMIMAGES.getNs()), anyString()))
        .willReturn(List.of(vmImageKey));
    given(accountCredentialsProvider.getAll()).willReturn(Set.of());
    given(
            cache.getAll(
                eq(Keys.Namespace.AZURE_CUSTOMVMIMAGES.getNs()), anyList(), any(CacheFilter.class)))
        .willReturn(List.of());
    given(
            cache.getAll(
                eq(Keys.Namespace.AZURE_MANAGEDIMAGES.getNs()), anyList(), any(CacheFilter.class)))
        .willReturn(List.of());
    given(
            cache.getAll(
                eq(Keys.Namespace.AZURE_VMIMAGES.getNs()), anyList(), any(CacheFilter.class)))
        .willReturn(List.of(vmImage));

    given(accountCredentialsProvider.getAll()).willReturn(Set.of());
    // act
    List<AzureNamedImage> list =
        lookupController.getVMImage(AZURE_ACCOUNT, REGION, OFFER + "-" + SKU);

    // assert
    ArgumentCaptor<String> namespace = ArgumentCaptor.forClass(String.class);
    assertThat(list).isNotEmpty().hasSize(1);

    AzureNamedImage resultVmImage = list.get(0);
    assertThat(resultVmImage)
        .isNotNull()
        .returns(
            OFFER + "-" + SKU + "(" + PUBLISHER + "_" + VERSION + ")",
            AzureNamedImage::getImageName)
        .returns(REGION, AzureNamedImage::getRegion)
        .returns(AZURE_ACCOUNT, AzureNamedImage::getAccount)
        .returns(NOT_AVAILABLE, AzureNamedImage::getOstype)
        .returns(false, AzureNamedImage::getIsCustom)
        .returns(OFFER, AzureNamedImage::getOffer)
        .returns(SKU, AzureNamedImage::getSku)
        .returns(VERSION, AzureNamedImage::getVersion)
        .returns(NOT_AVAILABLE, AzureNamedImage::getUri);

    verify(this.accountCredentialsProvider, times(1)).getAll();
    verify(this.cache, times(3)).filterIdentifiers(anyString(), anyString());
    verify(this.cache, times(3)).getAll(namespace.capture(), anyList(), any(CacheFilter.class));
    List<String> keyNamespace = namespace.getAllValues();
    assertThat(keyNamespace)
        .containsOnly(
            Keys.Namespace.AZURE_CUSTOMVMIMAGES.getNs(),
            Keys.Namespace.AZURE_VMIMAGES.getNs(),
            Keys.Namespace.AZURE_MANAGEDIMAGES.getNs());
  }

  @Test
  @DisplayName(
      "When managed image flag is set to true should return the AzureManagedImage if it is available in the cache")
  void shouldReturnManagedImage() {

    // prepare
    LookupOptions lookupOptions = getLookupOptions(true, true, false);

    String key =
        Keys.getManagedVMImageKey(
            azureCloudProvider, AZURE_ACCOUNT, REGION, RESOURCE_GROUP, VM_IMAGE_NAME, OS_TYPE);

    Map<String, Object> objectAsMap = getManagedImageAsJsonMap();

    CacheData c = new DefaultCacheData(key, Map.of("vmimage", objectAsMap), Map.of());

    given(cache.filterIdentifiers(eq(Keys.Namespace.AZURE_CUSTOMVMIMAGES.getNs()), anyString()))
        .willReturn(List.of());
    given(cache.filterIdentifiers(eq(Keys.Namespace.AZURE_MANAGEDIMAGES.getNs()), anyString()))
        .willReturn(List.of(key));
    given(
            cache.getAll(
                eq(Keys.Namespace.AZURE_CUSTOMVMIMAGES.getNs()), anyList(), any(CacheFilter.class)))
        .willReturn(List.of());
    given(
            cache.getAll(
                eq(Keys.Namespace.AZURE_MANAGEDIMAGES.getNs()), anyList(), any(CacheFilter.class)))
        .willReturn(List.of(c));

    // act
    List<AzureNamedImage> list = lookupController.list(lookupOptions);

    // assert
    ArgumentCaptor<String> namespace = ArgumentCaptor.forClass(String.class);
    assertThat(list).isNotEmpty().hasSize(1);
    AzureNamedImage namedImage = list.get(0);
    assertThat(namedImage)
        .isNotNull()
        .returns(VM_IMAGE_NAME, AzureNamedImage::getImageName)
        .returns(REGION, AzureNamedImage::getRegion)
        .returns(AZURE_ACCOUNT, AzureNamedImage::getAccount)
        .returns(OS_TYPE, AzureNamedImage::getOstype)
        .returns(true, AzureNamedImage::getIsCustom)
        .returns(NOT_AVAILABLE, AzureNamedImage::getOffer)
        .returns(NOT_AVAILABLE, AzureNamedImage::getSku)
        .returns(NOT_AVAILABLE, AzureNamedImage::getVersion)
        .returns(NOT_AVAILABLE, AzureNamedImage::getUri);

    verify(cache, times(2)).getAll(namespace.capture(), anyList(), any(CacheFilter.class));
    List<String> keyNamespace = namespace.getAllValues();
    assertThat(keyNamespace)
        .containsOnly(
            Keys.Namespace.AZURE_CUSTOMVMIMAGES.getNs(),
            Keys.Namespace.AZURE_MANAGEDIMAGES.getNs());
  }

  @Test
  @DisplayName(
      "Should return managed images when searched by account, region and name AND no custom image and no yaml image configured")
  void shouldReturnOnlyManagedImageWhenNoCustomAndYamlImagesConfigured() {

    // prepare

    String key =
        Keys.getManagedVMImageKey(
            azureCloudProvider, AZURE_ACCOUNT, REGION, RESOURCE_GROUP, VM_IMAGE_NAME, OS_TYPE);

    Map<String, Object> objectAsMap = getManagedImageAsJsonMap();

    CacheData c = new DefaultCacheData(key, Map.of("vmimage", objectAsMap), Map.of());

    given(cache.filterIdentifiers(eq(Keys.Namespace.AZURE_CUSTOMVMIMAGES.getNs()), anyString()))
        .willReturn(List.of());
    given(cache.filterIdentifiers(eq(Keys.Namespace.AZURE_MANAGEDIMAGES.getNs()), anyString()))
        .willReturn(List.of(key));
    given(accountCredentialsProvider.getAll()).willReturn(Set.of());
    given(
            cache.getAll(
                eq(Keys.Namespace.AZURE_CUSTOMVMIMAGES.getNs()), anyList(), any(CacheFilter.class)))
        .willReturn(List.of());
    given(
            cache.getAll(
                eq(Keys.Namespace.AZURE_MANAGEDIMAGES.getNs()), anyList(), any(CacheFilter.class)))
        .willReturn(List.of(c));

    // act
    List<AzureNamedImage> list = lookupController.getVMImage(AZURE_ACCOUNT, REGION, VM_IMAGE_NAME);

    // assert
    ArgumentCaptor<String> namespace = ArgumentCaptor.forClass(String.class);
    assertThat(list).isNotEmpty().hasSize(1);
    AzureNamedImage namedImage = list.get(0);
    assertThat(namedImage)
        .isNotNull()
        .returns(VM_IMAGE_NAME, AzureNamedImage::getImageName)
        .returns(REGION, AzureNamedImage::getRegion)
        .returns(AZURE_ACCOUNT, AzureNamedImage::getAccount)
        .returns(OS_TYPE, AzureNamedImage::getOstype)
        .returns(true, AzureNamedImage::getIsCustom)
        .returns(NOT_AVAILABLE, AzureNamedImage::getOffer)
        .returns(NOT_AVAILABLE, AzureNamedImage::getSku)
        .returns(NOT_AVAILABLE, AzureNamedImage::getVersion)
        .returns(NOT_AVAILABLE, AzureNamedImage::getUri);

    verify(cache, times(2)).filterIdentifiers(anyString(), anyString());
    verify(cache, times(2)).getAll(namespace.capture(), anyList(), any(CacheFilter.class));
    List<String> keyNamespace = namespace.getAllValues();
    assertThat(keyNamespace)
        .containsOnly(
            Keys.Namespace.AZURE_CUSTOMVMIMAGES.getNs(),
            Keys.Namespace.AZURE_MANAGEDIMAGES.getNs());
  }

  private static Map<String, Object> getManagedImageAsJsonMap() {
    var namedImage = new AzureManagedVMImage();
    namedImage.setResourceGroup(RESOURCE_GROUP);
    namedImage.setRegion(REGION);
    namedImage.setOsType(OS_TYPE);
    namedImage.setName(VM_IMAGE_NAME);
    return objectMapper.convertValue(namedImage, new TypeReference<>() {});
  }

  private static Map<String, Object> getVmCustomImageAsJsonMap(String name) {
    var namedImage = new AzureCustomVMImage();
    namedImage.setUri(CUSTOM_IMAGE_PATH);
    namedImage.setRegion(REGION);
    namedImage.setOsType(OS_TYPE);
    namedImage.setName(name);
    return objectMapper.convertValue(namedImage, new TypeReference<>() {});
  }

  private static Map<String, Object> getAzureVMImageAsJsonMap() {
    return objectMapper.convertValue(getAzureVmImage(), new TypeReference<>() {});
  }

  @NotNull
  private static LookupOptions getLookupOptions(
      boolean managedImage, boolean customOnly, boolean configOnly) {
    var lookupOptions = new LookupOptions();
    lookupOptions.setAccount(AZURE_ACCOUNT);
    lookupOptions.setRegion(REGION);
    lookupOptions.setManagedImages(managedImage);
    lookupOptions.setCustomOnly(customOnly);
    lookupOptions.setConfigOnly(configOnly);
    return lookupOptions;
  }

  private static AzureVMImage getAzureVmImage() {
    AzureVMImage azureVMImage = new AzureVMImage();
    azureVMImage.setOffer(OFFER);
    azureVMImage.setSku(SKU);
    azureVMImage.setPublisher(PUBLISHER);
    azureVMImage.setVersion(VERSION);
    return azureVMImage;
  }
}
