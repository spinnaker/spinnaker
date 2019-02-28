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

import com.microsoft.azure.management.compute.ImageReference
import com.microsoft.azure.management.compute.Sku
import com.microsoft.azure.management.compute.UpgradeMode
import com.microsoft.azure.management.compute.UpgradePolicy
import com.microsoft.azure.management.compute.VirtualMachineScaleSetOSProfile
import com.microsoft.azure.management.compute.VirtualMachineScaleSetStorageProfile
import com.microsoft.azure.management.compute.VirtualMachineScaleSetVMProfile
import com.microsoft.azure.management.compute.implementation.VirtualMachineScaleSetInner
import com.netflix.spinnaker.clouddriver.azure.common.AzureUtilities
import com.netflix.spinnaker.clouddriver.azure.resources.servergroup.model.AzureServerGroupDescription
import com.netflix.spinnaker.clouddriver.azure.resources.vmimage.model.AzureNamedImage
import spock.lang.Specification

class AzureServerGroupDescriptionUnitSpec extends Specification {
  VirtualMachineScaleSetInner scaleSet

  void setup() {
    scaleSet = createScaleSet()
  }

  def 'should generate correct server group description from an Azure scale set'() {
    AzureServerGroupDescription description = AzureServerGroupDescription.build(scaleSet)
    expect: descriptionIsValid(description, scaleSet)
  }


  private static VirtualMachineScaleSetInner createScaleSet() {
    Map<String, String> tags = [ "stack": "testStack",
                                 "detail": "testDetail",
                                 "appName" : "testScaleSet",
                                 "cluster" : "testScaleSet-testStack-testDetail"]

    VirtualMachineScaleSetInner scaleSet = new VirtualMachineScaleSetInner()
    //name is read only
    //scaleSet.name = 'testScaleSet-testStack-testDetail'
    scaleSet.withLocation 'testLocation'
    scaleSet.withTags tags

    def upgradePolicy = new UpgradePolicy()
    upgradePolicy.withMode UpgradeMode.AUTOMATIC
    scaleSet.withUpgradePolicy upgradePolicy

    VirtualMachineScaleSetVMProfile vmProfile = new VirtualMachineScaleSetVMProfile()
    VirtualMachineScaleSetOSProfile osProfile = new VirtualMachineScaleSetOSProfile()
    osProfile.withAdminUsername "testtest"
    osProfile.withAdminPassword "t3stt3st"
    osProfile.withComputerNamePrefix "nflx"
    vmProfile.withOsProfile osProfile

    VirtualMachineScaleSetStorageProfile storageProfile = new VirtualMachineScaleSetStorageProfile()
    ImageReference image = new ImageReference()
    image.withOffer "testOffer"
    image.withPublisher "testPublisher"
    image.withSku "testSku"
    image.withVersion "testVersion"
    storageProfile.withImageReference image
    vmProfile.withStorageProfile storageProfile

    scaleSet.withVirtualMachineProfile vmProfile

    Sku sku = new Sku()
    sku.withName "testSku"
    sku.withCapacity 100
    sku.withTier "tier1"
    scaleSet.withSku sku

    scaleSet
  }

  private static Boolean descriptionIsValid(AzureServerGroupDescription description, VirtualMachineScaleSetInner scaleSet) {
    (description.name == scaleSet.name()
      && description.appName == scaleSet.tags.appName
      && description.tags == scaleSet.tags
      && description.stack == scaleSet.tags.stack
      && description.detail == scaleSet.tags.detail
      && description.application == scaleSet.tags.appName
      && description.clusterName == scaleSet.tags.cluster
      && description.region == scaleSet.location()
      && description.upgradePolicy.name().toLowerCase() == scaleSet.upgradePolicy().mode().name().toLowerCase()
      && isValidImage(description.image, scaleSet)
      && isValidOsConfig(description.osConfig, scaleSet)
      && isValidSku(description.sku, scaleSet)
      && description.provisioningState == null)
  }

  private static AzureServerGroupDescription.UpgradePolicy getPolicy(String scaleSetPolicyMode)
  {
    AzureServerGroupDescription.getPolicyFromMode(scaleSetPolicyMode)
  }

  private static Boolean isValidImage(AzureNamedImage image, VirtualMachineScaleSetInner scaleSet) {
    (image.offer == scaleSet.virtualMachineProfile().storageProfile().imageReference().offer()
      && image.sku == scaleSet.virtualMachineProfile().storageProfile().imageReference().sku()
      && image.publisher == scaleSet.virtualMachineProfile().storageProfile().imageReference().publisher()
      && image.version == scaleSet.virtualMachineProfile().storageProfile().imageReference().version())
  }

  private static Boolean isValidOsConfig (AzureServerGroupDescription.AzureOperatingSystemConfig osConfig, VirtualMachineScaleSetInner scaleSet) {
    (osConfig.adminPassword == scaleSet.virtualMachineProfile().osProfile().adminPassword()
      && osConfig.adminUserName == scaleSet.virtualMachineProfile().osProfile().adminUsername()
      && osConfig.computerNamePrefix == scaleSet.virtualMachineProfile().osProfile().computerNamePrefix())
  }

  private static Boolean isValidSku (AzureServerGroupDescription.AzureScaleSetSku sku, VirtualMachineScaleSetInner scaleSet) {
    (sku.name == scaleSet.sku().name()
      && sku.tier == scaleSet.sku().tier()
      && sku.capacity == scaleSet.sku().capacity())
  }

}
