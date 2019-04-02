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
import com.netflix.spinnaker.clouddriver.google.deploy.GoogleOperationPoller
import com.netflix.spinnaker.clouddriver.google.deploy.SafeRetry
import com.netflix.spinnaker.clouddriver.google.deploy.description.EnableDisableGoogleServerGroupDescription
import com.netflix.spinnaker.clouddriver.google.model.GoogleAutoscalingPolicy
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleClusterProvider
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleLoadBalancerProvider
import org.springframework.beans.factory.annotation.Autowired
import retrofit.RetrofitError

abstract class AbstractEnableDisableAtomicOperation extends GoogleAtomicOperation<Void> {
  private static final List<Integer> RETRY_ERROR_CODES = [400, 403, 412]
  private static final List<Integer> SUCCESSFUL_ERROR_CODES = [404]

  abstract boolean isDisable()

  abstract String getPhaseName()

  EnableDisableGoogleServerGroupDescription description

  @Autowired
  GoogleClusterProvider googleClusterProvider

  @Autowired
  GoogleOperationPoller googleOperationPoller

  @Autowired
  ObjectMapper objectMapper

  @Autowired
  GoogleLoadBalancerProvider googleLoadBalancerProvider

  @Autowired
  SafeRetry safeRetry

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
      ? GCEUtil.queryRegionalManagedInstanceGroup(project, region, serverGroupName, credentials, task, phaseName, safeRetry, this)
      : GCEUtil.queryZonalManagedInstanceGroup(project, zone, serverGroupName, credentials, task, phaseName, safeRetry, this)
    def currentTargetPoolUrls = managedInstanceGroup.getTargetPools()
    def newTargetPoolUrls = []

    if (credentials.consulConfig?.enabled) {
      task.updateStatus phaseName, "$presentParticipling server group in Consul..."
      def instances
      if (isRegional) {
        instances = timeExecute(
          compute.regionInstanceGroupManagers().listManagedInstances(project, region, serverGroupName),
          "compute.regionInstanceGroupManagers.listManagedInstances",
          TAG_SCOPE, SCOPE_REGIONAL, TAG_REGION, region).getManagedInstances()
      } else {
        instances = timeExecute(
          compute.instanceGroupManagers().listManagedInstances(project, zone, serverGroupName),
          "compute.instanceGroupManagers.listManagedInstances",
          TAG_SCOPE, SCOPE_ZONAL, TAG_ZONE, zone).getManagedInstances()
      }

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

    if (disable) {
      task.updateStatus phaseName, "Disabling autoscaling for server group disable..."
      setAutoscalingPolicyMode(compute, project, serverGroup, GoogleAutoscalingPolicy.AutoscalingMode.OFF)

      task.updateStatus phaseName, "Deregistering server group from Http(s) load balancers..."

      safeRetry.doRetry(
        destroyHttpLoadBalancerBackends(compute, project, serverGroup, googleLoadBalancerProvider, task, phaseName),
        "Http load balancer backends",
        task,
        RETRY_ERROR_CODES,
        SUCCESSFUL_ERROR_CODES,
        [operation: "destroyHttpLoadBalancerBackends", action: "destroy", phase: phaseName, (TAG_SCOPE): SCOPE_GLOBAL],
        registry
      )

      task.updateStatus phaseName, "Deregistering server group from internal load balancers..."

      safeRetry.doRetry(
        destroyInternalLoadBalancerBackends(compute, project, serverGroup, googleLoadBalancerProvider, task, phaseName),
        "Internal load balancer backends",
        task,
        RETRY_ERROR_CODES,
        SUCCESSFUL_ERROR_CODES,
        [operation: "destroyInternalLoadBalancerBackends", action: "destroy", phase: phaseName, (TAG_SCOPE): SCOPE_GLOBAL],
        registry
      )

      task.updateStatus phaseName, "Deregistering server group from ssl load balancers..."

      safeRetry.doRetry(
        destroySslLoadBalancerBackends(compute, project, serverGroup, googleLoadBalancerProvider, task, phaseName),
        "Ssl load balancer backends",
        task,
        RETRY_ERROR_CODES,
        SUCCESSFUL_ERROR_CODES,
        [operation: "destroySslLoadBalancerBackends", action: "destroy", phase: phaseName, (TAG_SCOPE): SCOPE_GLOBAL],
        registry
      )

      task.updateStatus phaseName, "Deregistering server group from tcp load balancers..."

      safeRetry.doRetry(
        destroyTcpLoadBalancerBackends(compute, project, serverGroup, googleLoadBalancerProvider, task, phaseName),
        "Tcp load balancer backends",
        task,
        RETRY_ERROR_CODES,
        SUCCESSFUL_ERROR_CODES,
        [operation: "destroyTcpLoadBalancerBackends", action: "destroy", phase: phaseName, (TAG_SCOPE): SCOPE_GLOBAL],
        registry
      )

      task.updateStatus phaseName, "Deregistering server group from network load balancers..."

      currentTargetPoolUrls.each { targetPoolUrl ->
        def targetPoolLocalName = GCEUtil.getLocalName(targetPoolUrl)

        task.updateStatus phaseName, "Deregistering instances from $targetPoolLocalName..."

        def targetPool = safeRetry.doRetry(
          getTargetPool(compute, project, region, targetPoolLocalName),
          "target pool",
          task,
          RETRY_ERROR_CODES,
          [],
          [operation: "getTargetPool", action: "destroy", phase: phaseName, (TAG_SCOPE): SCOPE_REGIONAL, (TAG_REGION): region],
          registry
        ) as TargetPool

        def instanceUrls = targetPool.getInstances()
        def instanceReferencesToRemove = instanceUrls?.findResults { instanceUrl ->
          GCEUtil.getLocalName(instanceUrl).startsWith("$serverGroupName-") ? new InstanceReference(instance: instanceUrl) : null
        }

        if (instanceReferencesToRemove) {
          def targetPoolsRemoveInstanceRequest = new TargetPoolsRemoveInstanceRequest(instances: instanceReferencesToRemove)

          safeRetry.doRetry(
            removeInstancesFromTargetPool(compute, project, region, targetPoolLocalName, targetPoolsRemoveInstanceRequest),
            "instances",
            task,
            RETRY_ERROR_CODES,
            [],
            [operation: "removeInstancesFromTargetPool", action: "deregister", phase: phaseName, (TAG_SCOPE): SCOPE_REGIONAL, (TAG_REGION): region],
            registry
          )
        }
      }
    } else {

      def instanceTemplateUrl = managedInstanceGroup.getInstanceTemplate()
      def instanceTemplate = safeRetry.doRetry(
        getInstanceTemplate(compute, project, instanceTemplateUrl),
        "instance template",
        task,
        RETRY_ERROR_CODES,
        [],
        [operation: "getInstanceTemplate", action: "get", phase: phaseName, (TAG_SCOPE): SCOPE_GLOBAL],
        registry
      ) as InstanceTemplate
      def metadataItems = instanceTemplate?.properties?.metadata?.items

      task.updateStatus phaseName, "Re-enabling autoscaling for server group enable..."

      Map metadataMap = GCEUtil.buildMapFromMetadata(instanceTemplate?.properties?.metadata)
      String autoscalerJson = metadataMap?.(GCEUtil.AUTOSCALING_POLICY)
      if (autoscalerJson) {
        def autoscaler = objectMapper.readValue(autoscalerJson, Map)
        def enabledMode = GoogleAutoscalingPolicy.AutoscalingMode.valueOf(autoscaler?.autoscalingPolicy?.mode ?: "ON")
        setAutoscalingPolicyMode(compute, project, serverGroup, enabledMode)
      } else {
        setAutoscalingPolicyMode(compute, project, serverGroup, GoogleAutoscalingPolicy.AutoscalingMode.ON)
      }

      task.updateStatus phaseName, "Registering server group with Http(s) load balancers..."

      safeRetry.doRetry(
        addHttpLoadBalancerBackends(compute, objectMapper, project, serverGroup, googleLoadBalancerProvider, task, phaseName),
        "Http load balancer backends",
        task,
        RETRY_ERROR_CODES,
        [],
        [operation: "addHttpLoadBalancerBackends", action: "add", phase: phaseName, (TAG_SCOPE): SCOPE_GLOBAL],
        registry
      )

      task.updateStatus phaseName, "Registering server group with Internal load balancers..."

      safeRetry.doRetry(
        addInternalLoadBalancerBackends(compute, project, serverGroup, googleLoadBalancerProvider, task, phaseName),
        "Internal load balancer backends",
        task,
        RETRY_ERROR_CODES,
        [],
        [operation: "addInternalLoadbalancerBackends", action: "add", phase: phaseName, (TAG_SCOPE): SCOPE_GLOBAL],
        registry
      )

      task.updateStatus phaseName, "Registering server group with Ssl load balancers..."

      safeRetry.doRetry(
        addSslLoadBalancerBackends(compute, objectMapper, project, serverGroup, googleLoadBalancerProvider, task, phaseName),
        "Ssl load balancer backends",
        task,
        RETRY_ERROR_CODES,
        [],
        [operation: "addSslLoadbalancerBackends", action: "add", phase: phaseName, (TAG_SCOPE): SCOPE_GLOBAL],
        registry
      )

      task.updateStatus phaseName, "Registering server group with Tcp load balancers..."

      safeRetry.doRetry(
        addTcpLoadBalancerBackends(compute, objectMapper, project, serverGroup, googleLoadBalancerProvider, task, phaseName),
        "Tcp load balancer backends",
        task,
        RETRY_ERROR_CODES,
        [],
        [operation: "addTcpLoadbalancerBackends", action: "add", phase: phaseName, (TAG_SCOPE): SCOPE_GLOBAL],
        registry
      )

      task.updateStatus phaseName, "Registering instances with network load balancers..."

      def groupInstances =
        isRegional
        ? safeRetry.doRetry(
            listInstancesInRegionalGroup(compute, project, region, serverGroupName, new RegionInstanceGroupsListInstancesRequest()),
            "instances in regional group",
            task,
            RETRY_ERROR_CODES,
            [],
            [operation: "listInstanesInRegionalGroup", action: "list", phase: phaseName, (TAG_SCOPE): SCOPE_REGIONAL, (TAG_REGION): region],
            registry
          )
        : safeRetry.doRetry(
            listInstancesInZonalGroup(compute, project, zone, serverGroupName, new InstanceGroupsListInstancesRequest()),
            "instances in zonal group",
            task,
            RETRY_ERROR_CODES,
            [],
            [operation: "listInstanesInZonalGroup", action: "list", phase: phaseName, (TAG_SCOPE): SCOPE_ZONAL, (TAG_ZONE): zone],
            registry
          )

      def instanceReferencesToAdd = groupInstances.collect { groupInstance ->
        new InstanceReference(instance: groupInstance.instance)
      }

      def newForwardingRuleNames = []
      metadataItems.each { item ->
        if (item.key == 'load-balancer-names') {
          newForwardingRuleNames = item.value.split(",") as List
        }
      }

      def forwardingRules = GCEUtil.queryRegionalForwardingRules(project, region, newForwardingRuleNames, compute, task, phaseName, safeRetry, this)

      newTargetPoolUrls = forwardingRules.collect { forwardingRule ->
        forwardingRule.target
      } - null // Need to remove nulls that result from internal load balancers.

      newTargetPoolUrls.each { newTargetPoolUrl ->
        def targetPoolLocalName = GCEUtil.getLocalName(newTargetPoolUrl)

        task.updateStatus phaseName, "Registering instances with $targetPoolLocalName..."

        if (instanceReferencesToAdd) {
          def targetPoolsAddInstanceRequest = new TargetPoolsAddInstanceRequest(instances: instanceReferencesToAdd)

          safeRetry.doRetry(
            addInstancesToTargetPool(compute, project, region, targetPoolLocalName, targetPoolsAddInstanceRequest),
            "instances",
            task,
            RETRY_ERROR_CODES,
            [],
            [operation: "addInstancesToTargetPool", action: "register", phase: phaseName, (TAG_SCOPE): SCOPE_REGIONAL, (TAG_REGION): region],
            registry
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

      timeExecute(
        compute.regionInstanceGroupManagers().setTargetPools(project,
                                                             region,
                                                             serverGroupName,
                                                             instanceGroupManagersSetTargetPoolsRequest),
        "compute.regionInstanceGroupManagers.setTargetPools",
        TAG_SCOPE, SCOPE_REGIONAL, TAG_REGION, region)
    } else {
      def instanceGroupManagersSetTargetPoolsRequest = new InstanceGroupManagersSetTargetPoolsRequest(targetPools: newTargetPoolUrls)

      instanceGroupManagersSetTargetPoolsRequest.setFingerprint(managedInstanceGroup.getFingerprint())

      timeExecute(
        compute.instanceGroupManagers().setTargetPools(project,
                                                       zone,
                                                       serverGroupName,
                                                       instanceGroupManagersSetTargetPoolsRequest),
        "compute.regionInstanceGroupManagers.setTargetPools",
        TAG_SCOPE, SCOPE_ZONAL, TAG_ZONE, zone)
    }

    task.updateStatus phaseName, "Done ${presentParticipling.toLowerCase()} server group $description.serverGroupName in $region."
    null
  }

  Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  Closure destroyHttpLoadBalancerBackends(compute, project, serverGroup, googleLoadBalancerProvider, task, phaseName) {
    return {
      GCEUtil.destroyHttpLoadBalancerBackends(compute, project, serverGroup, googleLoadBalancerProvider, task, phaseName, googleOperationPoller, this)
      null
    }
  }

  Closure destroyInternalLoadBalancerBackends(compute, project, serverGroup, googleLoadBalancerProvider, task, phaseName) {
    return {
      GCEUtil.destroyInternalLoadBalancerBackends(compute, project, serverGroup, googleLoadBalancerProvider, task, phaseName, googleOperationPoller, this)
      null
    }
  }

  Closure destroySslLoadBalancerBackends(compute, project, serverGroup, googleLoadBalancerProvider, task, phaseName) {
    return {
      GCEUtil.destroySslLoadBalancerBackends(compute, project, serverGroup, googleLoadBalancerProvider, task, phaseName, googleOperationPoller, this)
      null
    }
  }

  Closure destroyTcpLoadBalancerBackends(compute, project, serverGroup, googleLoadBalancerProvider, task, phaseName) {
    return {
      GCEUtil.destroyTcpLoadBalancerBackends(compute, project, serverGroup, googleLoadBalancerProvider, task, phaseName, googleOperationPoller, this)
      null
    }
  }

  Closure addHttpLoadBalancerBackends(compute, objectMapper, project, serverGroup, googleLoadBalancerProvider, task, phaseName) {
    return {
      GCEUtil.addHttpLoadBalancerBackends(compute, objectMapper, project, serverGroup, googleLoadBalancerProvider, task, phaseName, googleOperationPoller, this)
      null
    }
  }

  Closure addSslLoadBalancerBackends(compute, objectMapper, project, serverGroup, googleLoadBalancerProvider, task, phaseName) {
    return {
      GCEUtil.addSslLoadBalancerBackends(compute, objectMapper, project, serverGroup, googleLoadBalancerProvider, task, phaseName, googleOperationPoller, this)
      null
    }
  }

  Closure addTcpLoadBalancerBackends(compute, objectMapper, project, serverGroup, googleLoadBalancerProvider, task, phaseName) {
    return {
      GCEUtil.addTcpLoadBalancerBackends(compute, objectMapper, project, serverGroup, googleLoadBalancerProvider, task, phaseName, googleOperationPoller, this)
      null
    }
  }

  Closure addInternalLoadBalancerBackends(compute, project, serverGroup, googleLoadBalancerProvider, task, phaseName) {
    return {
      GCEUtil.addInternalLoadBalancerBackends(compute, project, serverGroup, googleLoadBalancerProvider, task, phaseName, googleOperationPoller, this)
      null
    }
  }

  Closure getTargetPool(compute, project, region, targetPoolLocalName) {
    return {
      return timeExecute(
        compute.targetPools().get(project, region, targetPoolLocalName),
        "compute.targetPools.get",
        TAG_SCOPE, SCOPE_REGIONAL, TAG_REGION, region)
    }
  }

  Closure listInstancesInRegionalGroup(compute, project, region, serverGroupName, regionInstanceGroupsListInstancesRequest) {
    return {
      return timeExecute(
          compute.regionInstanceGroups().listInstances(project, region, serverGroupName, regionInstanceGroupsListInstancesRequest),
          "compute.regionInstanceGroups.listInstances",
          TAG_SCOPE, SCOPE_REGIONAL, TAG_REGION, region).items
    }
  }

  Closure listInstancesInZonalGroup(compute, project, zone, serverGroupName, instanceGroupsListInstancesRequest) {
    return {
      return timeExecute(
          compute.instanceGroups().listInstances(project, zone, serverGroupName, instanceGroupsListInstancesRequest),
          "compute.instanceGroups.listInstances",
          TAG_SCOPE, SCOPE_ZONAL, TAG_ZONE, zone).items
    }
  }

  Closure getInstanceTemplate(compute, project, instanceTemplateUrl) {
    return {
      return timeExecute(
          compute.instanceTemplates().get(project, GCEUtil.getLocalName(instanceTemplateUrl)),
          "compute.instanceTemplates.get",
          TAG_SCOPE, SCOPE_GLOBAL)
    }
  }

  Closure removeInstancesFromTargetPool(compute, project, region, targetPoolLocalName, targetPoolsRemoveInstanceRequest) {
    return {
        timeExecute(
            compute.targetPools().removeInstance(project, region, targetPoolLocalName, targetPoolsRemoveInstanceRequest),
            "compute.targetPools.removeInstance",
            TAG_SCOPE, SCOPE_REGIONAL, TAG_REGION, region)
    }
  }

  Closure addInstancesToTargetPool(compute, project, region, targetPoolLocalName, targetPoolsAddInstanceRequest) {
    return {
      timeExecute(
          compute.targetPools().addInstance(project, region, targetPoolLocalName, targetPoolsAddInstanceRequest),
          "compute.targetPools.addInstance",
          TAG_SCOPE, SCOPE_REGIONAL, TAG_REGION, region)
    }
  }

  void setAutoscalingPolicyMode(compute, String project, serverGroup, GoogleAutoscalingPolicy.AutoscalingMode mode) {
    String serverGroupName = serverGroup.name
    String region = serverGroup.region
    String zone = serverGroup.zone
    if (serverGroup.autoscalingPolicy) {
      def policyDescription =
        GCEUtil.buildAutoscalingPolicyDescriptionFromAutoscalingPolicy(serverGroup.autoscalingPolicy)
      if (policyDescription) {
        def autoscaler = GCEUtil.buildAutoscaler(serverGroupName, serverGroup.selfLink, policyDescription)
        autoscaler.getAutoscalingPolicy().setMode(mode.toString())

        if (serverGroup.regional) {
          timeExecute(
            compute.regionAutoscalers().update(project, region, autoscaler),
            "compute.regionAutoscalers.update",
            TAG_SCOPE, SCOPE_REGIONAL, TAG_REGION, region)
        } else {
          timeExecute(
            compute.autoscalers().update(project, zone, autoscaler),
            "compute.autoscalers.update",
            TAG_SCOPE, SCOPE_ZONAL, TAG_ZONE, zone)
        }
      }
    }
  }
}
