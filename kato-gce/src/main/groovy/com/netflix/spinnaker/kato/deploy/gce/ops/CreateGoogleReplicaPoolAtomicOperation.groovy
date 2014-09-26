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

package com.netflix.spinnaker.kato.deploy.gce.ops

import com.google.api.services.replicapool.model.Pool
import com.google.api.services.replicapool.model.Template
import com.google.api.services.replicapool.model.VmParams
import com.google.api.services.replicapool.ReplicapoolScopes
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.deploy.gce.description.CreateGoogleReplicaPoolDescription
import com.netflix.spinnaker.kato.deploy.gce.GCEUtil
import com.netflix.spinnaker.kato.orchestration.AtomicOperation

class CreateGoogleReplicaPoolAtomicOperation implements AtomicOperation<Void> {
  // TODO(duftler): This should move to a common location.
  private static final String APPLICATION_NAME = "Spinnaker"
  private static final String BASE_PHASE = "CREATE_REPLICA_POOL"

  // TODO(duftler): These should be exposed/configurable.
  private static final long diskSizeGb = 100
  private static final String networkName = "default"
  private static final String accessConfigName = "External NAT"
  private static final String accessConfigType = "ONE_TO_ONE_NAT"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private final CreateGoogleReplicaPoolDescription description
  private final ReplicaPoolBuilder replicaPoolBuilder

  CreateGoogleReplicaPoolAtomicOperation(CreateGoogleReplicaPoolDescription description,
                                         ReplicaPoolBuilder replicaPoolBuilder) {
    this.description = description
    this.replicaPoolBuilder = replicaPoolBuilder
  }

  @Override
  Void operate(List priorOutputs) {
    // TODO(duftler): Implement proper sequential naming and tests for same. Best done when this class is reconciled
    // with logic in BasicGoogleDeployHandler.
    def replicaPoolName = "${description.application}-${description.stack}-v000"

    task.updateStatus BASE_PHASE, "Initializing creation of replica pool $replicaPoolName..."

    def compute = description.credentials.compute
    def project = description.credentials.project

    def sourceImage = GCEUtil.querySourceImage(project, description.image, compute, task, BASE_PHASE)

    task.updateStatus BASE_PHASE, "Composing replica pool $replicaPoolName..."

    def newDisk = GCEUtil.buildNewDisk(sourceImage, diskSizeGb)

    def networkInterface = GCEUtil.buildNetworkInterface(networkName, accessConfigName, accessConfigType)

    def vmParams = new VmParams(machineType: description.type,
                                disksToCreate: [newDisk],
                                networkInterfaces: [networkInterface])

    def template = new Template(vmParams: vmParams)

    def credentialBuilder = description.credentials.createCredentialBuilder(ReplicapoolScopes.REPLICAPOOL)

    def replicapool = replicaPoolBuilder.buildReplicaPool(credentialBuilder, APPLICATION_NAME);

    replicapool.pools().insert(project,
                               description.zone,
                               new Pool(name: replicaPoolName,
                                        initialNumReplicas: description.initialNumReplicas,
                                        template: template)).execute()

    task.updateStatus BASE_PHASE, "Done creating replica pool $replicaPoolName."
    null
  }
}
