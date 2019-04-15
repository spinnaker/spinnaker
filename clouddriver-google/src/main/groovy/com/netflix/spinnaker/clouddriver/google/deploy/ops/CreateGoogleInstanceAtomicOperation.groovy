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

import com.google.api.services.compute.model.Instance
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult
import com.netflix.spinnaker.clouddriver.google.GoogleExecutorTraits
import com.netflix.spinnaker.clouddriver.google.deploy.description.BaseGoogleInstanceDescription
import com.netflix.spinnaker.config.GoogleConfiguration
import com.netflix.spinnaker.clouddriver.google.config.GoogleConfigurationProperties
import com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil
import com.netflix.spinnaker.clouddriver.google.deploy.SafeRetry
import com.netflix.spinnaker.clouddriver.google.deploy.description.CreateGoogleInstanceDescription
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleNetworkProvider
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleSubnetProvider
import org.springframework.beans.factory.annotation.Autowired

class CreateGoogleInstanceAtomicOperation extends GoogleAtomicOperation<DeploymentResult> {
  private static final String BASE_PHASE = "DEPLOY"

  // TODO(duftler): These should be exposed/configurable.
  private static final String DEFAULT_NETWORK_NAME = "default"
  private static final String accessConfigName = "External NAT"
  private static final String accessConfigType = "ONE_TO_ONE_NAT"

  @Autowired
  private GoogleConfigurationProperties googleConfigurationProperties

  @Autowired
  private GoogleConfiguration.DeployDefaults googleDeployDefaults

  @Autowired
  GoogleNetworkProvider googleNetworkProvider

  @Autowired
  GoogleSubnetProvider googleSubnetProvider

  @Autowired
  String clouddriverUserAgentApplicationName

  @Autowired
  SafeRetry safeRetry

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private final CreateGoogleInstanceDescription description

  CreateGoogleInstanceAtomicOperation(CreateGoogleInstanceDescription description) {
    this.description = description
  }

  /**
   * curl -X POST -H "Content-Type: application/json" -d '[ { "createGoogleInstanceDescription": { "instanceName": "myapp-dev-v000-abcd", "image": "ubuntu-1404-trusty-v20160509a", "instanceType": "f1-micro", "zone": "us-central1-f", "credentials": "my-account-name" }} ]' localhost:7002/ops
   */
  @Override
  DeploymentResult operate(List priorOutputs) {
    task.updateStatus BASE_PHASE, "Initializing creation of instance $description.instanceName in $description.zone..."

    if (!description.credentials) {
      throw new IllegalArgumentException("Unable to resolve credentials for Google account '${description.accountName}'.")
    }

    def accountName = description.accountName
    def credentials = description.credentials
    def compute = description.credentials.compute
    def project = description.credentials.project
    def zone = description.zone
    def region = credentials.regionFromZone(zone)
    def canIpForward = description.canIpForward

    def machineTypeName
    if (description.instanceType.startsWith('custom')) {
      machineTypeName = description.instanceType
    } else {
      machineTypeName = GCEUtil.queryMachineType(description.instanceType, zone, credentials, task, BASE_PHASE)
    }

    def network = GCEUtil.queryNetwork(accountName, description.network ?: DEFAULT_NETWORK_NAME, task, BASE_PHASE, googleNetworkProvider)

    def subnet =
      description.subnet ? GCEUtil.querySubnet(accountName, region, description.subnet, task, BASE_PHASE, googleSubnetProvider) : null

    task.updateStatus BASE_PHASE, "Composing instance..."

    description.baseDeviceName = description.instanceName

    def bootImage = GCEUtil.getBootImage(description,
      task,
      BASE_PHASE,
      clouddriverUserAgentApplicationName,
      googleConfigurationProperties.baseImageProjects,
      safeRetry,
      this)

    // We include a subset of the image's attributes and a reference in the disks.
    // Furthermore, we're using the underlying raw compute model classes
    // so we can't simply change the representation to support what we need for shielded VMs.
    def attachedDisks = GCEUtil.buildAttachedDisks(description,
                                                   zone,
                                                   true,
                                                   googleDeployDefaults,
                                                   task,
                                                   BASE_PHASE,
                                                   clouddriverUserAgentApplicationName,
                                                   googleConfigurationProperties.baseImageProjects,
                                                   bootImage,
                                                   safeRetry,
                                                   this)

    def networkInterface = GCEUtil.buildNetworkInterface(network,
                                                         subnet,
                                                         description.associatePublicIpAddress == null || description.associatePublicIpAddress,
                                                         accessConfigName,
                                                         accessConfigType)

    def metadata = GCEUtil.buildMetadataFromMap(description.instanceMetadata)

    def tags = GCEUtil.buildTagsFromList(description.tags)

    def serviceAccount = GCEUtil.buildServiceAccount(description.serviceAccountEmail, description.authScopes)

    def scheduling = GCEUtil.buildScheduling(description)

    def instance = new Instance(name: description.instanceName,
                                machineType: "zones/$zone/machineTypes/$machineTypeName",
                                disks: attachedDisks,
                                networkInterfaces: [networkInterface],
                                canIpForward: canIpForward,
                                metadata: metadata,
                                tags: tags,
                                labels: description.labels,
                                scheduling: scheduling,
                                serviceAccounts: serviceAccount)

    if (GCEUtil.isShieldedVmCompatible(bootImage)) {
      def shieldedVmConfig = GCEUtil.buildShieldedVmConfig(description)
      instance.setShieldedVmConfig(shieldedVmConfig)
    }

    if (description.minCpuPlatform) {
      instance.minCpuPlatform = description.minCpuPlatform
    }

    task.updateStatus BASE_PHASE, "Creating instance $description.instanceName..."
    timeExecute(compute.instances().insert(project, zone, instance),
        "compute.instances.insert",
        TAG_SCOPE, SCOPE_ZONAL, TAG_ZONE, zone)

    task.updateStatus BASE_PHASE, "Done creating instance $description.instanceName in $description.zone."
    new DeploymentResult(serverGroupNames: [description.instanceName])
  }
}
