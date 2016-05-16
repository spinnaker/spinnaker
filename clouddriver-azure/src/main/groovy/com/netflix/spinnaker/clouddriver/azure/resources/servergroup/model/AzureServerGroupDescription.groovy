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
import com.netflix.spinnaker.clouddriver.model.HealthState
import com.netflix.spinnaker.clouddriver.model.Instance
import com.netflix.spinnaker.clouddriver.model.ServerGroup

class AzureServerGroupDescription extends AzureResourceOpsDescription implements ServerGroup {

  private static final AZURE_SERVER_GROUP_TYPE = "azure"

  static enum UpgradePolicy {
    Automatic, Manual
  }

  Set<AzureInstance> instances
  Set<String> loadBalancers
  Set<String> securityGroups
  Set<String> zones
  String type = AZURE_SERVER_GROUP_TYPE
  Map<String, Object> launchConfig
  ServerGroup.Capacity capacity
  ServerGroup.ImagesSummary imagesSummary
  ServerGroup.ImageSummary imageSummary

  UpgradePolicy upgradePolicy
  String loadBalancerName
  String appGatewayName
  String appGatewayBapId
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
  Boolean isDisabled // TODO add implementation to handle when a server group has been enabled/disabled
  List<AzureInboundPortConfig> inboundPortConfigs = []

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

  static class AzureInboundPortConfig {
    String name
    String protocol
    int frontEndPortRangeStart
    int frontEndPortRangeEnd
    int backendPort

  }

  Integer getStorageAccountCount() {
    (sku.capacity / 20) + 1
  }

  static UpgradePolicy getPolicyFromMode(String mode) {
    mode.toLowerCase() == "Automatic".toLowerCase() ? UpgradePolicy.Automatic : UpgradePolicy.Manual
  }

  String getClusterName() {
    clusterName ?: Names.parseName(name).cluster
  }

  String getIdentifier() {
    String.format("%s-%s-%s", application, stack, detail)
  }

  @Override
  Boolean isDisabled() {
    false
    // TODO: (scotm) should be based on existence of LB. To be added
  }

  @Override
  ServerGroup.InstanceCounts getInstanceCounts() {
    Collection<AzureInstance> instances = getInstances()
    new ServerGroup.InstanceCounts(
      total: instances?.size() ?: 0,
      up: filterInstancesByHealthState(instances, HealthState.Up)?.size() ?: 0,
      down: filterInstancesByHealthState(instances, HealthState.Down)?.size() ?: 0,
      unknown: filterInstancesByHealthState(instances, HealthState.Unknown)?.size() ?: 0,
      starting: filterInstancesByHealthState(instances, HealthState.Starting)?.size() ?: 0,
      outOfService: filterInstancesByHealthState(instances, HealthState.OutOfService)?.size() ?: 0)
  }

  @Override
  ServerGroup.Capacity getCapacity() {
    new ServerGroup.Capacity(
      min: 1,
      max: instances ? instances.size() : 1,
      desired: 1 //TODO (scotm) figure out how these should be set correctly
    )
  }

  static AzureServerGroupDescription build(VirtualMachineScaleSet scaleSet) {
    def azureSG = new AzureServerGroupDescription()
    azureSG.name = scaleSet.name
    azureSG.cloudProvider = "azure"
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
    azureSG.appGatewayName = scaleSet.tags?.appGatewayName
    azureSG.appGatewayBapId = scaleSet.tags?.appGatewayBapId
    // TODO: appGatewayBapId can be retrieved via scaleSet->networkProfile->networkInterfaceConfigurations->ipConfigurations->ApplicationGatewayBackendAddressPools
    azureSG.subnetId = scaleSet.tags?.subnetId
    azureSG.createdTime = scaleSet.tags?.createdTime?.toLong()
    azureSG.image = new AzureNamedImage( isCustom:  scaleSet.tags?.customImage, imageName: scaleSet.tags?.imageName)
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
    def imgRef = scaleSet.virtualMachineProfile?.storageProfile?.imageReference
    if (imgRef) {
      azureSG.image.offer = imgRef.offer
      azureSG.image.publisher = imgRef.publisher
      azureSG.image.sku = imgRef.sku
      azureSG.image.version = imgRef.version
    }

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

  static Collection<Instance> filterInstancesByHealthState(Set<Instance> instances, HealthState healthState) {
    instances?.findAll { Instance it -> it.getHealthState() == healthState }
  }

  void addInboundPortConfig(String name, int startRange, int endRange, String protocol, int backendPort) {
    AzureInboundPortConfig inboundConfig = new AzureInboundPortConfig(name: name)
    inboundConfig.frontEndPortRangeStart = startRange
    inboundConfig.frontEndPortRangeEnd = endRange
    inboundConfig.backendPort = backendPort
    inboundConfig.protocol = protocol
    inboundPortConfigs.add(inboundConfig)
  }

}
