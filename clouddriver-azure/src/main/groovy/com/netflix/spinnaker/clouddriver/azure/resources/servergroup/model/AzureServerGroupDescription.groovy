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

import com.google.common.collect.Sets
import com.microsoft.azure.management.compute.VirtualMachineScaleSet
import com.microsoft.azure.management.compute.implementation.VirtualMachineScaleSetInner
import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.azure.AzureCloudProvider
import com.netflix.spinnaker.clouddriver.azure.common.AzureUtilities
import com.netflix.spinnaker.clouddriver.azure.resources.common.AzureResourceOpsDescription
import com.netflix.spinnaker.clouddriver.azure.resources.vmimage.model.AzureNamedImage
import com.netflix.spinnaker.clouddriver.model.HealthState
import com.netflix.spinnaker.clouddriver.model.Instance
import com.netflix.spinnaker.clouddriver.model.ServerGroup

class AzureServerGroupDescription extends AzureResourceOpsDescription implements ServerGroup {

  static enum UpgradePolicy {
    Automatic, Manual
  }

  Set<AzureInstance> instances
  Set<String> loadBalancers
  Set<String> securityGroups
  Set<String> zones
  final String type = AzureCloudProvider.ID
  final String cloudProvider = AzureCloudProvider.ID
  Map<String, Object> launchConfig
  ServerGroup.Capacity capacity
  ServerGroup.ImagesSummary imagesSummary
  ServerGroup.ImageSummary imageSummary

  UpgradePolicy upgradePolicy
  String loadBalancerName
  String appGatewayName
  String appGatewayBapId
  AzureNamedImage image
  AzureScaleSetSku sku
  AzureOperatingSystemConfig osConfig
  String provisioningState
  String application // TODO standardize between this and appName
  String clusterName
  String securityGroupName
  String subnetId /*Azure resource ID*/
  List<String> storageAccountNames
  Boolean isDisabled = false
  List<AzureInboundPortConfig> inboundPortConfigs = []
  String vnet
  String subnet
  String vnetResourceGroup
  Boolean hasNewSubnet = false
  Boolean createNewSubnet = false
  AzureExtensionCustomScriptSettings customScriptsSettings

  static class AzureScaleSetSku {
    String name
    String tier
    Long capacity
  }

  static class AzureOperatingSystemConfig {
    String adminUserName
    String adminPassword
    String computerNamePrefix
    String customData
  }

  static class AzureInboundPortConfig {
    String name
    String protocol
    int frontEndPortRangeStart
    int frontEndPortRangeEnd
    int backendPort

  }

  static class AzureExtensionCustomScriptSettings {
    Collection<String> fileUris
    String commandToExecute
  }

  Integer getStorageAccountCount() {
    (int)(sku.capacity / 20) + 1
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
  Set<String> getLoadBalancers() {
    return this.appGatewayName == null ? new HashSet<String>() : Sets.newHashSet(this.appGatewayName)
  }

  @Override
  Set<String> getSecurityGroups() {
    return this.securityGroupName == null ? new HashSet<String>() : Sets.newHashSet(this.securityGroupName)
  }

  @Override
  Boolean isDisabled() {
    this.isDisabled
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

  static AzureServerGroupDescription build(VirtualMachineScaleSetInner scaleSet) {
    def azureSG = new AzureServerGroupDescription()
    azureSG.name = scaleSet.name()
    def parsedName = Names.parseName(scaleSet.name())
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
    azureSG.subnet = AzureUtilities.getNameFromResourceId(azureSG.subnetId)
    azureSG.vnet = azureSG.subnetId ? AzureUtilities.getNameFromResourceId(azureSG.subnetId) : scaleSet.tags?.vnet
    azureSG.vnetResourceGroup = azureSG.subnetId ? AzureUtilities.getResourceGroupNameFromResourceId(azureSG.subnetId) : scaleSet.tags?.vnetResourceGroup
    azureSG.hasNewSubnet = (scaleSet.tags?.hasNewSubnet == "true")

    azureSG.createdTime = scaleSet.tags?.createdTime?.toLong()
    azureSG.image = new AzureNamedImage(isCustom: scaleSet.tags?.customImage, imageName: scaleSet.tags?.imageName)
    if (!azureSG.image.isCustom) {
      // Azure server group which was created using Azure Market Store images will have a number of storage accounts
      //   that were created at the time the server group was created; these storage account should be in saved in the
      //   tags map under storrageAccountNames key as a comma separated list of strings
      azureSG.storageAccountNames = new ArrayList<String>()
      String storageNames = scaleSet.tags?.storageAccountNames

      if (storageNames) azureSG.storageAccountNames.addAll(storageNames.split(","))
    }

    azureSG.region = scaleSet.location()
    azureSG.upgradePolicy = getPolicyFromMode(scaleSet.upgradePolicy().mode().name())

    // Get the image reference data
    def imgRef = scaleSet.virtualMachineProfile()?.storageProfile()?.imageReference()
    if (imgRef) {
      azureSG.image.offer = imgRef.offer()
      azureSG.image.publisher = imgRef.publisher()
      azureSG.image.sku = imgRef.sku()
      azureSG.image.version = imgRef.version()
    }

    // get the OS configuration data
    def osConfig = new AzureOperatingSystemConfig()
    def osProfile = scaleSet?.virtualMachineProfile()?.osProfile()
    if (osProfile) {
      osConfig.adminPassword = osProfile.adminPassword()
      osConfig.adminUserName = osProfile.adminUsername()
      osConfig.computerNamePrefix = osProfile.computerNamePrefix()
      osConfig.customData = osProfile.customData()

    }
    azureSG.osConfig = osConfig

    def customScriptSettings = new AzureExtensionCustomScriptSettings()
    def extensionProfile = scaleSet?.virtualMachineProfile()?.extensionProfile()
    if (extensionProfile) {
      def customScriptExtensionSettings = extensionProfile.extensions().find({
          it.type() == AzureUtilities.AZURE_CUSTOM_SCRIPT_EXT_TYPE_LINUX ||
          it.type() == AzureUtilities.AZURE_CUSTOM_SCRIPT_EXT_TYPE_WINDOWS
      })?.settings()
      //def customScriptExtensionSettings = extensionProfile.extensions.find({it.type=="CustomScript"}).settings
      if (customScriptExtensionSettings) {
        customScriptSettings = mapper.convertValue(customScriptExtensionSettings, AzureExtensionCustomScriptSettings)
      }
    }

    azureSG.customScriptsSettings = customScriptSettings

    def sku = new AzureScaleSetSku()
    def skuData = scaleSet.sku()
    if (skuData) {
      sku.capacity = skuData.capacity()
      sku.name = skuData.name()
      sku.tier = skuData.tier()
    }
    azureSG.sku = sku

    azureSG.provisioningState = scaleSet.provisioningState()

    azureSG
  }

  static Collection<Instance> filterInstancesByHealthState(Set<? extends Instance> instances, HealthState healthState) {
    (Collection<Instance>) instances?.findAll { Instance it -> it.getHealthState() == healthState }
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
