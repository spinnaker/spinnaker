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

import com.microsoft.azure.management.compute.InstanceViewStatus
import com.microsoft.azure.management.compute.Sku
import com.microsoft.azure.management.compute.VirtualMachineInstanceView
import com.microsoft.azure.management.compute.VirtualMachineScaleSetVM
import com.netflix.spinnaker.clouddriver.azure.common.AzureUtilities
import com.netflix.spinnaker.clouddriver.model.HealthState
import spock.lang.Specification

class AzureInstanceSpec extends Specification {

  def 'should generate a correctly structured instance'(){
    def vm = Mock(VirtualMachineScaleSetVM)
    def instanceView = Mock(VirtualMachineInstanceView)
    def sku = Mock(Sku)

    def provisioningStatus = new InstanceViewStatus()
    provisioningStatus.code = 'ProvisioningState/' + AzureUtilities.ProvisioningState.SUCCEEDED
    def powerStatus = new InstanceViewStatus()
    powerStatus.code = 'PowerState/Running'

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
}
