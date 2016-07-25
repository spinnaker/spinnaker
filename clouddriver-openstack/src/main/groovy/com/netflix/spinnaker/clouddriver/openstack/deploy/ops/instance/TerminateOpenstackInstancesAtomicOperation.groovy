/*
 * Copyright 2016 Target, Inc.
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

package com.netflix.spinnaker.clouddriver.openstack.deploy.ops.instance

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.instance.OpenstackInstancesDescription
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import groovy.util.logging.Slf4j

/**
 * Terminates an Openstack instance.
 */
@Slf4j
class TerminateOpenstackInstancesAtomicOperation implements AtomicOperation<Void> {

  private final String BASE_PHASE = "TERMINATE_INSTANCES"
  OpenstackInstancesDescription description

  TerminateOpenstackInstancesAtomicOperation(OpenstackInstancesDescription description) {
    this.description = description
  }

  protected static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  /*
   * curl -X POST -H "Content-Type: application/json" -d '[ { "terminateInstances": { "instanceIds": ["os-test-v000-beef"], "account": "test", "region": "region1" }} ]' localhost:7002/openstack/ops
   * curl -X GET -H "Accept: application/json" localhost:7002/task/1
   */
  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing terminate instances operation..."

    description.instanceIds.each {
      task.updateStatus BASE_PHASE, "Deleting $it"
      description.credentials.provider.deleteInstance(description.region, it)
      task.updateStatus BASE_PHASE, "Deleted $it"
    }

    task.updateStatus BASE_PHASE, "Successfully terminated provided instances."
  }
}
