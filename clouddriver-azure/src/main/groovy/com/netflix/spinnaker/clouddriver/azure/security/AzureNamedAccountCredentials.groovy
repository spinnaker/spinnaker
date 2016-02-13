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
import com.netflix.spinnaker.clouddriver.azure.client.AzureNetworkClient
import com.netflix.spinnaker.clouddriver.azure.client.AzureResourceManagerClient
import com.netflix.spinnaker.clouddriver.azure.resources.vmimage.model.AzureVMImage
import com.netflix.spinnaker.clouddriver.security.AccountCredentials
import groovy.transform.CompileStatic

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
  final String applicationName
  final List<String> requiredGroupMembership
  final AzureCredentials credentials


  AzureNamedAccountCredentials(String accountName,
                               String environment,
                               String accountType,
                               String clientId,
                               String appKey,
                               String tenantId,
                               String subscriptionId,
                               List<String> regions,
                               List<AzureVMImage> vmImages,
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
    this.vmImages = vmImages ?: [] as List<AzureVMImage>
    this.applicationName = applicationName
    this.requiredGroupMembership = requiredGroupMembership ?: [] as List<String>
    this.credentials = appKey.isEmpty() ? null : buildCredentials()
  }

  @Override
  public String getCloudProvider() {
    return CLOUD_PROVIDER
  }

  @Override
  String getName() {
    return accountName
  }

  @Override
  public String getProvider() {
    return getCloudProvider()
  }

  private AzureCredentials buildCredentials() {
    AzureResourceManagerClient rmClient = new AzureResourceManagerClient(this.subscriptionId)
    AzureNetworkClient networkClient = new AzureNetworkClient(this.subscriptionId)
    AzureComputeClient computeClient = new AzureComputeClient(this.subscriptionId)

    return new AzureCredentials(this.tenantId, this.clientId, this.appKey, rmClient, networkClient, computeClient)
  }

  private static List<AzureRegion> buildRegions(List<String> regions) {
    return regions?.collect {new AzureRegion(it)}
  }

  public static class AzureRegion {
    public final String name

    public AzureRegion(String name) {
      if (name == null) {
        throw new NullPointerException("name");
      }
      this.name = name
    }

    public String getName() {return name}

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      AzureRegion awsRegion = (AzureRegion) o;

      return name.equals(awsRegion.name)
    }

    @Override
    public int hashCode() {
      return name.hashCode();
    }
  }

}
