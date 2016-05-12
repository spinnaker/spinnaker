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
import com.netflix.spinnaker.clouddriver.google.deploy.description.BaseGoogleInstanceDescription
import com.netflix.spinnaker.clouddriver.google.deploy.description.BasicGoogleDeployDescription
import com.netflix.spinnaker.clouddriver.google.deploy.description.CreateGoogleHttpLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.google.deploy.description.UpsertGoogleLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.google.deploy.description.UpsertGoogleSecurityGroupDescription
import com.netflix.spinnaker.clouddriver.google.deploy.exception.GoogleOperationException
import com.netflix.spinnaker.clouddriver.google.deploy.exception.GoogleResourceNotFoundException
import com.netflix.spinnaker.clouddriver.google.model.GoogleDisk
import com.netflix.spinnaker.clouddriver.google.model.GoogleDiskType
import com.netflix.spinnaker.clouddriver.google.model.GoogleSecurityGroup
import com.netflix.spinnaker.clouddriver.google.model.GoogleServerGroup
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleClusterProvider
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleSecurityGroupProvider
import com.netflix.spinnaker.clouddriver.google.security.GoogleCredentials

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

  static HttpHealthCheck queryHttpHealthCheck(
    String projectName, String httpHealthCheckName, Compute compute, Task task, String phase) {
    task.updateStatus phase, "Checking for existing network load balancer (http health check) $httpHealthCheckName..."

    return compute.httpHealthChecks().list(projectName).execute().items.find { existingHealthCheck ->
      existingHealthCheck.name == httpHealthCheckName
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

  static InstanceGroupManager queryManagedInstanceGroup(String projectName,
                                                        String zone,
                                                        String serverGroupName,
                                                        GoogleCredentials credentials) {
    credentials.compute.instanceGroupManagers().get(projectName, zone, serverGroupName).execute()
  }

  static List<InstanceGroupManager> queryManagedInstanceGroups(String projectName,
                                                               String region,
                                                               GoogleCredentials credentials) {
    def compute = credentials.compute
    def zones = getZonesFromRegion(projectName, region, compute)

    def allMIGSInRegion = zones.findResults {
      def localZoneName = getLocalName(it)

      compute.instanceGroupManagers().list(projectName, localZoneName).execute().getItems()
    }.flatten()

    allMIGSInRegion
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

      image = GCEUtil.getLocalName(bootDisk?.initializeParams?.sourceImage)
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

    return new BaseGoogleInstanceDescription(
      image: image,
      instanceType: instanceTemplateProperties.machineType,
      disks: disks,
      instanceMetadata: instanceTemplateProperties.metadata?.items?.collectEntries {
        [it.key, it.value]
      },
      tags: instanceTemplateProperties.tags?.items,
      network: getLocalName(networkInterface.network),
      authScopes: retrieveScopesFromDefaultServiceAccount(instanceTemplateProperties.serviceAccounts)
    )
  }

  static BasicGoogleDeployDescription.AutoscalingPolicy buildAutoscalingPolicyDescriptionFromAutoscalingPolicy(
    AutoscalingPolicy autoscalingPolicy) {
    if (!autoscalingPolicy) {
      return null
    }

    autoscalingPolicy.with {
      def autoscalingPolicyDescription =
          new BasicGoogleDeployDescription.AutoscalingPolicy(
              coolDownPeriodSec: coolDownPeriodSec,
              minNumReplicas: minNumReplicas,
              maxNumReplicas: maxNumReplicas
          )

      if (cpuUtilization) {
        autoscalingPolicyDescription.cpuUtilization =
            new BasicGoogleDeployDescription.AutoscalingPolicy.CpuUtilization(
                utilizationTarget: cpuUtilization.utilizationTarget
            )
      }

      if (loadBalancingUtilization) {
        autoscalingPolicyDescription.loadBalancingUtilization =
            new BasicGoogleDeployDescription.AutoscalingPolicy.LoadBalancingUtilization(
                utilizationTarget: loadBalancingUtilization.utilizationTarget
            )
      }

      if (customMetricUtilizations) {
        autoscalingPolicyDescription.customMetricUtilizations =
            customMetricUtilizations.collect {
              new BasicGoogleDeployDescription.AutoscalingPolicy.CustomMetricUtilization(
                  metric: it.metric,
                  utilizationTarget: it.utilizationTarget,
                  utilizationTargetType: it.utilizationTargetType
              )
            }
      }

      return autoscalingPolicyDescription
    }
  }

  static List<String> retrieveScopesFromDefaultServiceAccount(List<ServiceAccount> serviceAccounts) {
    serviceAccounts?.find { it.email == "default" }?.scopes
  }

  static String buildDiskTypeUrl(String projectName, String zone, GoogleDiskType diskType) {
    return "https://www.googleapis.com/compute/v1/projects/$projectName/zones/$zone/diskTypes/$diskType"
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
                                    Operation migCreateOperation,
                                    BasicGoogleDeployDescription description) {
    description.autoscalingPolicy.with {
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

      return new Autoscaler(name: serverGroupName,
                            zone: migCreateOperation.zone,
                            target: migCreateOperation.targetLink,
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

  static ServiceAccount buildServiceAccount(List<String> authScopes) {
    return authScopes ? new ServiceAccount(email: "default", scopes: resolveAuthScopes(authScopes)) : null
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

  private static void updateStatusAndThrowNotFoundException(String errorMsg, Task task, String phase) {
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
