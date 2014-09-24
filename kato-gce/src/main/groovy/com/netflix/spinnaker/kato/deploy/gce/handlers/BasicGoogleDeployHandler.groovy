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

import com.google.api.services.compute.Compute
import com.google.api.services.compute.model.AccessConfig
import com.google.api.services.compute.model.AttachedDisk
import com.google.api.services.compute.model.AttachedDiskInitializeParams
import com.google.api.services.compute.model.Instance
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.deploy.DeployDescription
import com.netflix.spinnaker.kato.deploy.DeployHandler
import com.netflix.spinnaker.kato.deploy.DeploymentResult
import com.netflix.spinnaker.kato.deploy.gce.description.BasicGoogleDeployDescription
import com.netflix.spinnaker.kato.deploy.gce.GCEUtil
import org.springframework.stereotype.Component

@Component
class BasicGoogleDeployHandler implements DeployHandler<BasicGoogleDeployDescription> {
  private static final String BASE_PHASE = "DEPLOY"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Override
  boolean handles(DeployDescription description) {
    description instanceof BasicGoogleDeployDescription
  }

  /**
   * curl -X POST -H "Content-Type: application/json" -d '[ { "basicGoogleDeployDescription": { "application": "front50", "stack": "dev", "image": "debian-7-wheezy-v20140415", "type": "f1-micro", "zone": "us-central1-b", "credentials": "gce-test" }} ]' localhost:8501/ops
   *
   * @param description
   * @param priorOutputs
   * @return
   */
  @Override
  DeploymentResult handle(BasicGoogleDeployDescription description, List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing deployment..."

    if (!description.credentials) {
      throw new IllegalArgumentException("Unable to resolve credentials for Google account '${description.accountName}'.")
    }

    def compute = description.credentials.compute
    def project = description.credentials.project
    def zone = description.zone

    def machineType = GCEUtil.queryMachineType(project, zone, description.type, compute, task, BASE_PHASE)

    def sourceImage = GCEUtil.querySourceImage(project, description.image, compute, task, BASE_PHASE)

    def network = GCEUtil.queryNetwork(project, "default", compute, task, BASE_PHASE)

    task.updateStatus BASE_PHASE, "Composing instance..."
    def rootDrive = GCEUtil.buildAttachedDisk(sourceImage, "PERSISTENT")

    def networkInterface = GCEUtil.buildNetworkInterface(network, "ONE_TO_ONE_NAT")

    def clusterName = "${description.application}-${description.stack}"
    task.updateStatus BASE_PHASE, "Looking up next sequence..."
    def nextSequence = getNextSequence(clusterName, project, zone, compute)
    task.updateStatus BASE_PHASE, "Found next sequence ${nextSequence}."
    def instanceName = "${clusterName}-v${nextSequence}-instance1".toString()
    task.updateStatus BASE_PHASE, "Produced instance name: $instanceName"

    def instance = new Instance(name: instanceName, machineType: machineType.getSelfLink(), disks: [rootDrive], networkInterfaces: [networkInterface])

    task.updateStatus BASE_PHASE, "Creating instance $instanceName..."
    compute.instances().insert(project, zone, instance).execute()
    task.updateStatus BASE_PHASE, "Done."
    new DeploymentResult(serverGroupNames: ["${clusterName}-v${nextSequence}".toString()])
  }

  static def getNextSequence(String clusterName, String project, String zone, Compute compute) {
    def instance = compute.instances().list(project, zone).execute().getItems().find {
      def parts = it.getName().split('-')
      def cluster = "${parts[0]}-${parts[1]}"
      cluster == clusterName
    }
    if (instance) {
      def parts = instance.getName().split('-')
      def seq = Integer.valueOf(parts[2].replaceAll("v", ""))
      String.format("%03d", ++seq)
    } else {
      "000"
    }
  }
}
