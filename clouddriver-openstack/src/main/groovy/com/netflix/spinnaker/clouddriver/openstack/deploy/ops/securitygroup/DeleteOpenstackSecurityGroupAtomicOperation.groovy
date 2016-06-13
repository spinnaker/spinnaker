/*
 * Copyright 2016 Target, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.spinnaker.clouddriver.openstack.deploy.ops.securitygroup

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.securitygroup.DeleteOpenstackSecurityGroupDescription
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation

/**
 * Deletes an Openstack security group.
 *
 * Delete will fail in Openstack if the security group is associated to an instance.
 */
class DeleteOpenstackSecurityGroupAtomicOperation implements AtomicOperation<Void> {

  private final String BASE_PHASE = 'DELETE_SECURITY_GROUP'
  DeleteOpenstackSecurityGroupDescription description

  DeleteOpenstackSecurityGroupAtomicOperation(DeleteOpenstackSecurityGroupDescription description) {
    this.description = description
  }

  protected static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  /*
   * Delete:
   * curl -X POST -H "Content-Type: application/json" -d '[ { "deleteSecurityGroup": { "account": "test", "region": "west", "id": "ee411748-88b5-4825-a9d4-ec549d1a1276" } } ]' localhost:7002/openstack/ops
   * Task status:
   * curl -X GET -H "Accept: application/json" localhost:7002/task/1
   */
  @Override
  Void operate(List priorOutputs) {
    task.updateStatus(BASE_PHASE, "Deleting security group ${description.id}")

    // TODO: Check if Openstack failure gives a decent error
    description.credentials.provider.deleteSecurityGroup(description.region, description.id)

    task.updateStatus(BASE_PHASE, "Finished deleting security group ${description.id}")
  }
}
