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

import com.google.api.services.compute.model.InstanceGroupManagersSetTargetPoolsRequest
import com.google.api.services.compute.model.InstanceGroupsListInstancesRequest
import com.google.api.services.compute.model.InstanceReference
import com.google.api.services.compute.model.TargetPoolsAddInstanceRequest
import com.google.api.services.compute.model.TargetPoolsRemoveInstanceRequest
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil
import com.netflix.spinnaker.clouddriver.google.deploy.description.EnableDisableGoogleServerGroupDescription
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleClusterProvider
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import org.springframework.beans.factory.annotation.Autowired

abstract class AbstractEnableDisableAtomicOperation implements AtomicOperation<Void> {
  abstract boolean isDisable()

  abstract String getPhaseName()

  EnableDisableGoogleServerGroupDescription description

  @Autowired
  GoogleClusterProvider googleClusterProvider

  AbstractEnableDisableAtomicOperation(EnableDisableGoogleServerGroupDescription description) {
    this.description = description
  }

  @Override
  Void operate(List priorOutputs) {
    String verb = disable ? 'disable' : 'enable'
    String presentParticipling = disable ? 'Disabling' : 'Enabling'

    task.updateStatus phaseName, "Initializing $verb server group operation for $description.serverGroupName in " +
      "$description.region..."

    def accountName = description.accountName
    def credentials = description.credentials
    def compute = credentials.compute
    def project = credentials.project
    def region = description.region
    def serverGroupName = description.serverGroupName
    def serverGroup = GCEUtil.queryServerGroup(googleClusterProvider, accountName, region, serverGroupName)
    def zone = serverGroup.zone
    def managedInstanceGroup = GCEUtil.queryManagedInstanceGroup(project, zone, serverGroupName, credentials);
    def currentTargetPoolUrls = managedInstanceGroup.getTargetPools()
    def newTargetPoolUrls = []

    if (disable) {
      task.updateStatus phaseName, "Deregistering instances from load balancers..."

      currentTargetPoolUrls.each { targetPoolUrl ->
        def targetPoolLocalName = GCEUtil.getLocalName(targetPoolUrl)

        task.updateStatus phaseName, "Deregistering instances from $targetPoolLocalName..."

        def targetPool = compute.targetPools().get(project, region, targetPoolLocalName).execute()
        def instanceUrls = targetPool.getInstances()
        def instanceReferencesToRemove = instanceUrls.findResults { instanceUrl ->
          GCEUtil.getLocalName(instanceUrl).startsWith("$serverGroupName-") ? new InstanceReference(instance: instanceUrl) : null
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

      def groupInstances = compute.instanceGroups().listInstances(project,
                                                                  zone,
                                                                  serverGroupName,
                                                                  new InstanceGroupsListInstancesRequest()).execute().items

      def instanceReferencesToAdd = groupInstances.collect { groupInstance ->
        new InstanceReference(instance: groupInstance.instance)
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

    task.updateStatus phaseName, "$presentParticipling server group $description.serverGroupName in $region..."

    def instanceGroupManagersSetTargetPoolsRequest =
            new InstanceGroupManagersSetTargetPoolsRequest(targetPools: newTargetPoolUrls)

    instanceGroupManagersSetTargetPoolsRequest.setFingerprint(managedInstanceGroup.getFingerprint())

    compute.instanceGroupManagers().setTargetPools(project,
                                                   zone,
                                                   serverGroupName,
                                                   instanceGroupManagersSetTargetPoolsRequest).execute()

    task.updateStatus phaseName, "Done ${presentParticipling.toLowerCase()} server group $description.serverGroupName in $region."
    null
  }

  Task getTask() {
    TaskRepository.threadLocalTask.get()
  }
}
