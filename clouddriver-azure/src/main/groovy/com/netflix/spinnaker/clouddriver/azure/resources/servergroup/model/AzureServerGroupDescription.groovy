/*
 * Copyright 2016 The original authors.
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

package com.netflix.spinnaker.clouddriver.azure.resources.servergroup.model

import com.microsoft.azure.management.compute.models.VirtualMachineScaleSet
import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.azure.resources.common.AzureResourceOpsDescription
import com.netflix.spinnaker.clouddriver.azure.resources.securitygroup.model.AzureSecurityGroup
import com.netflix.spinnaker.clouddriver.azure.resources.vmimage.model.AzureNamedImage

class AzureServerGroupDescription extends AzureResourceOpsDescription {
  static enum UpgradePolicy {
    Automatic, Manual
  }

  UpgradePolicy upgradePolicy
  String loadBalancerName
  AzureSecurityGroup securityGroup
  AzureNamedImage image
  AzureScaleSetSku sku
  AzureOperatingSystemConfig osConfig
  String provisioningState
  String application // TODO standardize between this and appName
  String clusterName
  String securityGroupName
  String subnetId
  List<String> storageAccountNames

  static class AzureScaleSetSku {
    String name
    String tier
    Long capacity
  }

  static class AzureOperatingSystemConfig {
    String adminUserName
    String adminPassword
    String computerNamePrefix
  }

  Integer getStorageAccountCount() {
    (sku.capacity / 20) + 1
  }

  static UpgradePolicy getPolicyFromMode(String mode) {
    mode.toLowerCase() == "Automatic".toLowerCase() ? UpgradePolicy.Automatic : UpgradePolicy.Manual
  }

  // For now, same thing as identifier
  String getClusterName() {
    clusterName ?: Names.parseName(name).cluster
  }

  String getIdentifier() {
    String.format("%s-%s-%s", application, stack, detail)
  }

  static AzureServerGroupDescription build(VirtualMachineScaleSet scaleSet) {
    def azureSG = new AzureServerGroupDescription()
    azureSG.name = scaleSet.name
    def parsedName = Names.parseName(scaleSet.name)
    // Get the values from the tags if they exist
    azureSG.tags = scaleSet.tags ? scaleSet.tags : [:]
    // favor tag settings then Frigga name parser
    azureSG.appName = scaleSet.tags?.appName ?: parsedName.app
    azureSG.stack = scaleSet.tags?.stack ?: parsedName.stack
    azureSG.detail = scaleSet.tags?.detail ?: parsedName.detail
    azureSG.application = azureSG.appName
    azureSG.clusterName = scaleSet.tags?.cluster ?: parsedName.cluster
    azureSG.securityGroupName = scaleSet.tags?.securityGroupName
    azureSG.loadBalancerName = scaleSet.tags?.loadBalancerName
    azureSG.subnetId = scaleSet.tags?.subnetId
    azureSG.image = new AzureNamedImage( isCustom:  scaleSet.tags?.customImage)
    if (!azureSG.image.isCustom) {
      // Azure server group which was created using Azure Market Store images will have a number of storage accounts
      //   that were created at the time the server group was created; these storage account should be in saved in the
      //   tags map under storrageAccountNames key as a comma separated list of strings
      azureSG.storageAccountNames = new ArrayList<String>()
      String storageNames = scaleSet.tags?.storageAccountNames

      if (storageNames) azureSG.storageAccountNames.addAll(storageNames.split(","))
    }

    azureSG.region = scaleSet.location

    azureSG.upgradePolicy = getPolicyFromMode(scaleSet.upgradePolicy.mode)

    // Get the image reference data
    def image = new AzureNamedImage()
    def imgRef = scaleSet.virtualMachineProfile?.storageProfile?.imageReference
    if (imgRef) {
      image.offer = imgRef.offer
      image.publisher = imgRef.publisher
      image.sku = imgRef.sku
      image.version = imgRef.version
    }
    azureSG.image = image

    // get the OS configuration data
    def osConfig = new AzureOperatingSystemConfig()
    def osProfile = scaleSet?.virtualMachineProfile?.osProfile
    if (osProfile) {
      osConfig.adminPassword = osProfile.adminPassword
      osConfig.adminUserName = osProfile.adminUsername
      osConfig.computerNamePrefix = osProfile.computerNamePrefix

    }
    azureSG.osConfig = osConfig

    def sku = new AzureScaleSetSku()
    def skuData = scaleSet.sku
    if (skuData) {
      sku.capacity = skuData.capacity
      sku.name = skuData.name
      sku.tier = skuData.tier
    }
    azureSG.sku = sku

    azureSG.provisioningState = scaleSet.provisioningState

    azureSG
  }


}
