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

import com.google.api.services.compute.model.AttachedDisk
import com.google.api.services.compute.model.InstanceProperties
import com.google.api.services.replicapool.model.InstanceGroupManager
import com.netflix.frigga.Names
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.deploy.DeploymentResult
import com.netflix.spinnaker.kato.gce.deploy.GCEUtil
import com.netflix.spinnaker.kato.gce.deploy.description.BasicGoogleDeployDescription
import com.netflix.spinnaker.kato.gce.deploy.handlers.BasicGoogleDeployHandler
import com.netflix.spinnaker.kato.orchestration.AtomicOperation
import org.springframework.beans.factory.annotation.Autowired

class CopyLastGoogleServerGroupAtomicOperation implements AtomicOperation<DeploymentResult> {
  private static final String BASE_PHASE = "COPY_LAST_SERVER_GROUP"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private final BasicGoogleDeployDescription description
  private final ReplicaPoolBuilder replicaPoolBuilder

  @Autowired
  BasicGoogleDeployHandler basicGoogleDeployHandler

  CopyLastGoogleServerGroupAtomicOperation(BasicGoogleDeployDescription description,
                                           ReplicaPoolBuilder replicaPoolBuilder) {
    this.description = description
    this.replicaPoolBuilder = replicaPoolBuilder
  }

  /**
   * curl -X POST -H "Content-Type: application/json" -d '[ { "copyLastGoogleServerGroupDescription": { "source": { "zone": "us-central1-b", "serverGroupName": "myapp-dev-v000" }, "credentials": "my-account-name" }} ]' localhost:8501/ops
   * curl -X POST -H "Content-Type: application/json" -d '[ { "copyLastGoogleServerGroupDescription": { "source": { "zone": "us-central1-b", "serverGroupName": "myapp-dev-v000" }, "application": "myapp", "stack": "dev", "image": "debian-7-wheezy-v20141108", "initialNumReplicas": 4, "instanceType": "g1-small", "zone": "us-central1-a", "credentials": "my-account-name" }} ]' localhost:8501/ops
   */
  @Override
  DeploymentResult operate(List priorOutputs) {
    def BasicGoogleDeployDescription newDescription = description.clone()

    if (description?.source?.zone && description?.source?.serverGroupName) {
      task.updateStatus BASE_PHASE, "Initializing copy of server group $description.source.serverGroupName..."

      // Locate the ancestor server group.
      InstanceGroupManager ancestorServerGroup = GCEUtil.queryManagedInstanceGroup(description.credentials.project,
                                                                                   description.source.zone,
                                                                                   description.source.serverGroupName,
                                                                                   description.credentials,
                                                                                   replicaPoolBuilder,
                                                                                   GCEUtil.APPLICATION_NAME)

      if (ancestorServerGroup) {
        def ancestorNames = Names.parseName(ancestorServerGroup.name)

        // Override any ancestor values that were specified directly on the call.
        newDescription.zone = description.zone ?: description.source.zone
        newDescription.networkLoadBalancers =
                description.networkLoadBalancers != null
                ? description.networkLoadBalancers
                : GCEUtil.deriveNetworkLoadBalancerNamesFromTargetPoolUrls(ancestorServerGroup.getTargetPools())
        newDescription.application = description.application ?: ancestorNames.app
        newDescription.stack = description.stack ?: ancestorNames.stack
        newDescription.freeFormDetails = description.freeFormDetails ?: ancestorNames.detail
        newDescription.initialNumReplicas = description.initialNumReplicas ?: ancestorServerGroup.targetSize

        def project = description.credentials.project
        def compute = description.credentials.compute
        def ancestorInstanceTemplate =
                GCEUtil.queryInstanceTemplate(project, GCEUtil.getLocalName(ancestorServerGroup.instanceTemplate), compute)

        if (ancestorInstanceTemplate) {
          // Override any ancestor values that were specified directly on the call.
          InstanceProperties ancestorInstanceProperties = ancestorInstanceTemplate.properties

          newDescription.instanceType = description.instanceType ?: ancestorInstanceProperties.machineType

          List<AttachedDisk> attachedDisks = ancestorInstanceProperties?.disks

          if (attachedDisks) {
            newDescription.image = description.image ?: GCEUtil.getLocalName(attachedDisks[0].initializeParams.sourceImage)
            newDescription.diskType = description.diskType ?: GCEUtil.getLocalName(attachedDisks[0].initializeParams.diskType)
            newDescription.diskSizeGb = description.diskSizeGb ?: attachedDisks[0].initializeParams.diskSizeGb
          }

          def instanceMetadata = ancestorInstanceProperties.metadata

          if (instanceMetadata) {
            newDescription.instanceMetadata =
                    description.instanceMetadata != null
                    ? description.instanceMetadata
                    : GCEUtil.buildMapFromMetadata(instanceMetadata)
          }
        }
      }
    }

    def result = basicGoogleDeployHandler.handle(newDescription, priorOutputs)
    def newServerGroupName = getServerGroupName(result?.serverGroupNames?.getAt(0))

    task.updateStatus BASE_PHASE, "Finished copying server group for " +
                                  "${newDescription.application}-${newDescription.stack}. " +
                                  "New server group = $newServerGroupName in zone $newDescription.zone."

    result
  }

  private static String getServerGroupName(String regionPlusServerGroupName) {
    if (!regionPlusServerGroupName) {
      return 'Unknown'
    }

    def nameParts = regionPlusServerGroupName.split(":")

    return nameParts[nameParts.length - 1]
  }
}
