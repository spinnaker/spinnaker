/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.appengine.deploy.ops

import com.netflix.spinnaker.clouddriver.appengine.deploy.description.DeleteAppengineLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import groovy.util.logging.Slf4j

@Slf4j
class DeleteAppengineLoadBalancerAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "DELETE_LOAD_BALANCER"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private final DeleteAppengineLoadBalancerDescription description

  DeleteAppengineLoadBalancerAtomicOperation(DeleteAppengineLoadBalancerDescription description) {
    this.description = description
  }
  /**
   * curl -X POST -H "Content-Type: application/json" -d '[ { "deleteLoadBalancer": { "loadBalancerName": "default", "credentials": "my-appengine-account" }} ]' localhost:7002/appengine/ops
   */
  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing deletion of load balancer $description.loadBalancerName..."

    def credentials = description.credentials
    def appengine = credentials.appengine
    def project = credentials.project
    def loadBalancerName = description.loadBalancerName

    appengine.apps().services().delete(project, loadBalancerName).execute()

    task.updateStatus BASE_PHASE, "Successfully deleted load balancer $loadBalancerName."
    return null
  }
}
