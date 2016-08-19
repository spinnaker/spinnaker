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

package com.netflix.spinnaker.clouddriver.google.deploy

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.api.client.googleapis.batch.BatchRequest
import com.google.api.client.googleapis.batch.json.JsonBatchCallback
import com.google.api.client.googleapis.json.GoogleJsonError
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.HttpHeaders
import com.google.api.client.http.HttpRequest
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.services.compute.Compute
import com.google.api.services.compute.model.*
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.google.GoogleConfiguration
import com.netflix.spinnaker.clouddriver.google.deploy.description.*
import com.netflix.spinnaker.clouddriver.google.deploy.exception.GoogleOperationException
import com.netflix.spinnaker.clouddriver.google.deploy.exception.GoogleOperationTimedOutException
import com.netflix.spinnaker.clouddriver.google.deploy.exception.GoogleResourceNotFoundException
import com.netflix.spinnaker.clouddriver.google.model.*
import com.netflix.spinnaker.clouddriver.google.model.callbacks.Utils
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleHttpLoadBalancingPolicy
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleLoadBalancerType
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleLoadBalancerView
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleClusterProvider
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleLoadBalancerProvider
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleSecurityGroupProvider
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import groovy.util.logging.Slf4j

import java.util.concurrent.TimeUnit

@Slf4j
class GCEUtil {
  private static final String DISK_TYPE_PERSISTENT = "PERSISTENT"
  private static final String DISK_TYPE_SCRATCH = "SCRATCH"

  public static final String TARGET_POOL_NAME_PREFIX = "tp"

  static MachineType queryMachineType(String projectName, String machineTypeName, Compute compute, Task task, String phase) {
    task.updateStatus phase, "Looking up machine type $machineTypeName..."

    Map<String, MachineTypesScopedList> zoneToMachineTypesMap = compute.machineTypes().aggregatedList(projectName).execute().items

    def machineType = zoneToMachineTypesMap.collect { _, machineTypesScopedList ->
      machineTypesScopedList.machineTypes
    }.flatten().find { machineType ->
      machineType.name == machineTypeName
    }

    if (machineType) {
      return machineType
    } else {
      updateStatusAndThrowNotFoundException("Machine type $machineTypeName not found.", task, phase)
    }
  }

  static Image querySourceImage(String projectName,
                                BaseGoogleInstanceDescription description,
                                Compute compute,
                                Task task,
                                String phase,
                                String googleApplicationName,
                                List<String> baseImageProjects) {
    task.updateStatus phase, "Looking up source image $description.image..."

    def imageProjects = [projectName] + description.credentials?.imageProjects + baseImageProjects - null
    def sourceImageName = description.image
    def sourceImage = null

    def imageListBatch = buildBatchRequest(compute, googleApplicationName)
    def imageListCallback = new JsonBatchCallback<ImageList>() {
      @Override
      void onFailure(GoogleJsonError e, HttpHeaders responseHeaders) throws IOException {
        updateStatusAndThrowNotFoundException("Error locating $sourceImageName in these projects: $imageProjects: $e.message", task, phase)
      }

      @Override
      void onSuccess(ImageList imageList, HttpHeaders responseHeaders) throws IOException {
        // No need to look through these images if the requested image was already found.
        if (!sourceImage) {
          for (def image : imageList.items) {
            if (image.name == sourceImageName) {
              sourceImage = image
            }
          }
        }
      }
    }

    for (imageProject in imageProjects) {
      compute.images().list(imageProject).queue(imageListBatch, imageListCallback)
    }

    imageListBatch.execute()

    if (sourceImage) {
      return sourceImage
    } else {
      updateStatusAndThrowNotFoundException("Source image $sourceImageName not found in any of these projects: $imageProjects.", task, phase)
    }
  }

  private static BatchRequest buildBatchRequest(def compute, def googleApplicationName) {
    return compute.batch(
      new HttpRequestInitializer() {
        @Override
        void initialize(HttpRequest request) throws IOException {
          request.headers.setUserAgent(googleApplicationName);
        }
      }
    )
  }

  static Network queryNetwork(String projectName, String networkName, Compute compute, Task task, String phase) {
    task.updateStatus phase, "Looking up network $networkName..."
    def network = compute.networks().list(projectName).execute().getItems().find {
      it.getName() == networkName
    }

    if (network) {
      return network
    } else {
      updateStatusAndThrowNotFoundException("Network $networkName not found.", task, phase)
    }
  }

  static Subnetwork querySubnet(String projectName, String region, String subnetName, Compute compute, Task task, String phase) {
    task.updateStatus phase, "Looking up subnet $subnetName in $region..."

    try {
      return compute.subnetworks().get(projectName, region, subnetName).execute()
    } catch (GoogleJsonResponseException e) {
      // 404 is thrown, and the details are populated, if the subnet does not exist in the given region.
      // Any other exception should be propagated directly.
      if (e.getStatusCode() == 404 && e.details) {
        updateStatusAndThrowNotFoundException("Subnet $subnetName not found in $region.", task, phase)
      } else {
        throw e
      }
    }
  }

  // If a forwarding rule with the specified name is found in any region, it is returned.
  static ForwardingRule queryRegionalForwardingRule(
    String projectName, String forwardingRuleName, Compute compute, Task task, String phase) {
    task.updateStatus phase, "Checking for existing network load balancer (forwarding rule) $forwardingRuleName..."

    // Try to retrieve this forwarding rule in each region.
    for (def region : compute.regions().list(projectName).execute().items) {
      try {
        return compute.forwardingRules().get(projectName, region.name, forwardingRuleName).execute()
      } catch (GoogleJsonResponseException e) {
        // 404 is thrown if the forwarding rule does not exist in the given region. Any other exception needs to be propagated.
        if (e.getStatusCode() != 404) {
          throw e
        }
      }
    }
  }

  static TargetPool queryTargetPool(
    String projectName, String region, String targetPoolName, Compute compute, Task task, String phase) {
    task.updateStatus phase, "Checking for existing network load balancer (target pool) $targetPoolName..."

    return compute.targetPools().list(projectName, region).execute().items.find { existingTargetPool ->
      existingTargetPool.name == targetPoolName
    }
  }

  // TODO(duftler): Update this to query for the exact health check instead of searching all.
  static HttpHealthCheck queryHttpHealthCheck(
    String projectName, String httpHealthCheckName, Compute compute, Task task, String phase) {
    task.updateStatus phase, "Checking for existing network load balancer (http health check) $httpHealthCheckName..."

    return compute.httpHealthChecks().list(projectName).execute().items.find { existingHealthCheck ->
      existingHealthCheck.name == httpHealthCheckName
    }
  }

  static def queryHealthCheck(String projectName, String healthCheckName, Compute compute, Task task, String phase) {
    task.updateStatus phase, "Looking up http(s) health check $healthCheckName..."

    try {
      return compute.httpHealthChecks().get(projectName, healthCheckName).execute()
    } catch (GoogleJsonResponseException e) {
      // 404 is thrown, and the details are populated, if the health check does not exist in the given project.
      // Any other exception should be propagated directly.
      if (e.getStatusCode() == 404 && e.details) {
        try {
          return compute.httpsHealthChecks().get(projectName, healthCheckName).execute()
        } catch (GoogleJsonResponseException exc) {
          if (exc.getStatusCode() == 404 && exc.details) {
            updateStatusAndThrowNotFoundException("Http(s) health check $healthCheckName not found.", task, phase)
          } else {
            throw e
          }
        }
      } else {
        throw e
      }
    }
  }

  static List<ForwardingRule> queryForwardingRules(
          String projectName, String region, List<String> forwardingRuleNames, Compute compute, Task task, String phase) {
    task.updateStatus phase, "Looking up network load balancers $forwardingRuleNames..."

    def foundForwardingRules = compute.forwardingRules().list(projectName, region).execute().items.findAll {
      it.name in forwardingRuleNames
    }

    if (foundForwardingRules.size == forwardingRuleNames.size) {
      return foundForwardingRules
    } else {
      def foundNames = foundForwardingRules.collect { it.name }

      updateStatusAndThrowNotFoundException("Network load balancers ${forwardingRuleNames - foundNames} not found.", task, phase)
    }
  }

  static List<GoogleLoadBalancerView> queryAllLoadBalancers(GoogleLoadBalancerProvider googleLoadBalancerProvider,
                                                            List<String> forwardingRuleNames,
                                                            Task task,
                                                            String phase) {
    def loadBalancers = googleLoadBalancerProvider.getApplicationLoadBalancers("") as List
    def foundLoadBalancers = loadBalancers.findAll { it.name in forwardingRuleNames }

    if (foundLoadBalancers.size == forwardingRuleNames.size) {
      return foundLoadBalancers
    } else {
      def foundNames = loadBalancers.collect { it.name }
      updateStatusAndThrowNotFoundException("Load balancers ${forwardingRuleNames - foundNames} not found.", task, phase)
    }
  }

  static List<String> queryInstanceUrls(String projectName,
                                        String region,
                                        List<String> instanceLocalNames,
                                        Compute compute,
                                        Task task,
                                        String phase) {
    task.updateStatus phase, "Looking up instances $instanceLocalNames..."

    Map<String, InstancesScopedList> zoneToInstancesMap = compute.instances().aggregatedList(projectName).execute().items

    // Build up a list of all instances in the specified region with a name specified in instanceLocalNames:
    //   1) Build a list of lists where each sublist represents the matching instances in one zone.
    //   2) Flatten the list of lists into a one-level list.
    //   3) Remove any null entries (null entries are possible because .collect() still accumulates an element even if
    //      the conditional evaluates to false; it's just a null element).
    def foundInstances = zoneToInstancesMap.collect { zone, instanceList ->
      if (zone.startsWith("zones/$region-") && instanceList.instances) {
        return instanceList.instances.findAll { instance ->
          return instanceLocalNames.contains(instance.name)
        }
      }
    }.flatten() - null

    if (foundInstances.size == instanceLocalNames.size) {
      return foundInstances.collect { it.selfLink }
    } else {
      def foundNames = foundInstances.collect { it.name }

      updateStatusAndThrowNotFoundException("Instances ${instanceLocalNames - foundNames} not found.", task, phase)
    }
  }

  static InstanceGroupManager queryRegionalManagedInstanceGroup(String projectName,
                                                                String region,
                                                                String serverGroupName,
                                                                GoogleNamedAccountCredentials credentials) {
    credentials.compute.regionInstanceGroupManagers().get(projectName, region, serverGroupName).execute()
  }

  static InstanceGroupManager queryZonalManagedInstanceGroup(String projectName,
                                                             String zone,
                                                             String serverGroupName,
                                                             GoogleNamedAccountCredentials credentials) {
    credentials.compute.instanceGroupManagers().get(projectName, zone, serverGroupName).execute()
  }

  static List<InstanceGroupManager> queryRegionalManagedInstanceGroups(String projectName,
                                                                       String region,
                                                                       GoogleNamedAccountCredentials credentials) {
    return credentials.compute.regionInstanceGroupManagers().list(projectName, region).execute().getItems()
  }

  static List<InstanceGroupManager> queryZonalManagedInstanceGroups(String projectName,
                                                                    String region,
                                                                    GoogleNamedAccountCredentials credentials) {
    def compute = credentials.compute
    def zones = getZonesFromRegion(projectName, region, compute)

    def allMIGSInRegion = zones.findResults {
      def localZoneName = getLocalName(it)

      compute.instanceGroupManagers().list(projectName, localZoneName).execute().getItems()
    }.flatten()

    return allMIGSInRegion
  }

  static Set<String> querySecurityGroupTags(Set<String> securityGroupNames,
                                            String accountName,
                                            GoogleSecurityGroupProvider googleSecurityGroupProvider,
                                            Task task,
                                            String phase) {
    if (!securityGroupNames) {
      return null
    }

    task.updateStatus phase, "Looking up firewall rules $securityGroupNames..."

    Set<GoogleSecurityGroup> securityGroups = googleSecurityGroupProvider.getAllByAccount(false, accountName)

    Set<GoogleSecurityGroup> securityGroupMatches = securityGroups.findAll { securityGroup ->
      securityGroupNames.contains(securityGroup.name)
    }

    if (securityGroupMatches.size() == securityGroupNames.size()) {
      return securityGroupMatches.collect { securityGroupMatch ->
        securityGroupMatch.targetTags
      }.flatten() - null
    } else {
      def securityGroupNameMatches = securityGroupMatches.collect { it.name }

      updateStatusAndThrowNotFoundException("Firewall rules ${securityGroupNames - securityGroupNameMatches} not found.", task, phase)
    }

    return securityGroups.findAll { securityGroup ->
      securityGroupNames.contains(securityGroup.name)
    }.collect { securityGroup ->
      securityGroup.targetTags
    }.flatten() - null
  }

  static GoogleServerGroup.View queryServerGroup(GoogleClusterProvider googleClusterProvider, String accountName, String region, String serverGroupName) {
    def serverGroup = googleClusterProvider.getServerGroup(accountName, region, serverGroupName)

    if (!serverGroup) {
      throw new GoogleResourceNotFoundException("Unable to locate server group $serverGroupName in $region.")
    }

    return serverGroup
  }

  static List<String> collectInstanceUrls(GoogleServerGroup.View serverGroup, List<String> instanceIds) {
    return serverGroup.instances.findAll {
      instanceIds.contains(it.instanceId)
    }.collect {
      it.selfLink
    }
  }

  static List<String> mergeDescriptionAndSecurityGroupTags(List<String> tags, Set<String> securityGroupTags) {
    return ((tags ?: []) + securityGroupTags).unique()
  }

  static String getRegionFromZone(String projectName, String zone, Compute compute) {
    // Zone.getRegion() returns a full URL reference.
    def fullRegion = compute.zones().get(projectName, zone).execute().getRegion()
    // Even if getRegion() is changed to return just the unqualified region name, this will still work.
    getLocalName(fullRegion)
  }

  static List<String> getZonesFromRegion(String projectName, String region, Compute compute) {
    return compute.regions().get(projectName, region).execute().getZones()
  }

  static BaseGoogleInstanceDescription buildInstanceDescriptionFromTemplate(InstanceTemplate instanceTemplate) {
    def instanceTemplateProperties = instanceTemplate?.properties

    if (instanceTemplateProperties == null) {
      throw new GoogleOperationException("Unable to determine properties of instance template " +
          "$instanceTemplate.name.")
    }

    if (instanceTemplateProperties.networkInterfaces?.size != 1) {
      throw new GoogleOperationException("Instance templates must have exactly one network interface defined. " +
          "Instance template $instanceTemplate.name has ${instanceTemplateProperties.networkInterfaces?.size}.")
    }

    def image
    def disks

    if (instanceTemplateProperties.disks) {
      def bootDisk = instanceTemplateProperties.disks.find { it.getBoot() }

      image = getLocalName(bootDisk?.initializeParams?.sourceImage)
      disks = instanceTemplateProperties.disks.collect { attachedDisk ->
        def initializeParams = attachedDisk.initializeParams

        new GoogleDisk(type: initializeParams.diskType,
                       sizeGb: initializeParams.diskSizeGb,
                       autoDelete: attachedDisk.autoDelete)
      }
    } else {
      throw new GoogleOperationException("Instance templates must have at least one disk defined. Instance template " +
          "$instanceTemplate.name has ${instanceTemplateProperties.disks?.size}.")
    }

    def networkInterface = instanceTemplateProperties.networkInterfaces[0]
    def serviceAccountEmail = instanceTemplateProperties.serviceAccounts?.getAt(0)?.email

    return new BaseGoogleInstanceDescription(
      image: image,
      instanceType: instanceTemplateProperties.machineType,
      disks: disks,
      instanceMetadata: instanceTemplateProperties.metadata?.items?.collectEntries {
        [it.key, it.value]
      },
      tags: instanceTemplateProperties.tags?.items,
      network: getLocalName(networkInterface.network),
      serviceAccountEmail: serviceAccountEmail,
      authScopes: retrieveScopesFromServiceAccount(serviceAccountEmail, instanceTemplateProperties.serviceAccounts)
    )
  }

  static GoogleAutoscalingPolicy buildAutoscalingPolicyDescriptionFromAutoscalingPolicy(
    AutoscalingPolicy autoscalingPolicy) {
    if (!autoscalingPolicy) {
      return null
    }

    autoscalingPolicy.with {
      def autoscalingPolicyDescription =
          new GoogleAutoscalingPolicy(
              coolDownPeriodSec: coolDownPeriodSec,
              minNumReplicas: minNumReplicas,
              maxNumReplicas: maxNumReplicas
          )

      if (cpuUtilization) {
        autoscalingPolicyDescription.cpuUtilization =
            new GoogleAutoscalingPolicy.CpuUtilization(
                utilizationTarget: cpuUtilization.utilizationTarget
            )
      }

      if (loadBalancingUtilization) {
        autoscalingPolicyDescription.loadBalancingUtilization =
            new GoogleAutoscalingPolicy.LoadBalancingUtilization(
                utilizationTarget: loadBalancingUtilization.utilizationTarget
            )
      }

      if (customMetricUtilizations) {
        autoscalingPolicyDescription.customMetricUtilizations =
            customMetricUtilizations.collect {
              new GoogleAutoscalingPolicy.CustomMetricUtilization(
                  metric: it.metric,
                  utilizationTarget: it.utilizationTarget,
                  utilizationTargetType: it.utilizationTargetType
              )
            }
      }

      return autoscalingPolicyDescription
    }
  }

  static BasicGoogleDeployDescription.AutoHealingPolicy buildAutoHealingPolicyDescriptionFromAutoHealingPolicy(
    InstanceGroupManagerAutoHealingPolicy autoHealingPolicy) {
    if (!autoHealingPolicy) {
      return null
    }

    return new BasicGoogleDeployDescription.AutoHealingPolicy(
      healthCheck: Utils.getLocalName(autoHealingPolicy.healthCheck),
      initialDelaySec: autoHealingPolicy.initialDelaySec
    )
  }

  static List<String> retrieveScopesFromServiceAccount(String serviceAccountEmail, List<ServiceAccount> serviceAccounts) {
    return serviceAccountEmail ? serviceAccounts?.find { it.email == serviceAccountEmail }?.scopes : null
  }

  static String buildDiskTypeUrl(String projectName, String zone, GoogleDiskType diskType) {
    return "https://www.googleapis.com/compute/v1/projects/$projectName/zones/$zone/diskTypes/$diskType"
  }

  static String buildZonalServerGroupUrl(String projectName, String zone, String serverGroupName) {
    return "https://www.googleapis.com/compute/v1/projects/$projectName/zones/$zone/instanceGroups/$serverGroupName"
  }

  static String buildRegionalServerGroupUrl(String projectName, String region, String serverGroupName) {
    return "https://www.googleapis.com/compute/v1/projects/$projectName/regions/$region/instanceGroups/$serverGroupName"
  }

  static List<AttachedDisk> buildAttachedDisks(String projectName,
                                               String zone,
                                               Image sourceImage,
                                               List<GoogleDisk> disks,
                                               boolean useDiskTypeUrl,
                                               String instanceType,
                                               GoogleConfiguration.DeployDefaults deployDefaults) {
    if (!disks) {
      disks = deployDefaults.determineInstanceTypeDisk(instanceType).disks
    }

    if (!disks) {
      throw new GoogleOperationException("Unable to determine disks for instance type $instanceType.")
    }

    def firstPersistentDisk = disks.find { it.persistent }

    if (firstPersistentDisk && sourceImage.diskSizeGb > firstPersistentDisk.sizeGb) {
      firstPersistentDisk.sizeGb = sourceImage.diskSizeGb
    }

    return disks.collect { disk ->
      def diskType = useDiskTypeUrl ? buildDiskTypeUrl(projectName, zone, disk.type) : disk.type
      def attachedDiskInitializeParams =
        new AttachedDiskInitializeParams(sourceImage: disk.is(firstPersistentDisk) ? sourceImage.selfLink : null,
                                         diskSizeGb: disk.sizeGb,
                                         diskType: diskType)

      new AttachedDisk(boot: disk.is(firstPersistentDisk),
                       autoDelete: disk.autoDelete,
                       type: disk.persistent ? DISK_TYPE_PERSISTENT : DISK_TYPE_SCRATCH,
                       initializeParams: attachedDiskInitializeParams)
    }
  }

  static NetworkInterface buildNetworkInterface(Network network,
                                                Subnetwork subnet,
                                                String accessConfigName,
                                                String accessConfigType) {
    def accessConfig = new AccessConfig(name: accessConfigName, type: accessConfigType)

    return new NetworkInterface(network: network.selfLink,
                                subnetwork: subnet ? subnet.selfLink : null,
                                accessConfigs: [accessConfig])
  }

  static Metadata buildMetadataFromMap(Map<String, String> instanceMetadata) {
    def itemsList = []

    if (instanceMetadata != null) {
      itemsList = instanceMetadata.collect { key, value ->
        new Metadata.Items(key: key, value: value)
      }
    }

    return new Metadata(items: itemsList)
  }

  static Map<String, String> buildMapFromMetadata(Metadata metadata) {
    def map = metadata?.items?.collectEntries { def metadataItems ->
      [(metadataItems.key): metadataItems.value]
    }

    return map ?: [:]
  }

  static Tags buildTagsFromList(List<String> tagsList) {
    return new Tags(items: tagsList)
  }


  static Autoscaler buildAutoscaler(String serverGroupName,
                                    String targetLink,
                                    GoogleAutoscalingPolicy autoscalingPolicy) {
    autoscalingPolicy.with {
      def gceAutoscalingPolicy = new AutoscalingPolicy(coolDownPeriodSec: coolDownPeriodSec,
                                                       minNumReplicas: minNumReplicas,
                                                       maxNumReplicas: maxNumReplicas)

      if (cpuUtilization) {
        gceAutoscalingPolicy.cpuUtilization =
            new AutoscalingPolicyCpuUtilization(utilizationTarget: cpuUtilization.utilizationTarget)
      }

      if (loadBalancingUtilization) {
        gceAutoscalingPolicy.loadBalancingUtilization =
            new AutoscalingPolicyLoadBalancingUtilization(utilizationTarget: loadBalancingUtilization.utilizationTarget)
      }

      if (customMetricUtilizations) {
        gceAutoscalingPolicy.customMetricUtilizations = customMetricUtilizations.collect {
          new AutoscalingPolicyCustomMetricUtilization(metric: it.metric,
                                                       utilizationTarget: it.utilizationTarget,
                                                       utilizationTargetType: it.utilizationTargetType)
        }
      }

      new Autoscaler(name: serverGroupName,
                     target: targetLink,
                     autoscalingPolicy: gceAutoscalingPolicy)
    }
  }

  static void calibrateTargetSizeWithAutoscaler(BasicGoogleDeployDescription description) {
    description.autoscalingPolicy.with {
      if (description.targetSize < minNumReplicas) {
        description.targetSize = minNumReplicas
      } else if (description.targetSize > maxNumReplicas) {
        description.targetSize = maxNumReplicas
      }
    }
  }

  static List<String> resolveAuthScopes(List<String> authScopes) {
    return authScopes?.collect { authScope ->
      authScope.startsWith("https://") ? authScope : "https://www.googleapis.com/auth/$authScope".toString()
    }
  }

  // We only support zero or one service account per instance/instance-template.
  static List<ServiceAccount> buildServiceAccount(String serviceAccountEmail, List<String> authScopes) {
    return serviceAccountEmail && authScopes
           ? [new ServiceAccount(email: serviceAccountEmail, scopes: resolveAuthScopes(authScopes))]
           : []
  }

  static ServiceAccount buildScheduling(BaseGoogleInstanceDescription description) {
    def scheduling = new Scheduling()

    if (description.preemptible != null) {
      scheduling.preemptible = description.preemptible
    }

    if (description.automaticRestart != null) {
      scheduling.automaticRestart = description.automaticRestart
    }

    if (description.onHostMaintenance) {
      scheduling.onHostMaintenance = description.onHostMaintenance
    }

    return scheduling
  }

  static void updateStatusAndThrowNotFoundException(String errorMsg, Task task, String phase) {
    task.updateStatus phase, errorMsg
    throw new GoogleResourceNotFoundException(errorMsg)
  }

  public static String getLocalName(String fullUrl) {
    if (!fullUrl) {
      return fullUrl
    }

    def urlParts = fullUrl.split("/")

    return urlParts[urlParts.length - 1]
  }

  static def buildHttpHealthCheck(String name, UpsertGoogleLoadBalancerDescription.HealthCheck healthCheckDescription) {
    return new HttpHealthCheck(
        name: name,
        checkIntervalSec: healthCheckDescription.checkIntervalSec,
        timeoutSec: healthCheckDescription.timeoutSec,
        healthyThreshold: healthCheckDescription.healthyThreshold,
        unhealthyThreshold: healthCheckDescription.unhealthyThreshold,
        port: healthCheckDescription.port,
        requestPath: healthCheckDescription.requestPath)
  }

  // I know this is painfully similar to the method above. I will soon make a cleanup change to remove this ugliness.
  // TODO(bklingher): Clean this up.
  static def makeHttpHealthCheck(String name, CreateGoogleHttpLoadBalancerDescription.HealthCheck healthCheckDescription) {
    if (healthCheckDescription) {
      return new HttpHealthCheck(
          name: name,
          checkIntervalSec: healthCheckDescription.checkIntervalSec,
          timeoutSec: healthCheckDescription.timeoutSec,
          healthyThreshold: healthCheckDescription.healthyThreshold,
          unhealthyThreshold: healthCheckDescription.unhealthyThreshold,
          port: healthCheckDescription.port,
          requestPath: healthCheckDescription.requestPath)
    } else {
      return new HttpHealthCheck(
          name: name
      )
    }
  }

  static void addHttpLoadBalancerBackends(Compute compute,
                                          ObjectMapper objectMapper,
                                          String project,
                                          GoogleServerGroup.View serverGroup,
                                          GoogleLoadBalancerProvider googleLoadBalancerProvider,
                                          Task task,
                                          String phase) {
    String serverGroupName = serverGroup.name
    Metadata instanceMetadata = serverGroup?.launchConfig?.instanceTemplate?.properties?.metadata
    Map metadataMap = buildMapFromMetadata(instanceMetadata)
    def httpLoadBalancersInMetadata = metadataMap?.(GoogleServerGroup.View.GLOBAL_LOAD_BALANCER_NAMES)?.tokenize(",") ?: []
    def networkLoadBalancersInMetadata = metadataMap?.(GoogleServerGroup.View.REGIONAL_LOAD_BALANCER_NAMES)?.tokenize(",") ?: []

    def allFoundLoadBalancers = (httpLoadBalancersInMetadata + networkLoadBalancersInMetadata) as List<String>
    def httpLoadBalancersToAddTo = queryAllLoadBalancers(googleLoadBalancerProvider, allFoundLoadBalancers, task, phase)
        .findAll { GoogleLoadBalancerType.valueOf(it.loadBalancerType) == GoogleLoadBalancerType.HTTP }

    if (httpLoadBalancersToAddTo) {
      String policyJson = metadataMap?.(GoogleServerGroup.View.LOAD_BALANCING_POLICY)
      if (!policyJson) {
        updateStatusAndThrowNotFoundException("Load Balancing Policy not found for server group ${serverGroupName}", task, phase)
      }
      GoogleHttpLoadBalancingPolicy policy = objectMapper.readValue(policyJson, GoogleHttpLoadBalancingPolicy)

      List<String> backendServiceNames = metadataMap?.(GoogleServerGroup.View.BACKEND_SERVICE_NAMES)?.split(",") ?: []
      if (backendServiceNames) {
        backendServiceNames.each { String backendServiceName ->
          BackendService backendService = compute.backendServices().get(project, backendServiceName).execute()
          Backend backendToAdd = backendFromLoadBalancingPolicy(policy)
          if (serverGroup.regional) {
            backendToAdd.setGroup(buildRegionalServerGroupUrl(project, serverGroup.region, serverGroupName))
          } else {
            backendToAdd.setGroup(buildZonalServerGroupUrl(project, serverGroup.zone, serverGroupName))
          }
          if (backendService.backends == null) {
            backendService.backends = []
          }
          backendService.backends << backendToAdd
          compute.backendServices().update(project, backendServiceName, backendService).execute()
          task.updateStatus phase, "Enabled backend for server group ${serverGroupName} in Http(s) load balancer backend service ${backendServiceName}."
        }
      }
    }
  }

  /**
   * Build a backend from a load balancing policy. Note that this does not set the group URL, which is mandatory.
   *
   * @param policy - The load balancing policy to build the Backend from.
   * @return Backend created from the load balancing policy.
     */
  static Backend backendFromLoadBalancingPolicy(GoogleHttpLoadBalancingPolicy policy) {
    def balancingMode = policy.balancingMode
    return new Backend(
      balancingMode: balancingMode,
      maxRatePerInstance: balancingMode == GoogleHttpLoadBalancingPolicy.BalancingMode.RATE ?
        policy.maxRatePerInstance : null,
      maxUtilization: balancingMode == GoogleHttpLoadBalancingPolicy.BalancingMode.UTILIZATION ?
        policy.maxUtilization : null,
      capacityScaler: policy.capacityScaler != null ? policy.capacityScaler : 1.0,
    )
  }

  // Note: listeningPort is not set in this method.
  static GoogleHttpLoadBalancingPolicy loadBalancingPolicyFromBackend(Backend backend) {
    def backendBalancingMode = GoogleHttpLoadBalancingPolicy.BalancingMode.valueOf(backend.balancingMode)
    return new GoogleHttpLoadBalancingPolicy(
      balancingMode: backendBalancingMode,
      maxRatePerInstance: backendBalancingMode == GoogleHttpLoadBalancingPolicy.BalancingMode.RATE ?
        backend.maxRatePerInstance : null,
      maxUtilization: backendBalancingMode == GoogleHttpLoadBalancingPolicy.BalancingMode.UTILIZATION ?
        backend.maxUtilization : null,
      capacityScaler: backend.capacityScaler,
    )
  }

  static void destroyHttpLoadBalancerBackends(Compute compute,
                                              String project,
                                              GoogleServerGroup.View serverGroup,
                                              GoogleLoadBalancerProvider googleLoadBalancerProvider,
                                              Task task,
                                              String phase) {
    def serverGroupName = serverGroup.name
    def httpLoadBalancersInMetadata = serverGroup?.asg?.get(GoogleServerGroup.View.GLOBAL_LOAD_BALANCER_NAMES) ?: []
    def foundHttpLoadBalancers = googleLoadBalancerProvider.getApplicationLoadBalancers("").findAll {
      it.name in serverGroup.loadBalancers && it.loadBalancerType == GoogleLoadBalancerType.HTTP.toString()
    }
    def notDeleted = httpLoadBalancersInMetadata - (foundHttpLoadBalancers.collect { it.name })

    log.debug("Attempting to delete backends for ${serverGroup.name} from the following Http load balancers: ${httpLoadBalancersInMetadata}")
    if (notDeleted) {
      log.warn("Could not locate the following Http load balancers: ${notDeleted}. Proceeding with other backend deletions without mutating them.")
    }

    if (foundHttpLoadBalancers) {
      Metadata instanceMetadata = serverGroup?.launchConfig?.instanceTemplate?.properties?.metadata
      Map metadataMap = buildMapFromMetadata(instanceMetadata)
      List<String> backendServiceNames = metadataMap?.(GoogleServerGroup.View.BACKEND_SERVICE_NAMES)?.split(",")
      if (backendServiceNames) {
        backendServiceNames.each { String backendServiceName ->
          BackendService backendService = compute.backendServices().get(project, backendServiceName).execute()
          backendService?.backends?.removeAll { Backend backend ->
            (getLocalName(backend.group) == serverGroupName) &&
                (Utils.getRegionFromGroupUrl(backend.group) == serverGroup.region)
          }
          compute.backendServices().update(project, backendServiceName, backendService).execute()
          task.updateStatus phase, "Deleted backend for server group ${serverGroupName} from Http(s) load balancer backend service ${backendServiceName}."
        }
      }
    }
  }

  static Boolean isBackendServiceInUse(List<UrlMap> projectUrlMaps, String backendServiceName) {
    def defaultServicesMatch = projectUrlMaps?.findAll { UrlMap urlMap ->
      getLocalName(urlMap.getDefaultService()) == backendServiceName
    }

    def servicesInUse = []
    projectUrlMaps?.each { UrlMap urlMap ->
      urlMap?.getPathMatchers()?.each { PathMatcher pathMatcher ->
        servicesInUse << getLocalName(pathMatcher.getDefaultService())
        pathMatcher?.getPathRules()?.each { PathRule pathRule ->
          servicesInUse << getLocalName(pathRule.getService())
        }
      }
    }
    return defaultServicesMatch || (backendServiceName in servicesInUse)
  }

  /**
   * Resolve the L7 load balancer names that need added to the instance metadata.
   *
   * @param backendServiceNames - Backend service names explicitly included in the request.
   * @param compute
   * @param project
   * @return List of L7 load balancer names to put into the instance metadata.
   */
  static List<String> resolveHttpLoadBalancerNamesMetadata(List<String> backendServiceNames, Compute compute, String project) {
    def loadBalancerNames = []
    def projectUrlMaps = compute.urlMaps().list(project).execute().getItems()
    def servicesByUrlMap = [:]
    projectUrlMaps.each { UrlMap urlMap ->
      servicesByUrlMap.put(urlMap.name, Utils.getBackendServicesFromUrlMap(urlMap))
    }

    def urlMapsInUse = []
    backendServiceNames.each { String backendServiceName ->
      servicesByUrlMap.each { urlMapName, services ->
        if (backendServiceName in services) {
          urlMapsInUse << urlMapName
        }
      }
    }

    def globalForwardingRules = compute.globalForwardingRules().list(project).execute().getItems()
    globalForwardingRules.each { ForwardingRule fr ->
      String proxyType = Utils.getTargetProxyType(fr.target)
      def proxy = null
      switch (proxyType) {
        case "targetHttpProxies":
          proxy = compute.targetHttpProxies().get(project, getLocalName(fr.target)).execute()
          break
        case "targetHttpsProxies":
          proxy = compute.targetHttpsProxies().get(project, getLocalName(fr.target)).execute()
          break
        default:
          throw new GoogleOperationException("Illegal proxy type for ${fr.target}.")
          break
      }
      if (getLocalName(proxy.urlMap) in urlMapsInUse) {
        loadBalancerNames << fr.name
      }
    }
    return loadBalancerNames
  }

  /**
   * Retry a GCP operation if it fails. Treat any error codes in successfulErrorCodes as success.
   *
   * @param operation - The GCP operation.
   * @param action - String describing the GCP operation.
   * @param resource - Resource we are operating on.
   * @param task
   * @param phase
   * @param retryCodes - GoogleJsonResponseException codes we retry on.
   * @param successfulErrorCodes - GoogleJsonException codes we treat as success.
   */
  static void safeRetry(Closure operation,
                        String action,
                        String resource,
                        Task task,
                        String phase,
                        List<Integer> retryCodes,
                        List<Integer> successfulErrorCodes) {
    try {
      task.updateStatus phase, "Attempting $action of $resource..."
      operation()
    } catch (GoogleJsonResponseException | GoogleOperationTimedOutException _) {
      log.warn "Initial $action of $resource failed, retrying..."

      int tries = 1
      while (tries < 10) { // Retry 10 times.
        try {
          tries++
          sleep(TimeUnit.SECONDS.toMillis(10)) // With 10 seconds between tries.
          log.warn "$action $resource attempt #$tries..."
          operation()
          log.warn "$action $resource attempt #$tries succeeded"
          return
        } catch (GoogleJsonResponseException jsonException) {
          if (jsonException.statusCode in successfulErrorCodes) {
            log.warn "Retry $action of $resource encountered ${jsonException.statusCode}, treating as success..."
            return
          } else if (jsonException.statusCode in retryCodes) {
            log.warn "Retry $action of $resource encountered ${jsonException.statusCode} with error message: ${jsonException.message}. Trying again..."
          } else {
            throw jsonException
          }
        } catch (GoogleOperationTimedOutException __) {
          log.warn "Retry $action timed out again, trying again..."
        }
      }
      throw new GoogleOperationException("Failed to $action $resource after #$tries.")
    }
  }

  static Firewall buildFirewallRule(String projectName,
                                    UpsertGoogleSecurityGroupDescription securityGroupDescription,
                                    Compute compute,
                                    Task task,
                                    String phase) {
    def network = queryNetwork(projectName, securityGroupDescription.network, compute, task, phase)
    def firewall = new Firewall(
        name: securityGroupDescription.securityGroupName,
        network: network.selfLink
    )
    def allowed = securityGroupDescription.allowed.collect {
      new Firewall.Allowed(IPProtocol: it.ipProtocol, ports: it.portRanges)
    }

    if (allowed) {
      firewall.allowed = allowed
    }

    if (securityGroupDescription.description) {
      firewall.description = securityGroupDescription.description
    }

    if (securityGroupDescription.sourceRanges) {
      firewall.sourceRanges = securityGroupDescription.sourceRanges
    }

    if (securityGroupDescription.sourceTags) {
      firewall.sourceTags = securityGroupDescription.sourceTags
    }

    if (securityGroupDescription.targetTags) {
      firewall.targetTags = securityGroupDescription.targetTags
    }

    return firewall
  }
}
