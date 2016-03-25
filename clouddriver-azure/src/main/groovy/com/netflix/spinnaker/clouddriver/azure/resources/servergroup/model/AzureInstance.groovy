package com.netflix.spinnaker.clouddriver.azure.resources.servergroup.model

import com.microsoft.azure.management.compute.models.VirtualMachineScaleSetVM
import com.netflix.spinnaker.clouddriver.azure.common.AzureUtilities
import com.netflix.spinnaker.clouddriver.model.HealthState
import com.netflix.spinnaker.clouddriver.model.Instance


class AzureInstance implements Instance, Serializable {
  String name
  HealthState healthState
  Long launchTime
  String zone
  List<Map<String, String>> health

  static AzureInstance build(VirtualMachineScaleSetVM vm) {
    AzureInstance instance = new AzureInstance()
    instance.name = vm.name
    vm.instanceView.statuses.each { status ->
      def codes = status.code.split('/')
      switch (codes[0]) {
        case "ProvisioningState":
          if (codes[1].toLowerCase() == AzureUtilities.ProvisioningState.SUCCEEDED.toLowerCase()) {
            instance.launchTime = status.time?.millis
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
