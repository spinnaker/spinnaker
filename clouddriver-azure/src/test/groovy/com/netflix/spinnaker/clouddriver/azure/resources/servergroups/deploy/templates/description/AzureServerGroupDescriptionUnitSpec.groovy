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

package com.netflix.spinnaker.clouddriver.azure.resources.servergroups.deploy.templates.description

import com.microsoft.azure.management.compute.models.ImageReference
import com.microsoft.azure.management.compute.models.Sku
import com.microsoft.azure.management.compute.models.UpgradePolicy
import com.microsoft.azure.management.compute.models.VirtualMachineScaleSet
import com.microsoft.azure.management.compute.models.VirtualMachineScaleSetOSProfile
import com.microsoft.azure.management.compute.models.VirtualMachineScaleSetSku
import com.microsoft.azure.management.compute.models.VirtualMachineScaleSetStorageProfile
import com.microsoft.azure.management.compute.models.VirtualMachineScaleSetVMProfile
import com.netflix.spinnaker.clouddriver.azure.common.AzureUtilities
import com.netflix.spinnaker.clouddriver.azure.resources.servergroup.model.AzureServerGroupDescription
import com.netflix.spinnaker.clouddriver.azure.resources.vmimage.model.AzureNamedImage
import spock.lang.Specification


class AzureServerGroupDescriptionUnitSpec extends Specification {
  VirtualMachineScaleSet scaleSet

  void setup() {
    scaleSet = createScaleSet()
  }

  def 'should generate correct server group description from an Azure scale set'() {
    AzureServerGroupDescription description = AzureServerGroupDescription.build(scaleSet)
    expect: descriptionIsValid(description, scaleSet)
  }


  private static VirtualMachineScaleSet createScaleSet() {
    Map<String, String> tags = [ "stack": "testStack",
                                 "detail": "testDetail",
                                 "appName" : "testScaleSet",
                                 "cluster" : "testScaleSet-testStack-testDetail"]

    VirtualMachineScaleSet scaleSet = new VirtualMachineScaleSet()
    //name is read only
    //scaleSet.name = 'testScaleSet-testStack-testDetail'
    scaleSet.location = 'testLocation'
    scaleSet.tags = tags

    def upgradePolicy = new UpgradePolicy()
    upgradePolicy.mode = "Automatic"
    scaleSet.upgradePolicy = upgradePolicy

    VirtualMachineScaleSetVMProfile vmProfile = new VirtualMachineScaleSetVMProfile()
    VirtualMachineScaleSetOSProfile osProfile = new VirtualMachineScaleSetOSProfile()
    osProfile.adminUsername = "testtest"
    osProfile.adminPassword = "t3stt3st"
    osProfile.computerNamePrefix = "nflx"
    vmProfile.osProfile = osProfile

    VirtualMachineScaleSetStorageProfile storageProfile = new VirtualMachineScaleSetStorageProfile()
    ImageReference image = new ImageReference()
    image.offer = "testOffer"
    image.publisher = "testPublisher"
    image.sku = "testSku"
    image.version = "testVersion"
    storageProfile.imageReference = image
    vmProfile.storageProfile = storageProfile

    scaleSet.virtualMachineProfile = vmProfile

    Sku sku = new Sku()
    sku.name = "testSku"
    sku.capacity = 100
    sku.tier = "tier1"
    scaleSet.sku = sku

    scaleSet.provisioningState = "Succeeded"

    scaleSet
  }

  private static Boolean descriptionIsValid(AzureServerGroupDescription description, VirtualMachineScaleSet scaleSet) {
    (description.name == scaleSet.name
      && description.appName == scaleSet.tags.appName
      && description.tags == scaleSet.tags
      && description.stack == scaleSet.tags.stack
      && description.detail == scaleSet.tags.detail
      && description.application == scaleSet.tags.appName
      && description.clusterName == scaleSet.tags.cluster
      && description.region == scaleSet.location
      && description.upgradePolicy == getPolicy(scaleSet.upgradePolicy.mode)
      && isValidImage(description.image, scaleSet)
      && isValidOsConfig(description.osConfig, scaleSet)
      && isValidSku(description.sku, scaleSet)
      && description.provisioningState == AzureUtilities.ProvisioningState.SUCCEEDED)
  }

  private static AzureServerGroupDescription.UpgradePolicy getPolicy(String scaleSetPolicyMode)
  {
    AzureServerGroupDescription.getPolicyFromMode(scaleSetPolicyMode)
  }

  private static Boolean isValidImage(AzureNamedImage image, VirtualMachineScaleSet scaleSet) {
    (image.offer == scaleSet.virtualMachineProfile.storageProfile.imageReference.offer
      && image.sku == scaleSet.virtualMachineProfile.storageProfile.imageReference.sku
      && image.publisher == scaleSet.virtualMachineProfile.storageProfile.imageReference.publisher
      && image.version == scaleSet.virtualMachineProfile.storageProfile.imageReference.version)
  }

  private static Boolean isValidOsConfig (AzureServerGroupDescription.AzureOperatingSystemConfig osConfig, VirtualMachineScaleSet scaleSet) {
    (osConfig.adminPassword == scaleSet.virtualMachineProfile.osProfile.adminPassword
      && osConfig.adminUserName == scaleSet.virtualMachineProfile.osProfile.adminUsername
      && osConfig.computerNamePrefix == scaleSet.virtualMachineProfile.osProfile.computerNamePrefix)
  }

  private static Boolean isValidSku (AzureServerGroupDescription.AzureScaleSetSku sku, VirtualMachineScaleSet scaleSet) {
    (sku.name == scaleSet.sku.name
      && sku.tier == scaleSet.sku.tier
      && sku.capacity == scaleSet.sku.capacity)
  }

}
