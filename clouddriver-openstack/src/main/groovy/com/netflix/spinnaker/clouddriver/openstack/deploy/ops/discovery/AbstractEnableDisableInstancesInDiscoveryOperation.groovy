/*
 * Copyright 2016 Target, Inc.
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

package com.netflix.spinnaker.clouddriver.openstack.deploy.ops.discovery

import com.netflix.spinnaker.clouddriver.openstack.client.OpenstackClientProvider
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.instance.OpenstackInstancesDescription
import com.netflix.spinnaker.clouddriver.openstack.task.TaskStatusAware
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.consul.deploy.ops.EnableDisableConsulInstance

abstract class AbstractEnableDisableInstancesInDiscoveryOperation implements AtomicOperation<Void>, TaskStatusAware {

  OpenstackInstancesDescription description

  AbstractEnableDisableInstancesInDiscoveryOperation(OpenstackInstancesDescription description) {
    this.description = description
  }

  @Override
  Void operate(List priorOutputs) {
    def credentials = description.credentials.credentials
    def instances = description.instanceIds
    String verb = disable ? 'disable' : 'enable'
    String presentParticipling = disable ? 'Disabling' : 'Enabling'

    task.updateStatus phaseName, "Initializing $verb server group operation for instances $instances in $description.region..."

    if (!credentials.consulConfig?.enabled) {
      throw new IllegalArgumentException("Consul isn't enabled for account $credentials.name.")
    }

    instances.each { String instance ->
      //TODO - Need to functionally test yet.
      String ipAddress = clientProvider.getIpForInstance(description.region, instance)
      if (ipAddress) {
        task.updateStatus phaseName, "$presentParticipling instance $instance at $ipAddress..."
        EnableDisableConsulInstance.operate(credentials.consulConfig,
          instance,
          disable
            ? EnableDisableConsulInstance.State.disable
            : EnableDisableConsulInstance.State.enable)
      }
    }

    return null
  }

  /**
   * Operations must indicate if they are disabling the instance from service discovery.
   * @return
   */
  abstract boolean isDisable()

  /**
   * Phase name associated to operation.
   * @return
   */
  abstract String getPhaseName()

  /**
   * Helper method to access client provider via account credentials.
   * @return
   */
  OpenstackClientProvider getClientProvider() {
    description.credentials.provider
  }
}
