/*
 * Copyright 2015 The original authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

import com.netflix.spinnaker.clouddriver.azure.client.AzureComputeClient
import com.netflix.spinnaker.clouddriver.azure.resources.vmimage.model.AzureCustomImageStorage
import com.netflix.spinnaker.clouddriver.azure.resources.vmimage.model.AzureVMImage
import com.netflix.spinnaker.clouddriver.security.AccountCredentials
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

@Slf4j
@CompileStatic
public class AzureNamedAccountCredentials implements AccountCredentials<AzureCredentials> {
  private static final String CLOUD_PROVIDER = "azure"
  final String accountName
  final String environment
  final String accountType
  private final String tenantId
  private final String subscriptionId
  private final String clientId
  private final String appKey
  final List<AzureRegion> regions
  final List<AzureVMImage> vmImages
  final List<AzureCustomImageStorage> vmCustomImages
  final String applicationName
  final List<String> requiredGroupMembership
  final AzureCredentials credentials
  final String defaultKeyVault
  final String defaultResourceGroup
  final Map<String, List<AzureComputeClient.VirtualMachineSize>> locationToInstanceTypesMap

  AzureNamedAccountCredentials(String accountName,
                               String environment,
                               String accountType,
                               String clientId,
                               String appKey,
                               String tenantId,
                               String subscriptionId,
                               List<String> regions,
                               List<AzureVMImage> vmImages,
                               List<AzureCustomImageStorage> vmCustomImages,
                               String defaultResourceGroup,
                               String defaultKeyVault,
                               String applicationName,
                               List<String> requiredGroupMembership = null) {
    this.accountName = accountName
    this.environment = environment
    this.accountType = accountType
    this.clientId = clientId
    this.appKey = appKey
    this.tenantId = tenantId
    this.subscriptionId = subscriptionId
    this.regions = buildRegions(regions)
    this.vmImages = buildPreferredVMImageList(vmImages)
    this.vmCustomImages = buildCustomImageStorages(vmCustomImages)
    this.applicationName = applicationName
    this.defaultKeyVault = defaultKeyVault
    this.defaultResourceGroup = defaultResourceGroup
    this.requiredGroupMembership = requiredGroupMembership ?: [] as List<String>
    this.credentials = appKey.isEmpty() ? null : buildCredentials()
    this.locationToInstanceTypesMap = this.credentials.computeClient.getVirtualMachineSizesByRegions(this.regions)
  }

  @Override
  public String getCloudProvider() {
    return CLOUD_PROVIDER
  }

  @Override
  String getName() {
    accountName
  }

  private AzureCredentials buildCredentials() {
    new AzureCredentials(this.tenantId, this.clientId, this.appKey, this.subscriptionId, this.defaultKeyVault, this.defaultResourceGroup, this.applicationName, this.environment)
  }

  private static List<AzureVMImage> buildPreferredVMImageList(List<AzureVMImage> vmImages) {
    def result = new ArrayList<AzureVMImage>()
    vmImages?.each { vmImage ->
      if (vmImage && vmImage.publisher && vmImage.offer && vmImage.sku && vmImage.version) {
        result += vmImage
      } else {
        log.warn("Invalid preferred VM image entry found in the config file")
      }
    }

    result
  }

  private static List<AzureCustomImageStorage> buildCustomImageStorages(List<AzureCustomImageStorage> vmCustomImages) {
    def result = new ArrayList<AzureCustomImageStorage>()
    vmCustomImages?.each { vmImage ->
      if (vmImage && vmImage.scs && vmImage.blobDir && vmImage.osType) {
        result += vmImage
      } else {
        log.warn("Invalid custom image storage entry found in the config file")
      }
    }

    result
  }

  private static List<AzureRegion> buildRegions(List<String> regions) {
    regions?.collect {new AzureRegion(it)} ?: new ArrayList<AzureRegion>()
  }

  public static class AzureRegion {
    public final String name

    public AzureRegion(String name) {
      if (name == null) {
        throw new IllegalArgumentException("name must be specified.")
      }
      this.name = name
    }

    public String getName() {return name}

    @Override
    public boolean equals(Object o) {
      if (this == o) return true
      if (o == null || getClass() != o.getClass()) return false

      AzureRegion awsRegion = (AzureRegion) o

      name.equals(awsRegion.name)
    }

    @Override
    public int hashCode() {
      name.hashCode()
    }
  }

}
