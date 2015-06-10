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

import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.gce.deploy.GCEUtil
import com.netflix.spinnaker.kato.gce.deploy.description.AbandonAndDecrementGoogleServerGroupDescription
import com.netflix.spinnaker.kato.orchestration.AtomicOperation
import com.google.api.services.replicapool.ReplicapoolScopes
import com.google.api.services.replicapool.model.InstanceGroupManagersAbandonInstancesRequest
/**
 * Abandon instances from a replica pool, and decrement the size of the replica pool.
 *
 * This is an alternative to {@link TerminateAndDecrementGoogleServerGroup}
 * where the instances are not deleted, rather are left as normal instances
 * that are not associated with a replica pool.
 * 
 * @see TerminateAndDecrementGoogleServerGroupAtomicOperation
 */
class AbandonAndDecrementGoogleServerGroupAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "ABANDONING_INSTANCES"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private final AbandonAndDecrementGoogleServerGroupDescription description
  private final ReplicaPoolBuilder replicaPoolBuilder

  AbandonAndDecrementGoogleServerGroupAtomicOperation(AbandonAndDecrementGoogleServerGroupDescription description,
                                                      ReplicaPoolBuilder replicaPoolBuilder) {
    this.description = description
    this.replicaPoolBuilder = replicaPoolBuilder
  }

  /**
   * Attempt to abandon the specified instanceIds and remove from the specified replica pool.
   *
   * curl -X POST -H "Content-Type: application/json" -d '[ { "abandonAndDecrementGoogleServerGroupDescription": { "replicaPoolName": "myapp-dev-v000", "instanceIds": ["myapp-dev-v000-abcd"], "zone": "us-central1-b", "credentials": "my-account-name" }} ]' localhost:8501/ops
   */
  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Attempting to abandon instances (${description.instanceIds.join(", ")} and remove from replica pool (${description.replicaPoolName})."

    def project = description.credentials.project
    def zone = description.zone
    def replicaPoolName = description.replicaPoolName
    def abandonRequest = new InstanceGroupManagersAbandonInstancesRequest()
    abandonRequest.setInstances(description.instanceIds)

    def credentialBuilder = description.credentials.createCredentialBuilder(ReplicapoolScopes.COMPUTE)
    def replicapool = replicaPoolBuilder.buildReplicaPool(credentialBuilder, GCEUtil.APPLICATION_NAME);

    replicapool.instanceGroupManagers().abandonInstances(project, zone, replicaPoolName, abandonRequest).execute()
    task.updateStatus BASE_PHASE, "Successfully abandoned instances=(${description.instanceIds.join(", ")} and removed from ${replicaPoolName})."
    null
  }
}
