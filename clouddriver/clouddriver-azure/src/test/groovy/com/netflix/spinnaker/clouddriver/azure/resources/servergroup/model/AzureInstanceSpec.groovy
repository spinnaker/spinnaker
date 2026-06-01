/*
 * Copyright 2016 The original authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.clouddriver.azure.resources.servergroup.model

import com.azure.resourcemanager.compute.fluent.models.VirtualMachineScaleSetVMInner
import com.azure.resourcemanager.compute.models.InstanceViewStatus
import com.azure.resourcemanager.compute.models.Sku
import com.azure.resourcemanager.compute.models.VirtualMachineInstanceView
import com.azure.resourcemanager.compute.models.VirtualMachineScaleSetVM
import com.netflix.spinnaker.clouddriver.azure.common.AzureUtilities
import com.netflix.spinnaker.clouddriver.model.HealthState
import spock.lang.Specification

class AzureInstanceSpec extends Specification {

  def 'should generate a correctly structured instance'(){
    def vm = Mock(VirtualMachineScaleSetVM)
    def instanceView = Mock(VirtualMachineInstanceView)
    def sku = new Sku()

    def provisioningStatus = new InstanceViewStatus()
    provisioningStatus.withCode( 'ProvisioningState/' + AzureUtilities.ProvisioningState.SUCCEEDED)
    def powerStatus = new InstanceViewStatus()
    powerStatus.withCode( 'PowerState/Running')

    List<InstanceViewStatus> statuses = [provisioningStatus, powerStatus]


    vm.instanceView() >> instanceView
    vm.instanceView().statuses() >> statuses

    vm.sku() >> sku
    sku.name() >> "test"

    def instance = AzureInstance.build(vm)

    expect:
      instance.zone == 'N/A'
      instance.healthState == HealthState.Up
  }

  def 'should populate zone from the VMSS VM availability zone, falling back to N/A'(){
    def vm = Mock(VirtualMachineScaleSetVM)
    def sku = new Sku()

    vm.innerModel() >> innerWithZones(zones)
    vm.sku() >> sku
    sku.name() >> "test"

    expect:
      AzureInstance.build(vm).zone == expectedZone

    where:
      zones || expectedZone
      ['1'] || '1'
      null  || 'N/A'
      []    || 'N/A'
  }

  private static VirtualMachineScaleSetVMInner innerWithZones(List<String> zones) {
    def inner = new VirtualMachineScaleSetVMInner()
    def field = VirtualMachineScaleSetVMInner.getDeclaredField('zones')
    field.accessible = true
    field.set(inner, zones)
    inner
  }
}
