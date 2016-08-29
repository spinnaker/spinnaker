/*
* Copyright 2016 Veritas Technologies LLC.
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

package com.netflix.spinnaker.clouddriver.openstack.deploy.ops.servergroup

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.openstack.client.OpenstackClientProvider
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.servergroup.OpenstackServerGroupAtomicOperationDescription
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import org.openstack4j.model.heat.Stack

abstract class AbstractEnableDisableOpenstackAtomicOperation implements AtomicOperation<Void> {
  abstract boolean isDisable()

  abstract String getPhaseName()

  abstract Void addOrRemoveInstancesFromLoadBalancer(List<String> instanceIds, List<String> loadBalancers)

  static final int DEFAULT_WEIGHT = 1

  OpenstackServerGroupAtomicOperationDescription description

  AbstractEnableDisableOpenstackAtomicOperation(OpenstackServerGroupAtomicOperationDescription description) {
    this.description = description
  }

  protected static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Override
  Void operate(List priorOutputs) {
    String verb = disable ? 'disable' : 'enable'
    String presentParticipling = disable ? 'Disabling' : 'Enabling'

    task.updateStatus phaseName, "Initializing $verb server group operation for $description.serverGroupName in $description.region..."
    List<String> instanceIds = provider.getInstanceIdsForStack(description.region, description.serverGroupName)

    task.updateStatus phaseName, "Getting stack details for $description.serverGroupName..."
    Stack stack = provider.getStack(description.region, description.serverGroupName)

    addOrRemoveInstancesFromLoadBalancer(instanceIds, stack.tags)

    task.updateStatus phaseName, "Done ${presentParticipling.toLowerCase()} server group $description.serverGroupName in $description.region."
  }

  OpenstackClientProvider getProvider() {
    description.credentials.provider
  }

}
