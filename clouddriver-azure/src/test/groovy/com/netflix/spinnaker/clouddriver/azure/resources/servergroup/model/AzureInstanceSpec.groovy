package com.netflix.spinnaker.clouddriver.azure.resources.servergroup.model

import com.microsoft.azure.management.compute.models.InstanceViewStatus
import com.microsoft.azure.management.compute.models.Sku
import com.microsoft.azure.management.compute.models.VirtualMachineInstanceView
import com.microsoft.azure.management.compute.models.VirtualMachineScaleSetVM
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

    vm.instanceView >> instanceView
    instanceView.statuses >> statuses

    vm.sku >> sku
    sku.name >> "test"

    def instance = AzureInstance.build(vm)

    expect:
      instance.zone == 'N/A'
      instance.healthState == HealthState.Up
  }
}
