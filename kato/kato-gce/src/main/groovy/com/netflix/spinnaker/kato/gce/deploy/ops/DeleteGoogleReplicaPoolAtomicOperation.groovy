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

package com.netflix.spinnaker.kato.gce.deploy.ops

import com.google.api.services.replicapool.ReplicapoolScopes
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.gce.deploy.GoogleOperationPoller
import com.netflix.spinnaker.kato.gce.deploy.GCEUtil
import com.netflix.spinnaker.kato.gce.deploy.description.DeleteGoogleReplicaPoolDescription
import com.netflix.spinnaker.kato.orchestration.AtomicOperation
import org.springframework.beans.factory.annotation.Autowired

class DeleteGoogleReplicaPoolAtomicOperation implements AtomicOperation<Void> {
  // TODO(duftler): This should move to a common location.
  private static final String BASE_PHASE = "DELETE_REPLICA_POOL"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Autowired
  private GoogleOperationPoller googleOperationPoller

  private final DeleteGoogleReplicaPoolDescription description
  private final ReplicaPoolBuilder replicaPoolBuilder

  DeleteGoogleReplicaPoolAtomicOperation(DeleteGoogleReplicaPoolDescription description,
                                         ReplicaPoolBuilder replicaPoolBuilder) {
    this.description = description
    this.replicaPoolBuilder = replicaPoolBuilder
  }

  /**
   * curl -X POST -H "Content-Type: application/json" -d '[ { "deleteGoogleReplicaPoolDescription": { "replicaPoolName": "myapp-dev-v000", "zone": "us-central1-b", "credentials": "my-account-name" }} ]' localhost:8501/ops
   */
  @Override
  Void operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing delete of replica pool $description.replicaPoolName in $description.zone..."

    def compute = description.credentials.compute
    def project = description.credentials.project
    def zone = description.zone
    def replicaPoolName = description.replicaPoolName

    def credentialBuilder = description.credentials.createCredentialBuilder(ReplicapoolScopes.COMPUTE)
    def replicapool = replicaPoolBuilder.buildReplicaPool(credentialBuilder, GCEUtil.APPLICATION_NAME);

    def instanceGroupManager = replicapool.instanceGroupManagers().get(project, zone, replicaPoolName).execute()

    // We create a new instance template for each managed instance group. We need to delete it here.
    def instanceTemplateName = getLocalName(instanceGroupManager.instanceTemplate)

    task.updateStatus BASE_PHASE, "Identified instance template."

    def instanceGroupManagerDeleteOperation =
        replicapool.instanceGroupManagers().delete(project, zone, replicaPoolName).execute()
    def instanceGroupOperationName = instanceGroupManagerDeleteOperation.getName()

    task.updateStatus BASE_PHASE, "Waiting on delete operation for managed instance group."

    // We must make sure the managed instance group is deleted before deleting the instance template.
    googleOperationPoller.waitForZoneOperation(replicapool, project, zone, instanceGroupOperationName, null, task,
        "instance group $replicaPoolName", BASE_PHASE)

    task.updateStatus BASE_PHASE, "Deleted instance group."

    compute.instanceTemplates().delete(project, instanceTemplateName).execute()

    task.updateStatus BASE_PHASE, "Deleted instance template."

    task.updateStatus BASE_PHASE, "Done deleting replica pool $replicaPoolName in $zone."
    null
  }

  private static String getLocalName(String fullUrl) {
    int lastIndex = fullUrl.lastIndexOf('/')

    return lastIndex != -1 ? fullUrl.substring(lastIndex + 1) : fullUrl
  }
}
