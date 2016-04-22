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

import com.google.api.services.compute.model.AttachedDisk
import com.google.api.services.compute.model.Autoscaler
import com.google.api.services.compute.model.InstanceGroupManager
import com.google.api.services.compute.model.InstanceProperties
import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult
import com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil
import com.netflix.spinnaker.clouddriver.google.deploy.description.BasicGoogleDeployDescription
import com.netflix.spinnaker.clouddriver.google.deploy.handlers.BasicGoogleDeployHandler
import com.netflix.spinnaker.clouddriver.google.model.GoogleDisk
import com.netflix.spinnaker.clouddriver.google.model.GoogleSecurityGroup
import com.netflix.spinnaker.clouddriver.google.model.callbacks.Utils
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleSecurityGroupProvider
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import org.springframework.beans.factory.annotation.Autowired

class CopyLastGoogleServerGroupAtomicOperation implements AtomicOperation<DeploymentResult> {
  private static final String BASE_PHASE = "COPY_LAST_SERVER_GROUP"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private final BasicGoogleDeployDescription description

  @Autowired
  BasicGoogleDeployHandler basicGoogleDeployHandler

  @Autowired
  GoogleSecurityGroupProvider googleSecurityGroupProvider

  CopyLastGoogleServerGroupAtomicOperation(BasicGoogleDeployDescription description) {
    this.description = description
  }

  /**
   * curl -X POST -H "Content-Type: application/json" -d '[ { "cloneServerGroup": { "source": { "region": "us-central1", "serverGroupName": "myapp-dev-v000" }, "credentials": "my-account-name" }} ]' localhost:7002/gce/ops
   * curl -X POST -H "Content-Type: application/json" -d '[ { "cloneServerGroup": { "source": { "region": "us-central1", "serverGroupName": "myapp-dev-v000" }, "application": "myapp", "stack": "dev", "image": "ubuntu-1410-utopic-v20150625", "targetSize": 4, "instanceType": "g1-small", "zone": "us-central1-f", "credentials": "my-account-name" }} ]' localhost:7002/gce/ops
   */
  @Override
  DeploymentResult operate(List priorOutputs) {
    BasicGoogleDeployDescription newDescription = cloneAndOverrideDescription()

    task.updateStatus BASE_PHASE, "Initializing copy of server group for " +
      "${newDescription.application}-${newDescription.stack} in $newDescription.zone..."

    def result = basicGoogleDeployHandler.handle(newDescription, priorOutputs)
    def newServerGroupName = getServerGroupName(result?.serverGroupNames?.getAt(0))

    task.updateStatus BASE_PHASE, "Finished copying server group for " +
                                  "${newDescription.application}-${newDescription.stack}. " +
                                  "New server group = $newServerGroupName in $newDescription.zone."

    result
  }

  private BasicGoogleDeployDescription cloneAndOverrideDescription() {
    BasicGoogleDeployDescription newDescription = description.clone()

    if (!description?.source?.region || !description?.source?.serverGroupName) {
      return newDescription
    }

    task.updateStatus BASE_PHASE, "Initializing copy of server group $description.source.serverGroupName..."

    // Locate the ancestor server group.
    // TODO(duftler): Replace this with a call to GoogleClusterProvider.
    InstanceGroupManager ancestorServerGroup = GCEUtil.queryManagedInstanceGroupInRegion(description.credentials.project,
                                                                                         description.source.region,
                                                                                         description.source.serverGroupName,
                                                                                         description.credentials)

    if (!ancestorServerGroup) {
      return newDescription
    }

    def ancestorNames = Names.parseName(ancestorServerGroup.name)

    // Override any ancestor values that were specified directly on the copyLastGoogleServerGroupDescription call.
    newDescription.zone = description.zone ?: Utils.getLocalName(ancestorServerGroup.getZone())
    newDescription.loadBalancers =
        description.loadBalancers != null
        ? description.loadBalancers
        : GCEUtil.deriveNetworkLoadBalancerNamesFromTargetPoolUrls(ancestorServerGroup.getTargetPools())
    newDescription.application = description.application ?: ancestorNames.app
    newDescription.stack = description.stack ?: ancestorNames.stack
    newDescription.freeFormDetails = description.freeFormDetails ?: ancestorNames.detail
    newDescription.targetSize =
        description.targetSize != null
        ? description.targetSize
        : ancestorServerGroup.targetSize

    def project = description.credentials.project
    def compute = description.credentials.compute
    def accountName = description.accountName
    def ancestorInstanceTemplate =
        GCEUtil.queryInstanceTemplate(project, GCEUtil.getLocalName(ancestorServerGroup.instanceTemplate), compute)

    if (ancestorInstanceTemplate) {
      // Override any ancestor values that were specified directly on the call.
      InstanceProperties ancestorInstanceProperties = ancestorInstanceTemplate.properties

      newDescription.instanceType = description.instanceType ?: ancestorInstanceProperties.machineType

      List<AttachedDisk> attachedDisks = ancestorInstanceProperties?.disks

      if (attachedDisks) {
        def bootDisk = attachedDisks.find { it.getBoot() }

        newDescription.image = description.image ?: GCEUtil.getLocalName(bootDisk.initializeParams.sourceImage)
        newDescription.disks = description.disks ?: attachedDisks.collect { attachedDisk ->
          def initializeParams = attachedDisk.initializeParams

          new GoogleDisk(type: initializeParams.diskType,
                         sizeGb: initializeParams.diskSizeGb,
                         autoDelete: attachedDisk.autoDelete)
        }
      }

      def instanceMetadata = ancestorInstanceProperties.metadata

      if (instanceMetadata) {
        newDescription.instanceMetadata =
            description.instanceMetadata != null
            ? description.instanceMetadata
            : GCEUtil.buildMapFromMetadata(instanceMetadata)
      }

      def tags = ancestorInstanceProperties.tags

      if (tags != null) {
        newDescription.tags = description.tags != null ? description.tags : tags.items
      }

      def scheduling = ancestorInstanceProperties.scheduling

      if (scheduling) {
        newDescription.preemptible =
            description.preemptible != null
            ? description.preemptible
            : scheduling.preemptible
        newDescription.automaticRestart =
            description.automaticRestart != null
            ? description.automaticRestart
            : scheduling.automaticRestart
        newDescription.onHostMaintenance =
            description.onHostMaintenance != null
            ? description.onHostMaintenance
            : scheduling.onHostMaintenance
      }

      newDescription.authScopes =
          description.authScopes != null
          ? description.authScopes
          : GCEUtil.retrieveScopesFromDefaultServiceAccount(ancestorInstanceProperties.serviceAccounts)

      newDescription.network =
        GCEUtil.getLocalName(description.network ?: ancestorInstanceProperties.networkInterfaces?.getAt(0)?.network)

      newDescription.subnet =
          description.subnet != null
          ? description.subnet
          : GCEUtil.getLocalName(ancestorInstanceProperties.networkInterfaces?.getAt(0)?.subnetwork)

      Set<GoogleSecurityGroup> googleSecurityGroups = googleSecurityGroupProvider.getAllByAccount(false, accountName)

      // Find all firewall rules with target tags matching the tags of the ancestor instance template.
      def googleSecurityGroupMatches = [] as Set

      ancestorInstanceTemplate?.properties?.tags?.items.each { instanceTemplateTag ->
        googleSecurityGroupMatches << googleSecurityGroups.findAll { googleSecurityGroup ->
          googleSecurityGroup.targetTags?.contains(instanceTemplateTag)
        }
      }

      Set<GoogleSecurityGroup> ancestorSecurityGroups = googleSecurityGroupMatches.flatten().collect { it.name }

      if (newDescription.securityGroups == null) {
        // Since no security groups were specified, use the security groups of the ancestor server group.
        newDescription.securityGroups = ancestorSecurityGroups
      } else {
        // Since security groups were specified, we must back out the tags of the security groups that are associated
        // with the ancestor server group but not with the cloned server group.
        if (newDescription.tags) {
          Set<String> elidedSecurityGroupNames = ancestorSecurityGroups - newDescription.securityGroups

          if (elidedSecurityGroupNames) {
            Set<String> elidedSecurityGroupsTargetTags = GCEUtil.querySecurityGroupTags(elidedSecurityGroupNames,
                                                                                        newDescription.accountName,
                                                                                        googleSecurityGroupProvider,
                                                                                        task,
                                                                                        BASE_PHASE)

            newDescription.tags -= elidedSecurityGroupsTargetTags
          }
        }
      }
    }

    Autoscaler ancestorAutoscaler = GCEUtil.queryZonalAutoscaler(project,
                                                                 Utils.getLocalName(ancestorServerGroup.zone),
                                                                 ancestorServerGroup.name,
                                                                 description.credentials)
    BasicGoogleDeployDescription.AutoscalingPolicy ancestorAutoscalingPolicy =
        ancestorAutoscaler ? GCEUtil.buildAutoscalingPolicyDescriptionFromAutoscalingPolicy(ancestorAutoscaler) : null

    newDescription.autoscalingPolicy = description.autoscalingPolicy ?: ancestorAutoscalingPolicy

    return newDescription
  }

  private static String getServerGroupName(String regionPlusServerGroupName) {
    if (!regionPlusServerGroupName) {
      return 'Unknown'
    }

    def nameParts = regionPlusServerGroupName.split(":")

    return nameParts[nameParts.length - 1]
  }
}
