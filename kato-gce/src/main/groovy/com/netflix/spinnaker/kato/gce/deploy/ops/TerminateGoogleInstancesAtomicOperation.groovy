/*
 * Copyright 2014 Netflix, Inc.
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
import com.netflix.spinnaker.kato.gce.deploy.description.TerminateGoogleInstancesDescription
import com.netflix.spinnaker.kato.orchestration.AtomicOperation

/**
 * Terminate and delete an instance.
 *
 * This is an alternative to {@link RecreateGoogleReplicaPoolInstancesAtomicOperation} using
 * the API described in {@link https://cloud.google.com/compute/docs/instances#deleting_an_instance}
 * and {@link https://cloud.google.com/compute/docs/reference/latest/instances/delete}.
 *
 * This operation only explicitly deletes and removes an instance. However, if the instance is in a
 * replica pool then the replica pool will automatically recreate and restart the instance once it sees
 * that it is missing. The net effect is to recreate the instance (if it was in a replica pool).
 *
 * If the intention is in fact to recreate the instance, consider {@link RecreateGoogleReplicaPoolInstancesAtomicOperation},
 *
 * @see RecreateGoogleReplicaPoolInstancesAtomicOperation
 */
class TerminateGoogleInstancesAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "TERMINATE_INSTANCES"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private final TerminateGoogleInstancesDescription description

  TerminateGoogleInstancesAtomicOperation(TerminateGoogleInstancesDescription description) {
    this.description = description
  }

  /**
   * curl -X POST -H "Content-Type: application/json" -d '[ { "terminateGoogleInstancesDescription": { "instanceIds": ["myapp-dev-v000-abcd"], "zone": "us-central1-b", "credentials": "my-account-name" }} ]' localhost:8501/ops
   */
  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing termination of instances (${description.instanceIds.join(", ")})."

    def compute = description.credentials.compute
    def project = description.credentials.project
    def zone = description.zone

    // TODO(duftler): Should we attempt to terminate each instance, even in the case of individual failures?
    for (def instanceId : description.instanceIds) {
      task.updateStatus BASE_PHASE, "Attempting termination of instance ${instanceId}..."

      compute.instances().delete(project, zone, instanceId).execute()
    }

    task.updateStatus BASE_PHASE, "Done executing termination of instances (${description.instanceIds.join(", ")})."
    null
  }
}
