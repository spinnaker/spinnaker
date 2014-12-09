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

import com.google.api.services.compute.model.InstanceReference
import com.google.api.services.compute.model.TargetPoolsAddInstanceRequest
import com.google.api.services.compute.model.TargetPoolsRemoveInstanceRequest
import com.google.api.services.replicapool.ReplicapoolScopes
import com.google.api.services.replicapool.model.InstanceGroupManagersSetTargetPoolsRequest
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskRepository
import com.netflix.spinnaker.kato.gce.deploy.GCEUtil
import com.netflix.spinnaker.kato.gce.deploy.description.EnableDisableGoogleServerGroupDescription
import com.netflix.spinnaker.kato.orchestration.AtomicOperation

abstract class AbstractEnableDisableAtomicOperation implements AtomicOperation<Void> {
  abstract boolean isDisable()

  abstract String getPhaseName()

  EnableDisableGoogleServerGroupDescription description
  ReplicaPoolBuilder replicaPoolBuilder
  ResourceViewsBuilder resourceViewsBuilder

  AbstractEnableDisableAtomicOperation(EnableDisableGoogleServerGroupDescription description,
                                       ReplicaPoolBuilder replicaPoolBuilder,
                                       ResourceViewsBuilder resourceViewsBuilder) {
    this.description = description
    this.replicaPoolBuilder = replicaPoolBuilder
    this.resourceViewsBuilder = resourceViewsBuilder
  }

  @Override
  Void operate(List priorOutputs) {
    String verb = disable ? 'Disable' : 'Enable'
    String presentParticipling = disable ? 'Disabling' : 'Enabling'

    task.updateStatus phaseName, "Initializing $verb Google Server Group operation for $description.replicaPoolName..."

    def credentials = description.credentials
    def compute = credentials.compute
    def project = credentials.project
    def zone = description.zone
    def region = GCEUtil.getRegionFromZone(project, zone, compute)
    def replicaPoolName = description.replicaPoolName
    def credentialBuilder = credentials.createCredentialBuilder(ReplicapoolScopes.COMPUTE)
    def replicapool = replicaPoolBuilder.buildReplicaPool(credentialBuilder, GCEUtil.APPLICATION_NAME);
    def managedInstanceGroup = GCEUtil.queryManagedInstanceGroup(project,
                                                                 zone,
                                                                 replicaPoolName,
                                                                 credentials,
                                                                 replicaPoolBuilder,
                                                                 GCEUtil.APPLICATION_NAME);
    def currentTargetPoolUrls = managedInstanceGroup.getTargetPools()
    def newTargetPoolUrls = []

    if (disable) {
      task.updateStatus phaseName, "Deregistering instances from load balancers..."

      currentTargetPoolUrls.each { targetPoolUrl ->
        def targetPoolLocalName = GCEUtil.getLocalName(targetPoolUrl)

        task.updateStatus phaseName, "Deregistering instances from $targetPoolLocalName..."

        def targetPool = compute.targetPools().get(project, region, targetPoolLocalName).execute()
        def instanceUrls = targetPool.getInstances()
        def instanceReferencesToRemove = []

        instanceUrls.each { instanceUrl ->
          def instanceLocalName = GCEUtil.getLocalName(instanceUrl)

          if (instanceLocalName.startsWith("$replicaPoolName-")) {
            instanceReferencesToRemove << new InstanceReference(instance: instanceUrl)
          }
        }

        if (instanceReferencesToRemove) {
          def targetPoolsRemoveInstanceRequest = new TargetPoolsRemoveInstanceRequest(instances: instanceReferencesToRemove)

          compute.targetPools().removeInstance(project,
                                               region,
                                               targetPoolLocalName,
                                               targetPoolsRemoveInstanceRequest).execute()
        }
      }
    } else {
      task.updateStatus phaseName, "Registering instances with load balancers..."

      def resourceViews = resourceViewsBuilder.buildResourceViews(credentialBuilder, GCEUtil.APPLICATION_NAME)

      def resourceItems = resourceViews.zoneViews().listResources(project,
                                                                  zone,
                                                                  replicaPoolName).execute().items

      def instanceReferencesToAdd = []

      resourceItems.each { resourceItem ->
        instanceReferencesToAdd << new InstanceReference(instance: resourceItem.resource)
      }

      def instanceTemplateUrl = managedInstanceGroup.getInstanceTemplate()
      def instanceTemplate = compute.instanceTemplates().get(project, GCEUtil.getLocalName(instanceTemplateUrl)).execute()
      def metadataItems = instanceTemplate?.properties?.metadata?.items
      def newForwardingRuleNames = []

      metadataItems.each { item ->
        if (item.key == 'load-balancer-names') {
          newForwardingRuleNames = item.value.split(",") as List
        }
      }

      def forwardingRules = GCEUtil.queryForwardingRules(project, region, newForwardingRuleNames, compute, task, phaseName)

      newTargetPoolUrls = forwardingRules.collect { forwardingRule ->
        forwardingRule.target
      }

      newTargetPoolUrls.each { newTargetPoolUrl ->
        def targetPoolLocalName = GCEUtil.getLocalName(newTargetPoolUrl)

        task.updateStatus phaseName, "Registering instances with $targetPoolLocalName..."

        if (instanceReferencesToAdd) {
          def targetPoolsAddInstanceRequest = new TargetPoolsAddInstanceRequest(instances: instanceReferencesToAdd)

          compute.targetPools().addInstance(project, region, targetPoolLocalName, targetPoolsAddInstanceRequest).execute()
        }
      }

      if (currentTargetPoolUrls) {
        newTargetPoolUrls = ((currentTargetPoolUrls as Set) + (newTargetPoolUrls as Set)) as List
      }
    }

    task.updateStatus phaseName, "$presentParticipling Google Server Group $description.replicaPoolName in $zone..."

    def instanceGroupManagersSetTargetPoolsRequest =
            new InstanceGroupManagersSetTargetPoolsRequest(targetPools: newTargetPoolUrls)

    instanceGroupManagersSetTargetPoolsRequest.setFingerprint(managedInstanceGroup.getFingerprint())

    replicapool.instanceGroupManagers().setTargetPools(project,
                                                       zone,
                                                       replicaPoolName,
                                                       instanceGroupManagersSetTargetPoolsRequest).execute()

    null
  }

  Task getTask() {
    TaskRepository.threadLocalTask.get()
  }
}
