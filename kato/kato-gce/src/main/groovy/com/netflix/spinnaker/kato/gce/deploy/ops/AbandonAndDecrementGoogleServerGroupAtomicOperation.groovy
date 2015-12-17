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

package com.netflix.spinnaker.kato.gce.deploy.ops

import com.google.api.services.compute.model.InstanceGroupManagersAbandonInstancesRequest
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.kato.gce.deploy.GCEUtil
import com.netflix.spinnaker.kato.gce.deploy.description.AbandonAndDecrementGoogleServerGroupDescription

/**
 * Abandon instances from a managed instance group, and decrement the size of the managed instance group.
 *
 * This is an alternative to {@link TerminateAndDecrementGoogleServerGroupAtomicOperation} where the instances are not deleted, but
 * rather are left as standalone instances that are not associated with a managed instance group.
 *
 * @see TerminateAndDecrementGoogleServerGroupAtomicOperation
 */
class AbandonAndDecrementGoogleServerGroupAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "ABANDON_AND_DEC_INSTANCES"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private final AbandonAndDecrementGoogleServerGroupDescription description

  AbandonAndDecrementGoogleServerGroupAtomicOperation(AbandonAndDecrementGoogleServerGroupDescription description) {
    this.description = description
  }

  /**
   * Attempt to abandon the specified instanceIds and remove from the specified managed instance group.
   *
   * curl -X POST -H "Content-Type: application/json" -d '[ { "abandonAndDecrementGoogleServerGroupDescription": { "serverGroupName": "myapp-dev-v000", "instanceIds": ["myapp-dev-v000-abcd"], "zone": "us-central1-f", "credentials": "my-account-name" }} ]' localhost:7002/ops
   */
  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing abandon and decrement of instances " +
      "(${description.instanceIds.join(", ")}) from server group ${description.serverGroupName}..."

    def project = description.credentials.project
    def compute = description.credentials.compute
    def zone = description.zone
    def serverGroupName = description.serverGroupName
    def instanceIds = description.instanceIds
    def instanceUrls = GCEUtil.deriveInstanceUrls(project, zone, serverGroupName, instanceIds, description.credentials)
    def abandonRequest = new InstanceGroupManagersAbandonInstancesRequest().setInstances(instanceUrls)

    compute.instanceGroupManagers().abandonInstances(project, zone, serverGroupName, abandonRequest).execute()

    task.updateStatus BASE_PHASE, "Done abandoning and decrementing instances " +
      "(${description.instanceIds.join(", ")}) from server group ${serverGroupName}."
    null
  }
}
