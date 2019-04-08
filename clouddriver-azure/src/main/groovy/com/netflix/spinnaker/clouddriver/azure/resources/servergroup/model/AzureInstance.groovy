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

import com.microsoft.azure.management.compute.VirtualMachineScaleSetVM
import com.netflix.spinnaker.clouddriver.azure.AzureCloudProvider
import com.netflix.spinnaker.clouddriver.azure.common.AzureUtilities
import com.netflix.spinnaker.clouddriver.model.HealthState
import com.netflix.spinnaker.clouddriver.model.Instance
import groovy.transform.CompileStatic

@CompileStatic
class AzureInstance implements Instance, Serializable {
  String name
  String resourceId
  String vhd
  HealthState healthState
  Long launchTime
  final String zone = 'N/A'
  String instanceType
  List<Map<String, Object>> health
  final String providerType = AzureCloudProvider.ID
  final String cloudProvider = AzureCloudProvider.ID

  static AzureInstance build(VirtualMachineScaleSetVM vm) {
    AzureInstance instance = new AzureInstance()
    instance.name = vm.name()
    instance.instanceType = vm.sku().name()
    instance.resourceId = vm.instanceId()
    instance.vhd = vm.storageProfile()?.osDisk()?.vhd()?.uri()

    vm.instanceView()?.statuses()?.each { status ->
      def codes = status.code().split('/')
      switch (codes[0]) {
        case "ProvisioningState":
          if (codes[1].toLowerCase() == AzureUtilities.ProvisioningState.SUCCEEDED.toLowerCase()) {
            instance.launchTime = status.time()?.millis
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
