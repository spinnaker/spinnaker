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
 *  See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.openstack.deploy.ops.securitygroup

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.openstack.deploy.description.securitygroup.OpenstackSecurityGroupDescription
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import groovy.util.logging.Slf4j

/**
 * Creates or updates an Openstack security group.
 */
@Slf4j
class UpsertOpenstackSecurityGroupAtomicOperation implements AtomicOperation<Void> {

  private final String BASE_PHASE = "UPSERT_SECURITY_GROUP"
  OpenstackSecurityGroupDescription description

  UpsertOpenstackSecurityGroupAtomicOperation(OpenstackSecurityGroupDescription description) {
    this.description = description
  }

  protected static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  /*
  * Create:
  * curl -X POST -H "Content-Type: application/json" -d '[ { "upsertSecurityGroup": { "name": "sg-test-1", "description": "test", "account": "test", "rules": [ { "fromPort": 80, "toPort": 90, "cidr": "0.0.0.0/0"  } ] } } ]' localhost:7002/openstack/ops
  * Update:
  * curl -X POST -H "Content-Type: application/json" -d '[ { "upsertSecurityGroup": { "id": "e56fa7eb-550d-42d4-8d3f-f658fbacd496", "name": "sg-test-1", "description": "test", "account": "test", "rules": [ { "fromPort": 80, "toPort": 90, "cidr": "0.0.0.0/0"  } ] } } ]' localhost:7002/openstack/ops
  * Task status:
  * curl -X GET -H "Accept: application/json" localhost:7002/task/1
  */
  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Upserting security group ${description.name}..."

    description.credentials.provider.upsertSecurityGroup(description.id, description.name, description.description, description.rules)

    task.updateStatus BASE_PHASE, "Finished upserting security group ${description.name}."
  }
}
