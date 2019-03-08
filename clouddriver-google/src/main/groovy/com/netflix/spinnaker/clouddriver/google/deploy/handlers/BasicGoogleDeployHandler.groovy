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

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.api.services.compute.Compute
import com.google.api.services.compute.model.AcceleratorConfig
import com.google.api.services.compute.model.Autoscaler
import com.google.api.services.compute.model.Backend
import com.google.api.services.compute.model.BackendService
import com.google.api.services.compute.model.DistributionPolicy
import com.google.api.services.compute.model.DistributionPolicyZoneConfiguration
import com.google.api.services.compute.model.FixedOrPercent
import com.google.api.services.compute.model.InstanceGroupManager
import com.google.api.services.compute.model.InstanceGroupManagerAutoHealingPolicy
import com.google.api.services.compute.model.InstanceProperties
import com.google.api.services.compute.model.InstanceTemplate
import com.google.api.services.compute.model.NamedPort
import com.netflix.frigga.Names
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.deploy.DeployDescription
import com.netflix.spinnaker.clouddriver.deploy.DeployHandler
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult
import com.netflix.spinnaker.clouddriver.google.GoogleCloudProvider
import com.netflix.spinnaker.clouddriver.google.GoogleExecutorTraits
import com.netflix.spinnaker.clouddriver.google.config.GoogleConfigurationProperties
import com.netflix.spinnaker.clouddriver.google.deploy.GCEServerGroupNameResolver
import com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil
import com.netflix.spinnaker.clouddriver.google.deploy.GoogleOperationPoller
import com.netflix.spinnaker.clouddriver.google.deploy.SafeRetry
import com.netflix.spinnaker.clouddriver.google.deploy.description.BasicGoogleDeployDescription
import com.netflix.spinnaker.clouddriver.google.deploy.ops.GoogleUserDataProvider
import com.netflix.spinnaker.clouddriver.google.model.GoogleLabeledResource
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleHttpLoadBalancingPolicy
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleLoadBalancerType
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleLoadBalancingPolicy
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleClusterProvider
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleLoadBalancerProvider
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleNetworkProvider
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleSubnetProvider
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.names.NamerRegistry
import com.netflix.spinnaker.config.GoogleConfiguration
import com.netflix.spinnaker.moniker.Moniker
import com.netflix.spinnaker.moniker.Namer
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import static com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil.BACKEND_SERVICE_NAMES
import static com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil.GLOBAL_LOAD_BALANCER_NAMES
import static com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil.LOAD_BALANCING_POLICY
import static com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil.REGIONAL_LOAD_BALANCER_NAMES
import static com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil.SELECT_ZONES

@Component
@Slf4j
class BasicGoogleDeployHandler implements DeployHandler<BasicGoogleDeployDescription>, GoogleExecutorTraits {

  // TODO(duftler): This should move to a common location.
  private static final String BASE_PHASE = "DEPLOY"

  // TODO(duftler): These should be exposed/configurable.
  private static final String DEFAULT_NETWORK_NAME = "default"
  private static final String ACCESS_CONFIG_NAME = "External NAT"
  private static final String ACCESS_CONFIG_TYPE = "ONE_TO_ONE_NAT"
  private static final Integer MAX_NAME_SIZE = 64 // NOTE: Experimentally determined, subject to change. See https://github.com/spinnaker/spinnaker/issues/3449.
  private static final Integer TEMPLATE_UUID_SIZE = 8

  @Autowired
  private GoogleConfigurationProperties googleConfigurationProperties

  @Autowired
  private GoogleClusterProvider googleClusterProvider

  @Autowired
  private GoogleConfiguration.DeployDefaults googleDeployDefaults

  @Autowired
  private GoogleOperationPoller googleOperationPoller

  @Autowired
  private GoogleUserDataProvider googleUserDataProvider

  @Autowired
  GoogleLoadBalancerProvider googleLoadBalancerProvider

  @Autowired
  GoogleNetworkProvider googleNetworkProvider

  @Autowired
  GoogleSubnetProvider googleSubnetProvider

  @Autowired
  String clouddriverUserAgentApplicationName

  @Autowired
  Cache cacheView

  @Autowired
  ObjectMapper objectMapper

  @Autowired
  SafeRetry safeRetry

  @Autowired
  Registry registry

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
    def region = description.region ?: credentials.regionFromZone(zone)
    def location = isRegional ? region : zone
    def instanceMetadata = description.instanceMetadata
    def labels = description.labels
    def canIpForward = description.canIpForward
    Namer<GoogleLabeledResource> namer = NamerRegistry.lookup()
      .withProvider(GoogleCloudProvider.getID())
      .withAccount(accountName)
      .withResource(GoogleLabeledResource.class)

    def serverGroupNameResolver = new GCEServerGroupNameResolver(project, region, credentials, googleClusterProvider, safeRetry, this)
    def clusterName = serverGroupNameResolver.combineAppStackDetail(description.application, description.stack, description.freeFormDetails)

    task.updateStatus BASE_PHASE, "Initializing creation of server group for cluster $clusterName in $location..."

    task.updateStatus BASE_PHASE, "Looking up next sequence..."

    def serverGroupName = serverGroupNameResolver.resolveNextServerGroupName(description.application, description.stack, description.freeFormDetails, false)
    task.updateStatus BASE_PHASE, "Produced server group name: $serverGroupName"

    def machineTypeName
    if (description.instanceType.startsWith('custom')) {
      machineTypeName = description.instanceType
    } else {
      machineTypeName = GCEUtil.queryMachineType(description.instanceType, location, credentials, task, BASE_PHASE)
    }

    def network = GCEUtil.queryNetwork(accountName, description.network ?: DEFAULT_NETWORK_NAME, task, BASE_PHASE, googleNetworkProvider)
    def subnet =
      description.subnet ? GCEUtil.querySubnet(accountName, region, description.subnet, task, BASE_PHASE, googleSubnetProvider) : null

    // If no subnet is passed and the network is both an xpn host network and an auto-subnet network, then we need to set the subnet ourselves here.
    // This shouldn't be required, but GCE complains otherwise.
    if (!subnet && network.id.contains("/") && network.autoCreateSubnets) {
      // Auto-created subnets have the same name as the containing network.
      subnet = GCEUtil.querySubnet(accountName, region, network.id, task, BASE_PHASE, googleSubnetProvider)
    }

    def targetPools = []
    def internalLoadBalancers = []
    def sslLoadBalancers = []
    def tcpLoadBalancers = []

    // We need the full url for each referenced network load balancer, and also to check that the HTTP(S)
    // load balancers exist.
    if (description.loadBalancers) {
      // GCEUtil.queryAllLoadBalancers() will throw an exception if a referenced load balancer cannot be resolved.
      def foundLoadBalancers = GCEUtil.queryAllLoadBalancers(googleLoadBalancerProvider,
                                                             description.loadBalancers,
                                                             task,
                                                             BASE_PHASE)

      // Queue ILBs to update, but wait to update metadata until Https LBs are calculated.
      internalLoadBalancers = foundLoadBalancers.findAll { it.loadBalancerType == GoogleLoadBalancerType.INTERNAL }

      // Queue SSL LBs to update.
      sslLoadBalancers = foundLoadBalancers.findAll { it.loadBalancerType == GoogleLoadBalancerType.SSL }

      // Queue TCP LBs to update.
      tcpLoadBalancers = foundLoadBalancers.findAll { it.loadBalancerType == GoogleLoadBalancerType.TCP }

      if (!description.disableTraffic) {
        def networkLoadBalancers = foundLoadBalancers.findAll { it.loadBalancerType == GoogleLoadBalancerType.NETWORK }
        targetPools = networkLoadBalancers.collect { it.targetPool }.unique()
      }
    }

    task.updateStatus BASE_PHASE, "Composing server group $serverGroupName..."

    description.baseDeviceName = serverGroupName
    def attachedDisks = GCEUtil.buildAttachedDisks(description,
                                                   null,
                                                   false,
                                                   googleDeployDefaults,
                                                   task,
                                                   BASE_PHASE,
                                                   clouddriverUserAgentApplicationName,
                                                   googleConfigurationProperties.baseImageProjects,
                                                   safeRetry,
                                                   this)

    def networkInterface = GCEUtil.buildNetworkInterface(network,
                                                         subnet,
                                                         description.associatePublicIpAddress == null || description.associatePublicIpAddress,
                                                         ACCESS_CONFIG_NAME,
                                                         ACCESS_CONFIG_TYPE)

    def hasBackendServices = (instanceMetadata &&
      instanceMetadata.containsKey(BACKEND_SERVICE_NAMES)) || sslLoadBalancers || tcpLoadBalancers

    // Resolve and queue the backend service updates, but don't execute yet.
    // We need to resolve this information to set metadata in the template so enable can know about the
    // load balancing policy this server group was configured with.
    // If we try to execute the update, GCP will fail since the MIG is not created yet.
    List<BackendService> backendServicesToUpdate = []
    if (hasBackendServices) {
      List<String> backendServices = instanceMetadata[BACKEND_SERVICE_NAMES]?.split(",") ?: []
      backendServices.addAll(sslLoadBalancers.collect { it.backendService.name })
      backendServices.addAll(tcpLoadBalancers.collect { it.backendService.name })

      // Set the load balancer name metadata.
      def globalLbNames = sslLoadBalancers.collect { it.name } + tcpLoadBalancers.collect { it.name } + GCEUtil.resolveHttpLoadBalancerNamesMetadata(backendServices, compute, project, this)
      instanceMetadata[GLOBAL_LOAD_BALANCER_NAMES] = globalLbNames.join(",")

      String sourcePolicyJson = instanceMetadata[LOAD_BALANCING_POLICY]
      def loadBalancingPolicy = description.loadBalancingPolicy

      backendServices.each { String backendServiceName ->
        BackendService backendService = timeExecute(
            compute.backendServices().get(project, backendServiceName),
            "compute.backendServices.get",
            TAG_SCOPE, SCOPE_GLOBAL)

        Backend backendToAdd
        GoogleHttpLoadBalancingPolicy policy
        if (loadBalancingPolicy?.balancingMode) {
          policy = loadBalancingPolicy
        } else if (sourcePolicyJson) {
          policy = objectMapper.readValue(sourcePolicyJson, GoogleHttpLoadBalancingPolicy)
        } else {
          log.warn("No load balancing policy found in the operation description or the source server group, adding defaults")
          policy = new GoogleHttpLoadBalancingPolicy(
              balancingMode: GoogleLoadBalancingPolicy.BalancingMode.UTILIZATION,
              maxUtilization: 0.80,
              capacityScaler: 1.0,
              namedPorts: [new NamedPort(name: GoogleHttpLoadBalancingPolicy.HTTP_DEFAULT_PORT_NAME, port: GoogleHttpLoadBalancingPolicy.HTTP_DEFAULT_PORT)]
          )
        }
        GCEUtil.updateMetadataWithLoadBalancingPolicy(policy, instanceMetadata, objectMapper)
        backendToAdd = GCEUtil.backendFromLoadBalancingPolicy(policy)

        if (isRegional) {
          backendToAdd.setGroup(GCEUtil.buildRegionalServerGroupUrl(project, region, serverGroupName))
        } else {
          backendToAdd.setGroup(GCEUtil.buildZonalServerGroupUrl(project, zone, serverGroupName))
        }

        if (backendService.backends == null) {
          backendService.backends = new ArrayList<Backend>()
        }
        backendService.backends << backendToAdd
        backendServicesToUpdate << backendService
      }
    }

    // Update the instance metadata for ILBs and queue up region backend service calls.
    List<BackendService> regionBackendServicesToUpdate = []
    if (internalLoadBalancers) {
      List<String> existingRegionalLbs = instanceMetadata[REGIONAL_LOAD_BALANCER_NAMES]?.split(",") ?: []
      def ilbServices = internalLoadBalancers.collect { it.backendService.name }
      def ilbNames = internalLoadBalancers.collect { it.name }

      ilbNames.each { String ilbName ->
        if (!(ilbName in existingRegionalLbs))  {
          existingRegionalLbs << ilbName
        }
      }
      instanceMetadata[REGIONAL_LOAD_BALANCER_NAMES] = existingRegionalLbs.join(",")

      ilbServices.each { String backendServiceName ->
        BackendService backendService = timeExecute(
            compute.regionBackendServices().get(project, region, backendServiceName),
            "compute.regionBackendServices.get",
            TAG_SCOPE, SCOPE_REGIONAL, TAG_REGION, region)
        Backend backendToAdd = new Backend()
        if (isRegional) {
          backendToAdd.setGroup(GCEUtil.buildRegionalServerGroupUrl(project, region, serverGroupName))
        } else {
          backendToAdd.setGroup(GCEUtil.buildZonalServerGroupUrl(project, zone, serverGroupName))
        }

        if (backendService.backends == null) {
          backendService.backends = new ArrayList<Backend>()
        }
        backendService.backends << backendToAdd
        regionBackendServicesToUpdate << backendService
      }
    }

    String now = System.currentTimeMillis()
    String slice = now.substring(now.size()-TEMPLATE_UUID_SIZE)
    String instanceTemplateName = "$serverGroupName-$slice"
    if (instanceTemplateName.size() > MAX_NAME_SIZE) {
      throw new IllegalArgumentException("Max name length ${MAX_NAME_SIZE} exceeded in resolved instance template name ${instanceTemplateName}.")
    }

    Map userDataMap = getUserData(description, serverGroupName, instanceTemplateName, credentials)

    if (instanceMetadata) {
      instanceMetadata << userDataMap
    } else {
      instanceMetadata = userDataMap
    }

    if (isRegional && description.selectZones) {
      instanceMetadata[SELECT_ZONES] = true
    }

    def metadata = GCEUtil.buildMetadataFromMap(instanceMetadata)

    def tags = GCEUtil.buildTagsFromList(description.tags)

    if (description.authScopes && !description.serviceAccountEmail) {
      description.serviceAccountEmail = "default"
    }

    def serviceAccount = GCEUtil.buildServiceAccount(description.serviceAccountEmail, description.authScopes)

    def scheduling = GCEUtil.buildScheduling(description)

    if (labels == null) {
      labels = [:]
    }

    // Used to group instances when querying for metrics from kayenta.
    labels['spinnaker-region'] = region
    labels['spinnaker-server-group'] = serverGroupName

    def sequence = Names.parseName(serverGroupName).sequence

    def moniker = Moniker.builder()
      .app(description.application)
      .cluster(clusterName)
      .detail(description.freeFormDetails)
      .stack(description.stack)
      .sequence(sequence)
      .build()

    // Apply moniker to labels which are subsequently recorded in the instance template.
    namer.applyMoniker(new GoogleInstanceTemplate(labels: labels), moniker)

    // Accelerators are supported for zonal server groups only.
    List<AcceleratorConfig> acceleratorConfigs = description.regional ? [] : description.acceleratorConfigs

    def instanceProperties = new InstanceProperties(machineType: machineTypeName,
                                                    disks: attachedDisks,
                                                    guestAccelerators: acceleratorConfigs ?: [],
                                                    networkInterfaces: [networkInterface],
                                                    canIpForward: canIpForward,
                                                    metadata: metadata,
                                                    tags: tags,
                                                    labels: labels,
                                                    scheduling: scheduling,
                                                    serviceAccounts: serviceAccount)

    if (description.minCpuPlatform) {
      instanceProperties.minCpuPlatform = description.minCpuPlatform
    }

    def instanceTemplate = new InstanceTemplate(name: instanceTemplateName,
                                                properties: instanceProperties)

    def instanceTemplateCreateOperation = timeExecute(
        compute.instanceTemplates().insert(project, instanceTemplate),
        "compute.instanceTemplates.insert",
        TAG_SCOPE, SCOPE_GLOBAL)
    def instanceTemplateUrl = instanceTemplateCreateOperation.targetLink

    // Before building the managed instance group we must check and wait until the instance template is built.
    googleOperationPoller.waitForGlobalOperation(compute, project, instanceTemplateCreateOperation.getName(),
        null, task, "instance template " + GCEUtil.getLocalName(instanceTemplateUrl), BASE_PHASE)

    if (description.capacity) {
      description.targetSize = description.capacity.desired
    }

    if (autoscalerIsSpecified(description)) {
      if (description.capacity) {
        description.autoscalingPolicy.minNumReplicas = description.capacity.min
        description.autoscalingPolicy.maxNumReplicas = description.capacity.max
      }

      GCEUtil.calibrateTargetSizeWithAutoscaler(description)
    }

    if (description.source?.useSourceCapacity && description.source?.region && description.source?.serverGroupName) {
      task.updateStatus BASE_PHASE, "Looking up server group $description.source.serverGroupName in $description.source.region " +
                                    "in order to copy the current capacity..."

      // Locate the ancestor server group.
      def ancestorServerGroup = GCEUtil.queryServerGroup(googleClusterProvider,
                                                         description.accountName,
                                                         description.source.region,
                                                         description.source.serverGroupName)

      description.targetSize = ancestorServerGroup.capacity.desired
      description.autoscalingPolicy = GCEUtil.buildAutoscalingPolicyDescriptionFromAutoscalingPolicy(ancestorServerGroup.autoscalingPolicy)
    }

    def autoHealingHealthCheck = null
    if (description.autoHealingPolicy?.healthCheck) {
      autoHealingHealthCheck = GCEUtil.queryHealthCheck(project, description.accountName, description.autoHealingPolicy.healthCheck, description.autoHealingPolicy.healthCheckKind, compute, cacheView, task, BASE_PHASE, this)
    }

    List<InstanceGroupManagerAutoHealingPolicy> autoHealingPolicy =
      autoHealingHealthCheck
      ? [new InstanceGroupManagerAutoHealingPolicy(
             healthCheck: autoHealingHealthCheck.selfLink,
             initialDelaySec: description.autoHealingPolicy.initialDelaySec)]
      : null

    if (autoHealingPolicy && description.autoHealingPolicy.maxUnavailable) {
      def maxUnavailable = new FixedOrPercent(fixed: description.autoHealingPolicy.maxUnavailable.fixed as Integer,
                                              percent: description.autoHealingPolicy.maxUnavailable.percent as Integer)

      autoHealingPolicy[0].setMaxUnavailable(maxUnavailable)
    }

    def migCreateOperation
    def instanceGroupManager = new InstanceGroupManager()
        .setName(serverGroupName)
        .setBaseInstanceName(serverGroupName)
        .setInstanceTemplate(instanceTemplateUrl)
        .setTargetSize(description.targetSize)
        .setTargetPools(targetPools)
        .setAutoHealingPolicies(autoHealingPolicy)

    if (hasBackendServices && (description?.loadBalancingPolicy || description?.source?.serverGroupName))  {
      List<NamedPort> namedPorts = []
      def sourceGroupName = description?.source?.serverGroupName

      // Note: this favors the explicitly specified load balancing policy over the source server group.
      if (sourceGroupName && !description?.loadBalancingPolicy) {
        def sourceServerGroup = googleClusterProvider.getServerGroup(description.accountName, description.source.region, sourceGroupName)
        if (!sourceServerGroup) {
          log.warn("Could not locate source server group ${sourceGroupName} to update named port.")
        }
        namedPorts = sourceServerGroup?.namedPorts?.collect { name, port -> new NamedPort(name: name, port: port) }
      } else {
        def loadBalancingPolicy = description?.loadBalancingPolicy
        if (loadBalancingPolicy?.namedPorts != null)  {
          namedPorts = description?.loadBalancingPolicy?.namedPorts
        } else if (loadBalancingPolicy?.listeningPort) {
          log.warn("Deriving named ports from deprecated 'listeningPort' attribute. Please update your deploy description to use 'namedPorts'.")
          namedPorts = [new NamedPort(name: GoogleHttpLoadBalancingPolicy.HTTP_DEFAULT_PORT_NAME, port: loadBalancingPolicy?.listeningPort)]
        }
      }

      if (!namedPorts) {
        log.warn("Could not locate named port on either load balancing policy or source server group. Setting default named port.")
        namedPorts = [new NamedPort(name: GoogleHttpLoadBalancingPolicy.HTTP_DEFAULT_PORT_NAME, port: GoogleHttpLoadBalancingPolicy.HTTP_DEFAULT_PORT)]
      }
      instanceGroupManager.setNamedPorts(namedPorts)
    }

    def willUpdateBackendServices = !description.disableTraffic && hasBackendServices
    def willCreateAutoscaler = autoscalerIsSpecified(description)
    def willUpdateIlbs = !description.disableTraffic && internalLoadBalancers

    if (isRegional) {
      if (description.selectZones && description.distributionPolicy && description.distributionPolicy.zones) {
        log.info("Configuring explicit zones selected for regional server group: ${description.distributionPolicy.zones}")
        List<DistributionPolicyZoneConfiguration> selectedZones = description.distributionPolicy.zones.collect { String z ->
          new DistributionPolicyZoneConfiguration().setZone(GCEUtil.buildZoneUrl(project, z))
        }
        DistributionPolicy distributionPolicy = new DistributionPolicy().setZones(selectedZones)
        instanceGroupManager.setDistributionPolicy(distributionPolicy)
      }
      migCreateOperation = timeExecute(
          compute.regionInstanceGroupManagers().insert(project, region, instanceGroupManager),
          "compute.regionInstanceGroupManagers.insert",
          TAG_SCOPE, SCOPE_REGIONAL, TAG_REGION, region)

      if (willUpdateBackendServices || willCreateAutoscaler || willUpdateIlbs) {
        // Before updating the Backend Services or creating the Autoscaler we must wait until the managed instance group is created.
        googleOperationPoller.waitForRegionalOperation(compute, project, region, migCreateOperation.getName(),
          null, task, "managed instance group $serverGroupName", BASE_PHASE)

        if (willCreateAutoscaler) {
          task.updateStatus BASE_PHASE, "Creating regional autoscaler for $serverGroupName..."

          Autoscaler autoscaler = GCEUtil.buildAutoscaler(serverGroupName,
                                                          migCreateOperation.targetLink,
                                                          description.autoscalingPolicy)

          timeExecute(
              compute.regionAutoscalers().insert(project, region, autoscaler),
              "compute.regionAutoscalers.insert",
              TAG_SCOPE, SCOPE_REGIONAL, TAG_REGION, region)
        }
      }
    } else {
      migCreateOperation = timeExecute(
          compute.instanceGroupManagers().insert(project, zone, instanceGroupManager),
          "compute.instanceGroupManagers.insert",
          TAG_SCOPE, SCOPE_ZONAL, TAG_ZONE, zone)

      if (willUpdateBackendServices || willCreateAutoscaler || willUpdateIlbs) {
        // Before updating the Backend Services or creating the Autoscaler we must wait until the managed instance group is created.
        googleOperationPoller.waitForZonalOperation(compute, project, zone, migCreateOperation.getName(),
          null, task, "managed instance group $serverGroupName", BASE_PHASE)

        if (willCreateAutoscaler) {
          task.updateStatus BASE_PHASE, "Creating zonal autoscaler for $serverGroupName..."

          Autoscaler autoscaler = GCEUtil.buildAutoscaler(serverGroupName,
                                                          migCreateOperation.targetLink,
                                                          description.autoscalingPolicy)

          timeExecute(compute.autoscalers().insert(project, zone, autoscaler),
                      "compute.autoscalers.insert",
                      TAG_SCOPE, SCOPE_ZONAL, TAG_ZONE, zone)
        }
      }
    }

    task.updateStatus BASE_PHASE, "Done creating server group $serverGroupName in $location."

    // Actually update the backend services.
    if (willUpdateBackendServices) {
      backendServicesToUpdate.each { BackendService backendService ->
        safeRetry.doRetry(
          updateBackendServices(compute, project, backendService.name, backendService),
          "Load balancer backend service",
          task,
          [400, 412],
          [],
          [action: "update", phase: BASE_PHASE, operation: "updateBackendServices", (TAG_SCOPE): SCOPE_GLOBAL],
          registry
        )
        task.updateStatus BASE_PHASE, "Done associating server group $serverGroupName with backend service ${backendService.name}."
      }
    }

    if (willUpdateIlbs) {
      regionBackendServicesToUpdate.each { BackendService backendService ->
        safeRetry.doRetry(
          updateRegionBackendServices(compute, project, region, backendService.name, backendService),
          "Internal load balancer backend service",
          task,
          [400, 412],
          [],
          [action: "update", phase: BASE_PHASE, operation: "updateRegionBackendServices", (TAG_SCOPE): SCOPE_REGIONAL, (TAG_REGION): region],
          registry
        )
        task.updateStatus BASE_PHASE, "Done associating server group $serverGroupName with backend service ${backendService.name}."
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

  private Closure updateRegionBackendServices(Compute compute, String project, String region, String backendServiceName, BackendService backendService) {
    return {
      BackendService serviceToUpdate = timeExecute(
         compute.regionBackendServices().get(project, region, backendServiceName),
         "compute.regionBackendServices.get",
         TAG_SCOPE, SCOPE_REGIONAL, TAG_REGION, region)
      if (serviceToUpdate.backends == null) {
        serviceToUpdate.backends = new ArrayList<Backend>()
      }
      backendService?.backends?.each { serviceToUpdate.backends << it }
      serviceToUpdate.getBackends().unique { backend -> backend.group }
      timeExecute(
          compute.regionBackendServices().update(project, region, backendServiceName, serviceToUpdate),
          "compute.regionBackendServices.update",
          TAG_SCOPE, SCOPE_REGIONAL, TAG_REGION, region)
      null
    }
  }

  private Closure updateBackendServices(Compute compute, String project, String backendServiceName, BackendService backendService) {
    return {
      BackendService serviceToUpdate = timeExecute(
          compute.backendServices().get(project, backendServiceName),
          "compute.backendServices.get",
          TAG_SCOPE, SCOPE_GLOBAL)
      if (serviceToUpdate.backends == null) {
        serviceToUpdate.backends = new ArrayList<Backend>()
      }
      backendService?.backends?.each { serviceToUpdate.backends << it }
      serviceToUpdate.getBackends().unique { backend -> backend.group }
      timeExecute(
          compute.backendServices().update(project, backendServiceName, serviceToUpdate),
          "compute.backendServices.update",
          TAG_SCOPE, SCOPE_GLOBAL)
      null
    }
  }

  // todo(lwander): move to kork
  private static Moniker cloneMoniker(Moniker inp) {
    if (inp == null) {
      return new Moniker()
    }
    return Moniker.builder()
      .app(inp.getApp())
      .cluster(inp.getCluster())
      .stack(inp.getStack())
      .detail(inp.getDetail())
      .sequence(inp.getSequence())
      .build()
  }

  Map getUserData(BasicGoogleDeployDescription description, String serverGroupName,
                  String instanceTemplateName, GoogleNamedAccountCredentials credentials) {
    String customUserData = ''
    if (description.userData) {
      customUserData = description.userData
    }
    Map userData = googleUserDataProvider.getUserData(serverGroupName, instanceTemplateName,
      description, credentials, customUserData)
    task.updateStatus BASE_PHASE, "Resolved user data."
    return userData
  }

  static class GoogleInstanceTemplate implements GoogleLabeledResource {
    Map<String, String> labels
  }
}
