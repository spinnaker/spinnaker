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

package com.netflix.spinnaker.kato.deploy.gce.handlers

import com.google.api.services.replicapool.ReplicapoolScopes
import com.google.api.services.replicapool.model.Pool
import com.google.api.services.replicapool.model.Template
import com.google.api.services.replicapool.model.VmParams
import com.netflix.frigga.Names
import com.netflix.frigga.NameValidation
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.deploy.DeployDescription
import com.netflix.spinnaker.kato.deploy.DeployHandler
import com.netflix.spinnaker.kato.deploy.DeploymentResult
import com.netflix.spinnaker.kato.deploy.gce.description.BasicGoogleDeployDescription
import com.netflix.spinnaker.kato.deploy.gce.GCEUtil
import com.netflix.spinnaker.kato.deploy.gce.ops.ReplicaPoolBuilder
import com.netflix.spinnaker.kato.security.gce.GoogleCredentials
import org.springframework.stereotype.Component

@Component
class BasicGoogleDeployHandler implements DeployHandler<BasicGoogleDeployDescription> {
  // TODO(duftler): This should move to a common location.
  private static final String APPLICATION_NAME = "Spinnaker"
  private static final String BASE_PHASE = "DEPLOY"

  // TODO(duftler): These should be exposed/configurable.
  private static final long diskSizeGb = 100
  private static final String networkName = "default"
  private static final String accessConfigName = "External NAT"
  private static final String accessConfigType = "ONE_TO_ONE_NAT"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  ReplicaPoolBuilder replicaPoolBuilder

  BasicGoogleDeployHandler() {
    replicaPoolBuilder = new ReplicaPoolBuilder()
  }

  @Override
  boolean handles(DeployDescription description) {
    description instanceof BasicGoogleDeployDescription
  }

  /**
   * curl -X POST -H "Content-Type: application/json" -d '[ { "basicGoogleDeployDescription": { "application": "front50", "stack": "dev", "image": "debian-7-wheezy-v20140415", "initialNumReplicas": 3, "type": "f1-micro", "zone": "us-central1-b", "credentials": "gce-test" }} ]' localhost:8501/ops
   * curl -X POST -H "Content-Type: application/json" -d '[ { "basicGoogleDeployDescription": { "application": "front50", "stack": "dev", "freeFormDetails": "something", "image": "debian-7-wheezy-v20140415", "initialNumReplicas": 3, "type": "f1-micro", "zone": "us-central1-b", "credentials": "gce-test" }} ]' localhost:8501/ops
   *
   * @param description
   * @param priorOutputs
   * @return
   */
  @Override
  DeploymentResult handle(BasicGoogleDeployDescription description, List priorOutputs) {
    def clusterName = combineAppStackDetail(description.application, description.stack, description.freeFormDetails)

    task.updateStatus BASE_PHASE, "Initializing creation of server group for cluster $clusterName..."

    def compute = description.credentials.compute
    def project = description.credentials.project
    def zone = description.zone

    task.updateStatus BASE_PHASE, "Looking up next sequence..."

    def nextSequence = getNextSequence(clusterName, project, zone, description.credentials, replicaPoolBuilder)
    task.updateStatus BASE_PHASE, "Found next sequence ${nextSequence}."

    def serverGroupName = "${clusterName}-v${nextSequence}".toString()
    task.updateStatus BASE_PHASE, "Produced server group name: $serverGroupName"

    def sourceImage = GCEUtil.querySourceImage(project, description.image, compute, task, BASE_PHASE)

    task.updateStatus BASE_PHASE, "Composing server group $serverGroupName..."

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
                               new Pool(name: serverGroupName,
                                        initialNumReplicas: description.initialNumReplicas,
                                        template: template)).execute()

    task.updateStatus BASE_PHASE, "Done creating server group $serverGroupName."
    new DeploymentResult(serverGroupNames: [serverGroupName.toString()])
  }

  static def getNextSequence(String clusterName,
                             String project,
                             String zone,
                             GoogleCredentials credentials,
                             ReplicaPoolBuilder replicaPoolBuilder) {
    def maxSeqNumber = -1

    for (def replicaPool : GCEUtil.queryReplicaPools(project, zone, credentials, replicaPoolBuilder, APPLICATION_NAME)) {
      def names = Names.parseName(replicaPool.getName())

      if (names.cluster == clusterName) {
        maxSeqNumber = Math.max(maxSeqNumber, names.sequence)
      }
    }

    String.format("%03d", ++maxSeqNumber)
  }

  static def combineAppStackDetail(String appName, String stack, String detail) {
    NameValidation.notEmpty(appName, "appName");

    // Use empty strings, not null references that output "null"
    stack = stack != null ? stack : "";

    if (detail != null && !detail.isEmpty()) {
      return appName + "-" + stack + "-" + detail;
    }

    if (!stack.isEmpty()) {
      return appName + "-" + stack;
    }

    return appName;
  }
}
