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
import com.netflix.spinnaker.clouddriver.cf.deploy.description.UpsertCloudFoundryLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.cf.utils.CloudFoundryClientFactory
import org.springframework.beans.factory.annotation.Autowired

class UpsertCloudFoundryLoadBalancerAtomicOperation implements AtomicOperation<Map> {

  private static final String BASE_PHASE = "UPSERT_LOAD_BALANCER"

  @Autowired
  CloudFoundryClientFactory cloudFoundryClientFactory

  private final UpsertCloudFoundryLoadBalancerDescription description

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  UpsertCloudFoundryLoadBalancerAtomicOperation(UpsertCloudFoundryLoadBalancerDescription description) {
    this.description = description
  }

  @Override
  Map operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing creation of load balancer $description.loadBalancerName in $description.region..."

    def client = cloudFoundryClientFactory.createCloudFoundryClient(description.credentials, true)

    client.addRoute(description.loadBalancerName, client.defaultDomain.name)

    task.updateStatus BASE_PHASE, "Done creating load balancer $description.loadBalancerName."

    [loadBalancers: [(description.region): [name: description.loadBalancerName]]]
  }

}
