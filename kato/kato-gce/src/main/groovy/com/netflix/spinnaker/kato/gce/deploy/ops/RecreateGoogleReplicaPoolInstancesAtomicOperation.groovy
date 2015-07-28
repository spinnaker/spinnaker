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

import com.google.api.services.replicapool.model.InstanceGroupManagersRecreateInstancesRequest
import com.google.api.services.replicapool.Replicapool.InstanceGroupManagers
import com.google.api.services.replicapool.ReplicapoolScopes
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.gce.deploy.GCEUtil
import com.netflix.spinnaker.kato.gce.deploy.description.RecreateGoogleReplicaPoolInstancesDescription
import com.netflix.spinnaker.kato.orchestration.AtomicOperation


/**
 * Terminate and re-create instances in a managed instance group.
 *
 * This is an alternative to {@link TerminateGoogleInstancesAtomicOperation} using the API
 * described in {@link https://cloud.google.com/compute/docs/instance-groups/manager/#recreating_instances}
 * and {@link https://cloud.google.com/compute/docs/instance-groups/manager/v1beta2/instanceGroupManagers/recreateInstances}.
 *
 * This is a first-class explicit operation on a managed instance group where-as the Terminate
 * alternative operates on any instance and relies on the side effect of an external manager to
 * restart the instance when it sees that it has been deleted. The net effect is the same,
 * but this way is more clear and the intent is explicit so should be 'safer' longer term.
 *
 * @see TerminateGoogleInstancesAtomicOperation
 */
class RecreateGoogleReplicaPoolInstancesAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "RECREATE_INSTANCES"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private final RecreateGoogleReplicaPoolInstancesDescription description
  private final ReplicaPoolBuilder replicaPoolBuilder

  RecreateGoogleReplicaPoolInstancesAtomicOperation(RecreateGoogleReplicaPoolInstancesDescription description,
                                                    ReplicaPoolBuilder replicaPoolBuilder) {
    this.description = description
    this.replicaPoolBuilder = replicaPoolBuilder
  }

  /**
   * curl -X POST -H "Content-Type: application/json" -d '[ { "recreateGoogleReplicaPoolInstancesDescription": { "replicaPoolName": "myapp-dev-v000", "instanceIds": ["myapp-dev-v000-abcd"], "zone": "us-central1-f", "credentials": "my-account-name" }} ]' localhost:7002/ops
   */
  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing recreate of instances (${description.instanceIds.join(", ")})."

    def instanceIds = description.instanceIds
    def project = description.credentials.project
    def zone = description.zone
    def managerName = description.replicaPoolName

    def credentialBuilder = description.credentials.createCredentialBuilder(ReplicapoolScopes.COMPUTE)
    def replicapool = replicaPoolBuilder.buildReplicaPool(credentialBuilder, GCEUtil.APPLICATION_NAME)
    def instanceGroupManagers = replicapool.instanceGroupManagers()

    def request = new InstanceGroupManagersRecreateInstancesRequest().setInstances(instanceIds)
    instanceGroupManagers.recreateInstances(project, zone, managerName, request).execute()

    task.updateStatus BASE_PHASE, "Done executing recreate instances (${description.instanceIds.join(", ")})."
    null
  }
}
