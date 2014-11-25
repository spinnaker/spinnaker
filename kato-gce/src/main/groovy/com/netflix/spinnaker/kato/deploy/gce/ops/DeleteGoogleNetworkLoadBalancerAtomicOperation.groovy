/*
 * Copyright 2014 Google, Inc.
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

package com.netflix.spinnaker.kato.deploy.gce.ops

import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.deploy.gce.description.DeleteGoogleNetworkLoadBalancerDescription
import com.netflix.spinnaker.kato.orchestration.AtomicOperation

class DeleteGoogleNetworkLoadBalancerAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "DELETE_NETWORK_LOAD_BALANCER"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private final DeleteGoogleNetworkLoadBalancerDescription description

  DeleteGoogleNetworkLoadBalancerAtomicOperation(DeleteGoogleNetworkLoadBalancerDescription description) {
    this.description = description
  }

  /**
   * curl -X POST -H "Content-Type: application/json" -d '[ { "deleteGoogleNetworkLoadBalancerDescription": { "networkLoadBalancerName": "myapp-dev-v000", "zone": "us-central1-b", "credentials": "my-account-name" }} ]' localhost:8501/ops
   */
  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing delete of network load balancer $description.networkLoadBalancerName " +
        "in $description.zone..."
    task.updateStatus BASE_PHASE, "Done deleting network load balancer $description.networkLoadBalancerName in $zone."
    null
  }
}
