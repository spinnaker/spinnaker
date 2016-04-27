package com.netflix.spinnaker.clouddriver.azure.resources.servergroup.model

import com.microsoft.azure.management.compute.models.VirtualMachineScaleSetVM
import com.netflix.spinnaker.clouddriver.azure.common.AzureUtilities
import com.netflix.spinnaker.clouddriver.model.HealthState
import com.netflix.spinnaker.clouddriver.model.Instance


class AzureInstance implements Instance, Serializable {
  String name
  String resourceId
  String vhd
  HealthState healthState
  Long launchTime
  String zone
  String instanceType
  List<Map<String, String>> health

  AzureInstance(){
    zone = 'N/A'
  }

  static AzureInstance build(VirtualMachineScaleSetVM vm) {
    AzureInstance instance = new AzureInstance()
    instance.name = vm.name
    instance.instanceType = vm.sku.name
    instance.resourceId = vm.instanceId
    instance.vhd = vm.storageProfile?.osDisk?.vhd?.uri

    vm.instanceView?.statuses?.each { status ->
      def codes = status.code.split('/')
      switch (codes[0]) {
        case "ProvisioningState":
          if (codes[1].toLowerCase() == AzureUtilities.ProvisioningState.SUCCEEDED.toLowerCase()) {
            instance.launchTime = status.time?.millis
          } else {
            instance.healthState = HealthState.Failed
          }
          break
        case "PowerState":
          instance.healthState =
            codes[1].toLowerCase() == "Running".toLowerCase() ? HealthState.Up : HealthState.Down
          break
        default:
          break
      }

    }
    instance
  }

 }
