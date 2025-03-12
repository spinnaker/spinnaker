/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.google.deploy.ops

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.google.deploy.GoogleOperationPoller
import com.netflix.spinnaker.clouddriver.google.deploy.SafeRetry
import com.netflix.spinnaker.clouddriver.google.deploy.description.DeleteGoogleSecurityGroupDescription
import org.springframework.beans.factory.annotation.Autowired

/**
 * Delete a firewall rule from the specified project.
 *
 * Uses {@link https://cloud.google.com/compute/docs/reference/latest/firewalls/delete}
 */
class DeleteGoogleSecurityGroupAtomicOperation extends GoogleAtomicOperation<Void> {
  private static final String BASE_PHASE = "DELETE_SECURITY_GROUP"

  @Autowired
  private GoogleOperationPoller googleOperationPoller

  @Autowired
  SafeRetry safeRetry

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private final DeleteGoogleSecurityGroupDescription description

  DeleteGoogleSecurityGroupAtomicOperation(DeleteGoogleSecurityGroupDescription description) {
    this.description = description
  }

  /**
   * curl -X POST -H "Content-Type: application/json" -d '[ { "deleteSecurityGroup": { "securityGroupName": "mysecuritygroup", "credentials": "my-account-name" }} ]' localhost:7002/gce/ops
   */
  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing deletion of security group $description.securityGroupName..."

    def compute = description.credentials.compute
    def project = description.credentials.project
    def firewallRuleName = description.securityGroupName

    def deleteSecurityGroupOperation = safeRetry.doRetry(
        {
          timeExecute(
              compute.firewalls().delete(project, firewallRuleName),
              "compute.firewalls.delete",
              TAG_SCOPE, SCOPE_GLOBAL)
        },
        "Firewall rule ${firewallRuleName}",
        task,
        [400, 403, 412],
        [404],
        [action: "delete", phase: BASE_PHASE, operation: "compute.firewalls.delete", (TAG_SCOPE): SCOPE_GLOBAL],
        registry
    )

    googleOperationPoller.waitForGlobalOperation(compute, project, deleteSecurityGroupOperation.getName(),
      null, task, "delete security group $firewallRuleName", BASE_PHASE)

    task.updateStatus BASE_PHASE, "Done deleting security group $firewallRuleName."
    null
  }
}
