/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.google.deploy.ops.discovery

import com.netflix.spinnaker.clouddriver.consul.deploy.ops.EnableDisableConsulInstance
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.google.deploy.description.GoogleInstanceListDescription
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation

abstract class AbstractEnableDisableInstancesInDiscoveryOperation implements AtomicOperation<Void> {
  abstract boolean isDisable()

  abstract String getPhaseName()

  GoogleInstanceListDescription description

  AbstractEnableDisableInstancesInDiscoveryOperation(GoogleInstanceListDescription description) {
    this.description = description
  }

  Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Override
  Void operate(List priorOutputs) {
    def credentials = description.credentials
    def instances = description.instanceIds
    String verb = disable ? 'disable' : 'enable'
    String presentParticipling = disable ? 'Disabling' : 'Enabling'

    task.updateStatus phaseName, "Initializing $verb server group operation for instances $instances in " +
      "$description.region..."

    if (!credentials.consulConfig?.enabled) {
      throw new IllegalArgumentException("Consul isn't enabled for account $credentials.name.")
    }

    instances.each { String instance ->
      task.updateStatus phaseName, "$presentParticipling instance $instance..."
      EnableDisableConsulInstance.operate(credentials.consulConfig,
        instance,
        disable
          ? EnableDisableConsulInstance.State.disable
          : EnableDisableConsulInstance.State.enable)
    }

    return null
  }
}
