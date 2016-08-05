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

package com.netflix.spinnaker.clouddriver.google.deploy.handlers

import com.google.api.services.compute.model.*
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.deploy.DeployDescription
import com.netflix.spinnaker.clouddriver.deploy.DeployHandler
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult
import com.netflix.spinnaker.clouddriver.google.GoogleConfiguration
import com.netflix.spinnaker.clouddriver.google.config.GoogleConfigurationProperties
import com.netflix.spinnaker.clouddriver.google.deploy.GCEServerGroupNameResolver
import com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil
import com.netflix.spinnaker.clouddriver.google.deploy.GoogleOperationPoller
import com.netflix.spinnaker.clouddriver.google.deploy.description.BasicGoogleDeployDescription
import com.netflix.spinnaker.clouddriver.google.model.GoogleServerGroup
import com.netflix.spinnaker.clouddriver.google.model.callbacks.Utils
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleHttpLoadBalancingPolicy
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleLoadBalancerType
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleClusterProvider
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleLoadBalancerProvider
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleSecurityGroupProvider
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
@Slf4j
class BasicGoogleDeployHandler implements DeployHandler<BasicGoogleDeployDescription> {

  // TODO(duftler): This should move to a common location.
  private static final String BASE_PHASE = "DEPLOY"

  // TODO(duftler): These should be exposed/configurable.
  private static final String DEFAULT_NETWORK_NAME = "default"
  private static final String ACCESS_CONFIG_NAME = "External NAT"
  private static final String ACCESS_CONFIG_TYPE = "ONE_TO_ONE_NAT"

  @Autowired
  private GoogleConfigurationProperties googleConfigurationProperties

  @Autowired
  private GoogleClusterProvider googleClusterProvider

  @Autowired
  private GoogleConfiguration.DeployDefaults googleDeployDefaults

  @Autowired
  private GoogleOperationPoller googleOperationPoller

  @Autowired
  GoogleSecurityGroupProvider googleSecurityGroupProvider

  @Autowired
  GoogleLoadBalancerProvider googleLoadBalancerProvider

  @Autowired
  String googleApplicationName

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Override
  boolean handles(DeployDescription description) {
    description instanceof BasicGoogleDeployDescription
  }

  /**
   * curl -X POST -H "Content-Type: application/json" -d '[ { "createServerGroup": { "application": "myapp", "stack": "dev", "image": "ubuntu-1404-trusty-v20160509a", "targetSize": 3, "instanceType": "f1-micro", "zone": "us-central1-f", "credentials": "my-account-name" }} ]' localhost:7002/gce/ops
   * curl -X POST -H "Content-Type: application/json" -d '[ { "createServerGroup": { "application": "myapp", "stack": "dev", "freeFormDetails": "something", "image": "ubuntu-1404-trusty-v20160509a", "targetSize": 3, "instanceType": "f1-micro", "zone": "us-central1-f", "credentials": "my-account-name" }} ]' localhost:7002/gce/ops
   * curl -X POST -H "Content-Type: application/json" -d '[ { "createServerGroup": { "application": "myapp", "stack": "dev", "image": "ubuntu-1404-trusty-v20160509a", "targetSize": 3, "instanceType": "f1-micro", "zone": "us-central1-f", "loadBalancers": ["testlb", "testhttplb"], "instanceMetadata": { "load-balancer-names": "myapp-testlb", "global-load-balancer-names": "myapp-testhttplb", "backend-service-names": "my-backend-service"}, "credentials": "my-account-name" }} ]' localhost:7002/gce/ops
   * curl -X POST -H "Content-Type: application/json" -d '[ { "createServerGroup": { "application": "myapp", "stack": "dev", "image": "ubuntu-1404-trusty-v20160509a", "targetSize": 3, "instanceType": "f1-micro", "zone": "us-central1-f", "tags": ["my-tag-1", "my-tag-2"], "credentials": "my-account-name" }} ]' localhost:7002/gce/ops
   *
   * @param description
   * @param priorOutputs
   * @return
   */
  @Override
  DeploymentResult handle(BasicGoogleDeployDescription description, List priorOutputs) {
    def accountName = description.accountName
    def credentials = description.credentials
    def compute = credentials.compute
    def project = credentials.project
    def isRegional = description.regional
    def zone = description.zone
    def region = description.region ?: GCEUtil.getRegionFromZone(project, zone, compute)

    def serverGroupNameResolver = new GCEServerGroupNameResolver(project, region, credentials)
    def clusterName = serverGroupNameResolver.combineAppStackDetail(description.application, description.stack, description.freeFormDetails)

    task.updateStatus BASE_PHASE, "Initializing creation of server group for cluster $clusterName in ${isRegional ? region : zone}..."

    task.updateStatus BASE_PHASE, "Looking up next sequence..."

    def serverGroupName = serverGroupNameResolver.resolveNextServerGroupName(description.application, description.stack, description.freeFormDetails, false)

    task.updateStatus BASE_PHASE, "Produced server group name: $serverGroupName"

    def machineTypeName
    if (description.instanceType.startsWith('custom')) {
      machineTypeName = description.instanceType
    } else {
      machineTypeName = GCEUtil.queryMachineType(project, description.instanceType, compute, task, BASE_PHASE).name
    }


    def sourceImage = GCEUtil.querySourceImage(project,
                                               description,
                                               compute,
                                               task,
                                               BASE_PHASE,
                                               googleApplicationName,
                                               googleConfigurationProperties.baseImageProjects)

    def network = GCEUtil.queryNetwork(project, description.network ?: DEFAULT_NETWORK_NAME, compute, task, BASE_PHASE)

    def subnet =
      description.subnet ? GCEUtil.querySubnet(project, region, description.subnet, compute, task, BASE_PHASE) : null

    def targetPools = []

    // We need the full url for each referenced network load balancer, and also to check that the HTTP(S)
    // load balancers exist.
    if (description.loadBalancers) {
      def foundLoadBalancers = GCEUtil.queryAllLoadBalancers(googleLoadBalancerProvider,
                                                             description.loadBalancers,
                                                             task,
                                                             BASE_PHASE)
      def networkLoadBalancers = foundLoadBalancers.findAll { it.loadBalancerType == GoogleLoadBalancerType.NETWORK.toString() }
      targetPools = networkLoadBalancers.collect { it.targetPool }
    }

    def securityGroupTags = GCEUtil.querySecurityGroupTags(description.securityGroups, accountName,
        googleSecurityGroupProvider, task, BASE_PHASE)

    if (securityGroupTags) {
      description.tags = GCEUtil.mergeDescriptionAndSecurityGroupTags(description.tags, securityGroupTags)
    }

    task.updateStatus BASE_PHASE, "Composing server group $serverGroupName..."

    def attachedDisks = GCEUtil.buildAttachedDisks(project,
                                                   null,
                                                   sourceImage,
                                                   description.disks,
                                                   false,
                                                   description.instanceType,
                                                   googleDeployDefaults)

    def networkInterface = GCEUtil.buildNetworkInterface(network, subnet, ACCESS_CONFIG_NAME, ACCESS_CONFIG_TYPE)

    def metadata = GCEUtil.buildMetadataFromMap(description.instanceMetadata)

    def tags = GCEUtil.buildTagsFromList(description.tags)

    if (description.authScopes && !description.serviceAccountEmail) {
      description.serviceAccountEmail = "default"
    }

    def serviceAccount = GCEUtil.buildServiceAccount(description.serviceAccountEmail, description.authScopes)

    def scheduling = GCEUtil.buildScheduling(description)

    def instanceProperties = new InstanceProperties(machineType: machineTypeName,
                                                    disks: attachedDisks,
                                                    networkInterfaces: [networkInterface],
                                                    metadata: metadata,
                                                    tags: tags,
                                                    scheduling: scheduling,
                                                    serviceAccounts: serviceAccount)

    def instanceTemplate = new InstanceTemplate(name: "$serverGroupName-${System.currentTimeMillis()}",
                                                properties: instanceProperties)
    def instanceTemplateCreateOperation = compute.instanceTemplates().insert(project, instanceTemplate).execute()
    def instanceTemplateUrl = instanceTemplateCreateOperation.targetLink

    // Before building the managed instance group we must check and wait until the instance template is built.
    googleOperationPoller.waitForGlobalOperation(compute, project, instanceTemplateCreateOperation.getName(),
        null, task, "instance template " + GCEUtil.getLocalName(instanceTemplateUrl), BASE_PHASE)

    if (autoscalerIsSpecified(description)) {
      GCEUtil.calibrateTargetSizeWithAutoscaler(description)
    }

    def autoHealingPolicy =
      description.autoHealingPolicy?.healthCheck
      ? [new InstanceGroupManagerAutoHealingPolicy(
             healthCheck: GCEUtil.queryHealthCheck(project,
                                                   description.autoHealingPolicy.healthCheck,
                                                   compute,
                                                   task,
                                                   BASE_PHASE).selfLink,
             initialDelaySec: description.autoHealingPolicy.initialDelaySec)]
      : null

    def migCreateOperation
    def instanceGroupManager = new InstanceGroupManager()
        .setName(serverGroupName)
        .setBaseInstanceName(serverGroupName)
        .setInstanceTemplate(instanceTemplateUrl)
        .setTargetSize(description.targetSize)
        .setTargetPools(targetPools)
        .setAutoHealingPolicies(autoHealingPolicy)

    def hasBackendServices = description.instanceMetadata &&
        description.instanceMetadata.containsKey(GoogleServerGroup.View.BACKEND_SERVICE_NAMES)

    if (hasBackendServices && (description?.loadBalancingPolicy || description?.source?.serverGroupName))  {
      NamedPort namedPort = null
      def sourceGroupName = description?.source?.serverGroupName
      // Note: this favors the explicitly specified load balancing policy over the source server group.
      if (sourceGroupName && !description?.loadBalancingPolicy) {
        def sourceServerGroup = googleClusterProvider.getServerGroup(description.accountName, description.source.region, sourceGroupName)
        if (!sourceServerGroup) {
          log.warn("Could not locate source server group ${sourceGroupName} to update named port.")
        }
        namedPort = new NamedPort(
            name: GoogleHttpLoadBalancingPolicy.HTTP_PORT_NAME,
            port: sourceServerGroup?.namedPorts[(GoogleHttpLoadBalancingPolicy.HTTP_PORT_NAME)] ?: GoogleHttpLoadBalancingPolicy.HTTP_DEFAULT_PORT,
        )
      } else {
        namedPort = new NamedPort(
            name: GoogleHttpLoadBalancingPolicy.HTTP_PORT_NAME,
            port: description?.loadBalancingPolicy?.listeningPort ?: GoogleHttpLoadBalancingPolicy.HTTP_DEFAULT_PORT
        )
      }
      if (!namedPort) {
        log.warn("Could not locate named port on either load balancing policy or source server group. Setting default named port.")
        namedPort = new NamedPort(
            name: GoogleHttpLoadBalancingPolicy.HTTP_PORT_NAME,
            port: GoogleHttpLoadBalancingPolicy.HTTP_DEFAULT_PORT,
        )
      }
      instanceGroupManager.setNamedPorts([namedPort])
    }

    if (isRegional) {
      migCreateOperation = compute.regionInstanceGroupManagers().insert(project, region, instanceGroupManager).execute()

      if (autoscalerIsSpecified(description)) {
        // Before creating the Autoscaler we must wait until the managed instance group is created.
        googleOperationPoller.waitForRegionalOperation(compute, project, region, migCreateOperation.getName(),
          null, task, "managed instance group $serverGroupName", BASE_PHASE)

        task.updateStatus BASE_PHASE, "Creating regional autoscaler for $serverGroupName..."

        Autoscaler autoscaler = GCEUtil.buildAutoscaler(serverGroupName,
                                                        migCreateOperation.targetLink,
                                                        description.autoscalingPolicy)

        compute.regionAutoscalers().insert(project, region, autoscaler).execute()
      }
    } else {
      migCreateOperation = compute.instanceGroupManagers().insert(project, zone, instanceGroupManager).execute()

      if (autoscalerIsSpecified(description)) {
        // Before creating the Autoscaler we must wait until the managed instance group is created.
        googleOperationPoller.waitForZonalOperation(compute, project, zone, migCreateOperation.getName(),
          null, task, "managed instance group $serverGroupName", BASE_PHASE)

        task.updateStatus BASE_PHASE, "Creating zonal autoscaler for $serverGroupName..."

        Autoscaler autoscaler = GCEUtil.buildAutoscaler(serverGroupName,
                                                        migCreateOperation.targetLink,
                                                        description.autoscalingPolicy)

        compute.autoscalers().insert(project, zone, autoscaler).execute()
      }
    }

    task.updateStatus BASE_PHASE, "Done creating server group $serverGroupName in ${isRegional ? region : zone}."

    if (hasBackendServices) {
      List<String> backendServices = description.instanceMetadata[GoogleServerGroup.View.BACKEND_SERVICE_NAMES].split(",")

      backendServices.each { String backendServiceName ->
        BackendService backendService = compute.backendServices().get(project, backendServiceName).execute()

        Backend sourceBackend = backendService.backends.find { Backend backend ->
          Utils.getLocalName(backend.group) == description?.source?.serverGroupName
        }
        if (description?.source?.serverGroupName && !sourceBackend) {
          GCEUtil.updateStatusAndThrowNotFoundException("Backend for ancestor server group ${description?.source?.serverGroupName} not found.", task, BASE_PHASE)
        }

        Backend backendToAdd
        def loadBalancingPolicy = description.loadBalancingPolicy
        if (loadBalancingPolicy?.balancingMode) {
          def balancingMode = loadBalancingPolicy.balancingMode
          backendToAdd = new Backend(
              balancingMode: balancingMode,
              maxRatePerInstance: balancingMode == GoogleHttpLoadBalancingPolicy.BalancingMode.RATE ?
                  loadBalancingPolicy.maxRatePerInstance : null,
              maxUtilization: balancingMode == GoogleHttpLoadBalancingPolicy.BalancingMode.UTILIZATION ?
                  loadBalancingPolicy.maxUtilization : null,
              capacityScaler: loadBalancingPolicy.capacityScaler != null ? loadBalancingPolicy.capacityScaler : 1.0,
          )
        } else if (sourceBackend) {
          backendToAdd = new Backend(sourceBackend)
        } else {
          backendToAdd = new Backend()
        }

        if (isRegional) {
          backendToAdd.setGroup(GCEUtil.buildRegionalServerGroupUrl(project, region, serverGroupName))
        } else {
          backendToAdd.setGroup(GCEUtil.buildZonalServerGroupUrl(project, zone, serverGroupName))
        }
        backendService.backends << backendToAdd
        compute.backendServices().update(project, backendServiceName, backendService).execute()
        task.updateStatus BASE_PHASE, "Done associating server group $serverGroupName with backend service $backendServiceName."
      }
    }

    DeploymentResult deploymentResult = new DeploymentResult()
    deploymentResult.serverGroupNames = ["$region:$serverGroupName".toString()]
    deploymentResult.serverGroupNameByRegion[region] = serverGroupName
    deploymentResult
  }

  private boolean autoscalerIsSpecified(BasicGoogleDeployDescription description) {
    return description.autoscalingPolicy?.with {
      cpuUtilization || loadBalancingUtilization || customMetricUtilizations
    }
  }
}
