/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.titus.deploy.ops.discovery

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.eureka.deploy.ops.AbstractEurekaSupport
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.titus.TitusClientProvider
import com.netflix.spinnaker.clouddriver.titus.client.model.TaskState
import com.netflix.spinnaker.clouddriver.titus.deploy.description.EnableDisableInstanceDiscoveryDescription
import com.netflix.spinnaker.clouddriver.titus.model.TitusServerGroup
import org.springframework.beans.factory.annotation.Autowired

abstract class AbstractEnableDisableTitusInstanceDiscoveryAtomicOperation implements AtomicOperation<Void> {
  abstract boolean isEnable()

  abstract String getPhaseName()

  private final TitusClientProvider titusClientProvider

  @Autowired
  TitusEurekaSupport discoverySupport

  EnableDisableInstanceDiscoveryDescription description

  AbstractEnableDisableTitusInstanceDiscoveryAtomicOperation(TitusClientProvider titusClientProvider, EnableDisableInstanceDiscoveryDescription description) {
    this.titusClientProvider = titusClientProvider
    this.description = description
  }

  @Override
  Void operate(List priorOutputs) {
    def performingAction = isEnable() ? 'Enabling' : 'Disabling'
    def task = getTask()

    task.updateStatus phaseName, "Initializing ${performingAction} of Instances (${description.instanceIds.join(", ")}) in Discovery Operation..."
    if (!description.credentials.discoveryEnabled) {
      task.updateStatus phaseName, "Discovery is not enabled, unable to modify instance status"
      task.fail()
      return null
    }
    def titusClient = titusClientProvider.getTitusClient(description.credentials, description.region)
    def job = titusClient.findJobByName(description.asgName, true)
    if (!job) {
      return
    }
    def asgInstanceIds = new TitusServerGroup(job, description.credentials.name, description.region).instances.findAll {
      (it.state == TaskState.RUNNING || it.state == TaskState.STARTING) && description.instanceIds.contains(it.id)
    }
    if (!asgInstanceIds) {
      return
    }
    def status = isEnable() ? AbstractEurekaSupport.DiscoveryStatus.UP : AbstractEurekaSupport.DiscoveryStatus.OUT_OF_SERVICE
    discoverySupport.updateDiscoveryStatusForInstances(
      description, task, phaseName, status, asgInstanceIds*.instanceId
    )
    null
  }

  Task getTask() {
    TaskRepository.threadLocalTask.get()
  }
}
