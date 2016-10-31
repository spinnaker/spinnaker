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

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.api.services.compute.model.*
import com.netflix.spinnaker.clouddriver.consul.deploy.ops.EnableDisableConsulInstance
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil
import com.netflix.spinnaker.clouddriver.google.deploy.SafeRetry
import com.netflix.spinnaker.clouddriver.google.deploy.description.EnableDisableGoogleServerGroupDescription
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleClusterProvider
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleLoadBalancerProvider
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import org.springframework.beans.factory.annotation.Autowired
import retrofit.RetrofitError

abstract class AbstractEnableDisableAtomicOperation implements AtomicOperation<Void> {
  private static final List<Integer> RETRY_ERROR_CODES = [400, 403, 412]
  private static final List<Integer> SUCCESSFUL_ERROR_CODES = [404]

  abstract boolean isDisable()

  abstract String getPhaseName()

  EnableDisableGoogleServerGroupDescription description

  @Autowired
  GoogleClusterProvider googleClusterProvider

  @Autowired
  ObjectMapper objectMapper

  @Autowired
  GoogleLoadBalancerProvider googleLoadBalancerProvider

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
    def isRegional = serverGroup.regional
    // Will return null if this is a regional server group.
    def zone = serverGroup.zone
    def managedInstanceGroup =
      isRegional
      ? GCEUtil.queryRegionalManagedInstanceGroup(project, region, serverGroupName, credentials, task, phaseName)
      : GCEUtil.queryZonalManagedInstanceGroup(project, zone, serverGroupName, credentials, task, phaseName)
    def currentTargetPoolUrls = managedInstanceGroup.getTargetPools()
    def newTargetPoolUrls = []

    if (credentials.consulConfig?.enabled) {
      task.updateStatus phaseName, "$presentParticipling server group in Consul..."
      def instances =
        isRegional
        ? compute.regionInstanceGroupManagers().listManagedInstances(project, region, serverGroupName).execute().getManagedInstances()
        : compute.instanceGroupManagers().listManagedInstances(project, zone, serverGroupName).execute().getManagedInstances()

      instances.each { ManagedInstance instance ->
        try {
          EnableDisableConsulInstance.operate(credentials.consulConfig,
                                              GCEUtil.getLocalName(instance.getInstance()),
                                              disable
                                              ? EnableDisableConsulInstance.State.disable
                                              : EnableDisableConsulInstance.State.enable)
        } catch (RetrofitError e) {
          // Consul isn't running
        }
      }
    }

    def voidRetry = new SafeRetry<Void>()

    if (disable) {
      task.updateStatus phaseName, "Deregistering server group from Http(s) load balancers..."

      voidRetry.doRetry(
        destroyHttpLoadBalancerBackends(compute, project, serverGroup, googleLoadBalancerProvider, task, phaseName),
        "destroy",
        "Http load balancer backends",
        task,
        phaseName,
        RETRY_ERROR_CODES,
        SUCCESSFUL_ERROR_CODES
      )

      task.updateStatus phaseName, "Deregistering server group from internal load balancers..."

      voidRetry.doRetry(
        destroyInternalLoadBalancerBackends(compute, project, serverGroup, googleLoadBalancerProvider, task, phaseName),
        "destroy",
        "Internal load balancer backends",
        task,
        phaseName,
        RETRY_ERROR_CODES,
        SUCCESSFUL_ERROR_CODES
      )

      task.updateStatus phaseName, "Deregistering server group from ssl load balancers..."

      voidRetry.doRetry(
        destroySslLoadBalancerBackends(compute, project, serverGroup, googleLoadBalancerProvider, task, phaseName),
        "destroy",
        "Ssl load balancer backends",
        task,
        phaseName,
        RETRY_ERROR_CODES,
        SUCCESSFUL_ERROR_CODES
      )

      task.updateStatus phaseName, "Deregistering server group from network load balancers..."

      currentTargetPoolUrls.each { targetPoolUrl ->
        def targetPoolLocalName = GCEUtil.getLocalName(targetPoolUrl)

        task.updateStatus phaseName, "Deregistering instances from $targetPoolLocalName..."

        def targetPool = new SafeRetry<TargetPool>().doRetry(
          getTargetPool(compute, project, region, targetPoolLocalName),
          "get",
          "target pool",
          task,
          phaseName,
          RETRY_ERROR_CODES,
          []
        )

        def instanceUrls = targetPool.getInstances()
        def instanceReferencesToRemove = instanceUrls?.findResults { instanceUrl ->
          GCEUtil.getLocalName(instanceUrl).startsWith("$serverGroupName-") ? new InstanceReference(instance: instanceUrl) : null
        }

        if (instanceReferencesToRemove) {
          def targetPoolsRemoveInstanceRequest = new TargetPoolsRemoveInstanceRequest(instances: instanceReferencesToRemove)

          voidRetry.doRetry(
            removeInstancesFromTargetPool(compute, project, region, targetPoolLocalName, targetPoolsRemoveInstanceRequest),
            "deregister",
            "instances",
            task,
            phaseName,
            RETRY_ERROR_CODES,
            []
          )
        }
      }
    } else {
      task.updateStatus phaseName, "Registering server group with Http(s) load balancers..."

      voidRetry.doRetry(
        addHttpLoadBalancerBackends(compute, objectMapper, project, serverGroup, googleLoadBalancerProvider, task, phaseName),
        "add",
        "Http load balancer backends",
        task,
        phaseName,
        RETRY_ERROR_CODES,
        []
      )

      task.updateStatus phaseName, "Registering server group with Internal load balancers..."

      voidRetry.doRetry(
        addInternalLoadBalancerBackends(compute, project, serverGroup, googleLoadBalancerProvider, task, phaseName),
        "add",
        "Internal load balancer backends",
        task,
        phaseName,
        RETRY_ERROR_CODES,
        []
      )

      task.updateStatus phaseName, "Registering server group with Ssl load balancers..."

      voidRetry.doRetry(
        addSslLoadBalancerBackends(compute, objectMapper, project, serverGroup, googleLoadBalancerProvider, task, phaseName),
        "add",
        "Ssl load balancer backends",
        task,
        phaseName,
        RETRY_ERROR_CODES,
        []
      )

      task.updateStatus phaseName, "Registering instances with network load balancers..."

      def instanceListRetry = new SafeRetry<List<InstanceWithNamedPorts>>()
      def groupInstances =
        isRegional
        ? instanceListRetry.doRetry(
            listInstancesInRegionalGroup(compute, project, region, serverGroupName, new RegionInstanceGroupsListInstancesRequest()),
            "list",
            "instances in regional group",
            task,
            phaseName,
            RETRY_ERROR_CODES,
            []
          )
        : instanceListRetry.doRetry(
            listInstancesInZonalGroup(compute, project, zone, serverGroupName, new InstanceGroupsListInstancesRequest()),
            "list",
            "instances in zonal group",
            task,
            phaseName,
            RETRY_ERROR_CODES,
            []
          )

      def instanceReferencesToAdd = groupInstances.collect { groupInstance ->
        new InstanceReference(instance: groupInstance.instance)
      }

      def instanceTemplateUrl = managedInstanceGroup.getInstanceTemplate()
      def instanceTemplate = new SafeRetry<InstanceTemplate>().doRetry(
        getInstanceTemplate(compute, project, instanceTemplateUrl),
        "get",
        "instance template",
        task,
        phaseName,
        RETRY_ERROR_CODES,
        []
      )
      def metadataItems = instanceTemplate?.properties?.metadata?.items
      def newForwardingRuleNames = []

      metadataItems.each { item ->
        if (item.key == 'load-balancer-names') {
          newForwardingRuleNames = item.value.split(",") as List
        }
      }

      def forwardingRules = GCEUtil.queryRegionalForwardingRules(project, region, newForwardingRuleNames, compute, task, phaseName)

      newTargetPoolUrls = forwardingRules.collect { forwardingRule ->
        forwardingRule.target
      } - null // Need to remove nulls that result from internal load balancers.

      newTargetPoolUrls.each { newTargetPoolUrl ->
        def targetPoolLocalName = GCEUtil.getLocalName(newTargetPoolUrl)

        task.updateStatus phaseName, "Registering instances with $targetPoolLocalName..."

        if (instanceReferencesToAdd) {
          def targetPoolsAddInstanceRequest = new TargetPoolsAddInstanceRequest(instances: instanceReferencesToAdd)

          voidRetry.doRetry(
            addInstancesToTargetPool(compute, project, region, targetPoolLocalName, targetPoolsAddInstanceRequest),
            "register",
            "instances",
            task,
            phaseName,
            RETRY_ERROR_CODES,
            []
          )
        }
      }

      if (currentTargetPoolUrls) {
        newTargetPoolUrls = ((currentTargetPoolUrls as Set) + (newTargetPoolUrls as Set)) as List
      }
    }

    task.updateStatus phaseName, "$presentParticipling server group $description.serverGroupName in $region..."

    if (isRegional) {
      def instanceGroupManagersSetTargetPoolsRequest = new RegionInstanceGroupManagersSetTargetPoolsRequest(targetPools: newTargetPoolUrls)

      instanceGroupManagersSetTargetPoolsRequest.setFingerprint(managedInstanceGroup.getFingerprint())

      compute.regionInstanceGroupManagers().setTargetPools(project,
                                                           region,
                                                           serverGroupName,
                                                           instanceGroupManagersSetTargetPoolsRequest).execute()
    } else {
      def instanceGroupManagersSetTargetPoolsRequest = new InstanceGroupManagersSetTargetPoolsRequest(targetPools: newTargetPoolUrls)

      instanceGroupManagersSetTargetPoolsRequest.setFingerprint(managedInstanceGroup.getFingerprint())

      compute.instanceGroupManagers().setTargetPools(project,
                                                     zone,
                                                     serverGroupName,
                                                     instanceGroupManagersSetTargetPoolsRequest).execute()
    }

    task.updateStatus phaseName, "Done ${presentParticipling.toLowerCase()} server group $description.serverGroupName in $region."
    null
  }

  Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  Closure destroyHttpLoadBalancerBackends(compute, project, serverGroup, googleLoadBalancerProvider, task, phaseName) {
    return {
      GCEUtil.destroyHttpLoadBalancerBackends(compute, project, serverGroup, googleLoadBalancerProvider, task, phaseName)
      null
    }
  }

  Closure destroyInternalLoadBalancerBackends(compute, project, serverGroup, googleLoadBalancerProvider, task, phaseName) {
    return {
      GCEUtil.destroyInternalLoadBalancerBackends(compute, project, serverGroup, googleLoadBalancerProvider, task, phaseName)
      null
    }
  }

  Closure destroySslLoadBalancerBackends(compute, project, serverGroup, googleLoadBalancerProvider, task, phaseName) {
    return {
      GCEUtil.destroySslLoadBalancerBackends(compute, project, serverGroup, googleLoadBalancerProvider, task, phaseName)
      null
    }
  }

  Closure addHttpLoadBalancerBackends(compute, objectMapper, project, serverGroup, googleLoadBalancerProvider, task, phaseName) {
    return {
      GCEUtil.addHttpLoadBalancerBackends(compute, objectMapper, project, serverGroup, googleLoadBalancerProvider, task, phaseName)
      null
    }
  }

  Closure addSslLoadBalancerBackends(compute, objectMapper, project, serverGroup, googleLoadBalancerProvider, task, phaseName) {
    return {
      GCEUtil.addSslLoadBalancerBackends(compute, objectMapper, project, serverGroup, googleLoadBalancerProvider, task, phaseName)
      null
    }
  }

  Closure addInternalLoadBalancerBackends(compute, project, serverGroup, googleLoadBalancerProvider, task, phaseName) {
    return {
      GCEUtil.addInternalLoadBalancerBackends(compute, project, serverGroup, googleLoadBalancerProvider, task, phaseName)
      null
    }
  }

  Closure getTargetPool(compute, project, region, targetPoolLocalName) {
    return {
      return compute.targetPools().get(project, region, targetPoolLocalName).execute()
    }
  }

  Closure listInstancesInRegionalGroup(compute, project, region, serverGroupName, regionInstanceGroupsListInstancesRequest) {
    return {
      return compute.regionInstanceGroups().listInstances(project, region, serverGroupName, regionInstanceGroupsListInstancesRequest).execute().items
    }
  }

  Closure listInstancesInZonalGroup(compute, project, zone, serverGroupName, instanceGroupsListInstancesRequest) {
    return {
      return compute.instanceGroups().listInstances(project, zone, serverGroupName, instanceGroupsListInstancesRequest).execute().items
    }
  }

  Closure getInstanceTemplate(compute, project, instanceTemplateUrl) {
    return {
      return compute.instanceTemplates().get(project, GCEUtil.getLocalName(instanceTemplateUrl)).execute()
    }
  }

  Closure removeInstancesFromTargetPool(compute, project, region, targetPoolLocalName, targetPoolsRemoveInstanceRequest) {
    return {
      compute.targetPools().removeInstance(project, region, targetPoolLocalName, targetPoolsRemoveInstanceRequest).execute()
      null
    }
  }

  Closure addInstancesToTargetPool(compute, project, region, targetPoolLocalName, targetPoolsAddInstanceRequest) {
    return {
      compute.targetPools().addInstance(project, region, targetPoolLocalName, targetPoolsAddInstanceRequest).execute()
      null
    }
  }
}
