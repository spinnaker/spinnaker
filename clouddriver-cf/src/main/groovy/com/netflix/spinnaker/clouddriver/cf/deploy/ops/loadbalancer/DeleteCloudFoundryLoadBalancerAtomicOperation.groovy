/*
 * Copyright 2015 Pivotal Inc.
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

package com.netflix.spinnaker.clouddriver.cf.deploy.ops.loadbalancer

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.cf.deploy.description.DeleteCloudFoundryLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.cf.utils.CloudFoundryClientFactory
import org.springframework.beans.factory.annotation.Autowired

class DeleteCloudFoundryLoadBalancerAtomicOperation implements AtomicOperation<Void> {

  private static final String BASE_PHASE = "DELETE_LOAD_BALANCER"

  @Autowired
  CloudFoundryClientFactory cloudFoundryClientFactory

  private final DeleteCloudFoundryLoadBalancerDescription description

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  DeleteCloudFoundryLoadBalancerAtomicOperation(DeleteCloudFoundryLoadBalancerDescription description) {
    this.description = description
  }

  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing deletion of load balancer $description.loadBalancerName in $description.region..."

    def client = cloudFoundryClientFactory.createCloudFoundryClient(description.credentials, true)

    try {
      client.deleteRoute(description.loadBalancerName, client.defaultDomain.name)
    } catch (IllegalArgumentException e) {
      task.updateStatus BASE_PHASE, "Failed to delete load balancer => ${e.message}"
    }

    task.updateStatus BASE_PHASE, "Done deleting load balancer $description.loadBalancerName."

    null
  }

}
