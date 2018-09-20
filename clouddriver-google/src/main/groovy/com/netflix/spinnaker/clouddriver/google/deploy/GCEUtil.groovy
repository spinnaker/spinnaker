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
import com.google.api.client.http.GenericUrl
import com.google.api.client.http.HttpHeaders
import com.google.api.client.http.HttpRequest
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.http.HttpResponse
import com.google.api.client.json.JsonObjectParser
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.compute.Compute
import com.google.api.services.compute.model.*
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.artifacts.ArtifactUtils
import com.netflix.spinnaker.clouddriver.consul.provider.ConsulProviderUtils
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.config.GoogleConfiguration
import com.netflix.spinnaker.clouddriver.google.GoogleExecutorTraits
import com.netflix.spinnaker.clouddriver.google.cache.Keys
import com.netflix.spinnaker.clouddriver.google.deploy.description.BaseGoogleInstanceDescription
import com.netflix.spinnaker.clouddriver.google.deploy.description.BasicGoogleDeployDescription
import com.netflix.spinnaker.clouddriver.google.deploy.description.UpsertGoogleLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.google.deploy.description.UpsertGoogleSecurityGroupDescription
import com.netflix.spinnaker.clouddriver.google.deploy.exception.GoogleOperationException
import com.netflix.spinnaker.clouddriver.google.deploy.exception.GoogleResourceNotFoundException
import com.netflix.spinnaker.clouddriver.google.model.*
import com.netflix.spinnaker.clouddriver.google.model.callbacks.Utils
import com.netflix.spinnaker.clouddriver.google.model.health.GoogleInstanceHealth
import com.netflix.spinnaker.clouddriver.google.model.health.GoogleLoadBalancerHealth
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.*
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleClusterProvider
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleLoadBalancerProvider
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleNetworkProvider
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleSubnetProvider
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import com.netflix.spinnaker.kork.artifacts.model.Artifact
import groovy.util.logging.Slf4j

import static com.netflix.spinnaker.clouddriver.google.cache.Keys.Namespace.HTTP_HEALTH_CHECKS
import static com.netflix.spinnaker.clouddriver.google.cache.Keys.Namespace.HEALTH_CHECKS

@Slf4j
class GCEUtil {
  private static final String DISK_TYPE_PERSISTENT = "PERSISTENT"
  private static final String DISK_TYPE_SCRATCH = "SCRATCH"
  private static final String GCE_API_PREFIX = "https://www.googleapis.com/compute/v1/projects/"
  private static final List<Integer> RETRY_ERROR_CODES = [400, 403, 412]

  public static final String TARGET_POOL_NAME_PREFIX = "tp"

  static String queryMachineType(String instanceType, String location, GoogleNamedAccountCredentials credentials, Task task, String phase) {
    task.updateStatus phase, "Looking up machine type $instanceType..."

    if (instanceType in credentials.locationToInstanceTypesMap[location]?.instanceTypes) {
      return instanceType
    } else {
      updateStatusAndThrowNotFoundException("Machine type $instanceType not found.", task, phase)
    }
  }

  static Image queryImage(String projectName,
                          String imageName,
                          GoogleNamedAccountCredentials credentials,
                          Compute compute,
                          Task task,
                          String phase,
                          String clouddriverUserAgentApplicationName,
                          List<String> baseImageProjects,
                          GoogleExecutorTraits executor) {
    task.updateStatus phase, "Looking up image $imageName..."

    def filter = "name eq $imageName"
    def imageProjects = [projectName] + credentials?.imageProjects + baseImageProjects - null
    def sourceImage = null

    def imageListBatch = buildBatchRequest(compute, clouddriverUserAgentApplicationName)
    def imageListCallback = new JsonBatchCallback<ImageList>() {
      @Override
      void onFailure(GoogleJsonError e, HttpHeaders responseHeaders) throws IOException {
        updateStatusAndThrowNotFoundException("Error locating $imageName in these projects: $imageProjects: $e.message", task, phase)
      }

      @Override
      void onSuccess(ImageList imageList, HttpHeaders responseHeaders) throws IOException {
        if (!sourceImage && imageList.items) {
          sourceImage = imageList.items[0]
        }
      }
    }

    imageProjects.each { imageProject ->
      def imagesList = compute.images().list(imageProject)
      imagesList.setFilter(filter)
      imagesList.queue(imageListBatch, imageListCallback)
    }

    executor.timeExecuteBatch(imageListBatch, "findImage")

    if (sourceImage) {
      return sourceImage
    } else {
      updateStatusAndThrowNotFoundException("Image $imageName not found in any of these projects: $imageProjects.", task, phase)
    }
  }

  static Image getImageFromArtifact(Artifact artifact,
                                    Compute compute,
                                    Task task,
                                    String phase,
                                    SafeRetry safeRetry,
                                    GoogleExecutorTraits executor) {
    if (artifact.getType() != ArtifactUtils.GCE_IMAGE_TYPE) {
      throw new GoogleOperationException("Artifact to deploy to GCE must be of type ${ArtifactUtils.GCE_IMAGE_TYPE}")
    }

    def reference = artifact.getReference()
    task.updateStatus phase, "Looking up image $reference..."

    def result = safeRetry.doRetry(
      {
        return compute.getRequestFactory()
          .buildGetRequest(new GenericUrl(reference))
          .setParser(new JsonObjectParser(JacksonFactory.getDefaultInstance()))
          .execute()
      },
      "gce/image",
      task,
      RETRY_ERROR_CODES,
      [],
      [action: "get", phase: phase, operation: "compute.buildGetRequest.execute", (executor.TAG_SCOPE): executor.SCOPE_GLOBAL],
      executor.registry
    ) as HttpResponse

    return result.parseAs(Image)
  }

  static Image getBootImage(BaseGoogleInstanceDescription description,
                            Task task,
                            String phase,
                            String clouddriverUserAgentApplicationName,
                            List<String> baseImageProjects,
                            SafeRetry safeRetry,
                            GoogleExecutorTraits executor) {
    if (description.imageSource == BaseGoogleInstanceDescription.ImageSource.ARTIFACT) {
      return getImageFromArtifact(
        description.imageArtifact,
        description.credentials.compute,
        task,
        phase,
        safeRetry,
        executor
      )
    } else {
      return queryImage(description.credentials.project,
        description.image,
        description.credentials,
        description.credentials.compute,
        task,
        phase,
        clouddriverUserAgentApplicationName,
        baseImageProjects,
        executor)
    }
  }

  private static BatchRequest buildBatchRequest(def compute, String clouddriverUserAgentApplicationName) {
    return compute.batch(
      new HttpRequestInitializer() {
        @Override
        void initialize(HttpRequest request) throws IOException {
          request.headers.setUserAgent(clouddriverUserAgentApplicationName);
        }
      }
    )
  }

  static GoogleNetwork queryNetwork(String accountName, String networkName, Task task, String phase, GoogleNetworkProvider googleNetworkProvider) {
    task.updateStatus phase, "Looking up network $networkName..."

    def networks = googleNetworkProvider.getAllMatchingKeyPattern(Keys.getNetworkKey(networkName, "global", accountName))

    if (networks) {
      return networks[0]
    } else {
      updateStatusAndThrowNotFoundException("Network $networkName not found.", task, phase)
    }
  }

  static GoogleSubnet querySubnet(String accountName, String region, String subnetName, Task task, String phase, GoogleSubnetProvider googleSubnetProvider) {
    task.updateStatus phase, "Looking up subnet $subnetName in $region..."

    def subnets = googleSubnetProvider.getAllMatchingKeyPattern(Keys.getSubnetKey(subnetName, region, accountName))

    if (subnets) {
      return subnets[0]
    } else {
      updateStatusAndThrowNotFoundException("Subnet $subnetName not found in $region.", task, phase)
    }
  }

  // If a forwarding rule with the specified name is found in any region, it is returned.
  static ForwardingRule queryRegionalForwardingRule(
    String projectName, String forwardingRuleName, Compute compute, Task task, String phase, GoogleExecutorTraits executor) {
    task.updateStatus phase, "Checking for existing network load balancer (forwarding rule) $forwardingRuleName..."

    // Try to retrieve this forwarding rule in each region.
    def all_regions = executor.timeExecute(compute.regions().list(projectName),
                                           "compute.regions.list",
                                           executor.TAG_SCOPE, executor.SCOPE_GLOBAL)
    for (def region : all_regions.items) {
      try {
        return executor.timeExecute(
          compute.forwardingRules().get(projectName, region.name, forwardingRuleName),
          "compute.forwardingRules.get",
          executor.TAG_SCOPE, executor.SCOPE_REGIONAL, executor.TAG_REGION, region.name)
      } catch (GoogleJsonResponseException e) {
        // 404 is thrown if the forwarding rule does not exist in the given region. Any other exception needs to be propagated.
        if (e.getStatusCode() != 404) {
          throw e
        }
      }
    }
  }

  static BackendService queryBackendService(Compute compute, String project, String serviceName, Task task, String phase, GoogleExecutorTraits executor) {
    task.updateStatus phase, "Checking for existing backend service $serviceName..."

    try {
      return executor.timeExecute(
        compute.backendServices().get(project, serviceName),
        "compute.backendServices.get",
        executor.TAG_SCOPE, executor.SCOPE_GLOBAL)
    } catch (GoogleJsonResponseException e) {
      if (e.getStatusCode() != 404) {
        throw e
      }
      return null
    }
  }

  static TargetPool queryTargetPool(
    String projectName, String region, String targetPoolName, Compute compute, Task task, String phase, GoogleExecutorTraits executor) {
    task.updateStatus phase, "Checking for existing network load balancer (target pool) $targetPoolName..."

    def all_pools = executor.timeExecute(
      compute.targetPools().list(projectName, region),
      "compute.targetPools.list",
      executor.TAG_SCOPE, executor.SCOPE_REGIONAL, executor.TAG_REGION, region)
    return all_pools.items.find { existingTargetPool -> existingTargetPool.name == targetPoolName }
  }

  // TODO(duftler): Update this to query for the exact health check instead of searching all.
  static HttpHealthCheck queryHttpHealthCheck(
    String projectName, String httpHealthCheckName, Compute compute, Task task, String phase, GoogleExecutorTraits executor) {
    task.updateStatus phase, "Checking for existing network load balancer (http health check) $httpHealthCheckName..."

    def all_checks = executor.timeExecute(
      compute.httpHealthChecks().list(projectName),
      "compute.httpHealthChecks.list",
      executor.TAG_SCOPE, executor.SCOPE_GLOBAL)
    return all_checks.items.find { existingHealthCheck -> existingHealthCheck.name == httpHealthCheckName }
  }

  static def queryHealthCheck(String projectName,
                              String account,
                              String healthCheckName,
                              Compute compute,
                              Cache cacheView,
                              Task task,
                              String phase,
                              GoogleExecutorTraits executor) {
    task.updateStatus phase, "Looking up http(s) health check $healthCheckName..."

    def httpHealthCheckIdentifiers = cacheView.filterIdentifiers(HTTP_HEALTH_CHECKS.ns, Keys.getHttpHealthCheckKey(account, healthCheckName))
    def results = cacheView.getAll(HTTP_HEALTH_CHECKS.ns, httpHealthCheckIdentifiers, RelationshipCacheFilter.none())

    if (results[0]?.attributes?.httpHealthCheck) {
      return results[0]?.attributes?.httpHealthCheck
    } else {
      try {
        // TODO(duftler): Update this to use the cache instead of a live call once we are caching https health checks.
        return executor.timeExecute(
          compute.httpsHealthChecks().get(projectName, healthCheckName),
          "compute.httpsHealthChecks.get",
          executor.TAG_SCOPE, executor.SCOPE_GLOBAL)
      } catch (GoogleJsonResponseException | SocketTimeoutException | SocketException _) {
        updateStatusAndThrowNotFoundException("Http(s) health check $healthCheckName not found.", task, phase)
      }
    }
  }

  static def queryNestedHealthCheck(String projectName,
                                    String account,
                                    String healthCheckName,
                                    Compute compute,
                                    Cache cacheView,
                                    Task task,
                                    String phase,
                                    GoogleExecutorTraits executor) {
    task.updateStatus phase, "Looking up health check $healthCheckName..."

    def healthCheckIdentifiers = cacheView.filterIdentifiers(HEALTH_CHECKS.ns, Keys.getHealthCheckKey(account, 'healthCheck', healthCheckName))
    def results = cacheView.getAll(HEALTH_CHECKS.ns, healthCheckIdentifiers, RelationshipCacheFilter.none())

    return results[0]?.attributes?.healthCheck
  }

  static List<ForwardingRule> queryRegionalForwardingRules(String projectName,
                                                           String region,
                                                           List<String> forwardingRuleNames,
                                                           Compute compute,
                                                           Task task,
                                                           String phase,
                                                           SafeRetry safeRetry,
                                                           GoogleExecutorTraits executor) {
    task.updateStatus phase, "Looking up regional load balancers $forwardingRuleNames..."

    def forwardingRules = safeRetry.doRetry(
      { return executor.timeExecute(
          compute.forwardingRules().list(projectName, region),
          "compute.forwardingRules.list",
          executor.TAG_SCOPE, executor.SCOPE_GLOBAL
        ).items
      },
      "regional forwarding rules",
      task,
      RETRY_ERROR_CODES,
      [],
      [action: "list", phase: phase, operation: "compute.forwardingRules.list", (executor.TAG_SCOPE): executor.SCOPE_GLOBAL],
      executor.registry
    ) as List<ForwardingRule>
    def foundForwardingRules = forwardingRules.findAll {
      it.name in forwardingRuleNames
    }

    if (foundForwardingRules.size == forwardingRuleNames.size) {
      return foundForwardingRules
    } else {
      def foundNames = foundForwardingRules.collect { it.name }

      updateStatusAndThrowNotFoundException("Regional load balancers ${forwardingRuleNames - foundNames} not found.", task, phase)
    }
  }

  static List<GoogleLoadBalancerView> queryAllLoadBalancers(GoogleLoadBalancerProvider googleLoadBalancerProvider,
                                                            List<String> forwardingRuleNames,
                                                            Task task,
                                                            String phase) {
    def loadBalancers = googleLoadBalancerProvider.getApplicationLoadBalancers("") as List
    def foundLoadBalancers = loadBalancers.findAll { it.name in forwardingRuleNames }

    if (foundLoadBalancers.size() == forwardingRuleNames.size()) {
      return foundLoadBalancers
    } else {
      def foundNames = foundLoadBalancers.collect { it.name }
      updateStatusAndThrowNotFoundException("Load balancers ${forwardingRuleNames - foundNames} not found.", task, phase)
    }
  }

  static List<String> queryInstanceUrls(String projectName,
                                        String region,
                                        List<String> instanceLocalNames,
                                        Compute compute,
                                        Task task,
                                        String phase,
                                        GoogleExecutorTraits executor) {
    task.updateStatus phase, "Looking up instances $instanceLocalNames..."

    Map<String, InstancesScopedList> zoneToInstancesMap = executor.timeExecute(
      compute.instances().aggregatedList(projectName),
      "compute.instances.aggregatedList",
      executor.TAG_SCOPE, executor.SCOPE_GLOBAL
    ).items

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
                                                                GoogleNamedAccountCredentials credentials,
                                                                Task task,
                                                                String phase,
                                                                SafeRetry safeRetry,
                                                                GoogleExecutorTraits executor) {
    return safeRetry.doRetry(
      { return executor.timeExecute(
            credentials.compute.regionInstanceGroupManagers().get(projectName, region, serverGroupName),
            "compute.regionInstanceGroupManagers.get",
            executor.TAG_SCOPE, executor.SCOPE_REGIONAL, executor.TAG_REGION, region)
      },
      "regional managed instance group",
      task,
      RETRY_ERROR_CODES,
      [],
      [action: "get", phase: phase, operation: "compute.regionInstanceGroupManagers.get", (executor.TAG_SCOPE): executor.SCOPE_REGIONAL, (executor.TAG_REGION): region],
      executor.registry
    ) as InstanceGroupManager
  }

  static InstanceGroupManager queryZonalManagedInstanceGroup(String projectName,
                                                             String zone,
                                                             String serverGroupName,
                                                             GoogleNamedAccountCredentials credentials,
                                                             Task task,
                                                             String phase,
                                                             SafeRetry safeRetry,
                                                             GoogleExecutorTraits executor) {
    return safeRetry.doRetry(
      { return executor.timeExecute(
            credentials.compute.instanceGroupManagers().get(projectName, zone, serverGroupName),
            "compute.instanceGroupManagers.get",
            executor.TAG_SCOPE, executor.SCOPE_ZONAL, executor.TAG_ZONE, zone)
      },
      "zonal managed instance group",
      task,
      RETRY_ERROR_CODES,
      [],
      [action: "get", phase: phase, operation: "compute.instanceGroupManagers.get", (executor.TAG_SCOPE): executor.SCOPE_ZONAL, (executor.TAG_ZONE): zone],
      executor.registry
    ) as InstanceGroupManager
  }

  static List<InstanceGroupManager> queryAllManagedInstanceGroups(String projectName,
                                                                  String region,
                                                                  GoogleNamedAccountCredentials credentials,
                                                                  Task task,
                                                                  String phase,
                                                                  SafeRetry safeRetry,
                                                                  GoogleExecutorTraits executor) {
    boolean executedAtLeastOnce = false
    String nextPageToken = null
    Map<String, InstanceGroupManagersScopedList> fullAggregatedList = [:].withDefault { new InstanceGroupManagersScopedList() }

    while (!executedAtLeastOnce || nextPageToken) {
      Map<String, InstanceGroupManagersScopedList> aggregatedList = safeRetry.doRetry(
        {
          InstanceGroupManagerAggregatedList instanceGroupManagerAggregatedList = executor.timeExecute(
            credentials.compute.instanceGroupManagers().aggregatedList(projectName).setPageToken(nextPageToken),
            "compute.instanceGroupManagers.aggregatedList",
            executor.TAG_SCOPE, executor.SCOPE_GLOBAL
          )

          executedAtLeastOnce = true
          nextPageToken = instanceGroupManagerAggregatedList.getNextPageToken()

          return instanceGroupManagerAggregatedList.getItems()
        },
        "aggregated managed instance groups",
        task,
        RETRY_ERROR_CODES,
        [],
        [action: "list", phase: phase, operation: "compute.instanceGroupManagers.aggregatedList", (executor.TAG_SCOPE): executor.SCOPE_GLOBAL],
        executor.registry
      ) as Map<String, InstanceGroupManagersScopedList>

      aggregatedList.each { scope, InstanceGroupManagersScopedList instanceGroupManagersScopedList ->
        // Only accumulate these results if there are actual MIGs in this scope.
        if (instanceGroupManagersScopedList.getInstanceGroupManagers()) {
          // The scope we are adding to may not have any MIGs yet.
          if (!fullAggregatedList[scope].getInstanceGroupManagers()) {
            fullAggregatedList[scope].setInstanceGroupManagers([])
          }

          fullAggregatedList[scope].getInstanceGroupManagers().addAll(instanceGroupManagersScopedList.getInstanceGroupManagers())
        }
      }
    }

    def zonesInRegion = credentials.getZonesFromRegion(region)

    return fullAggregatedList.findResults { _, InstanceGroupManagersScopedList instanceGroupManagersScopedList ->
      return instanceGroupManagersScopedList.getInstanceGroupManagers()?.findResults { mig ->
        if (mig.zone) {
          return getLocalName(mig.zone) in zonesInRegion ? mig : null
        } else {
          return getLocalName(mig.region) == region ? mig : null
        }
      }
    }.flatten()
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
      minCpuPlatform: instanceTemplateProperties.minCpuPlatform,
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

      if (mode) {
        autoscalingPolicyDescription.mode = AutoscalingMode.valueOf(mode)
      }

      return autoscalingPolicyDescription
    }
  }

  static GoogleAutoHealingPolicy buildAutoHealingPolicyDescriptionFromAutoHealingPolicy(
    InstanceGroupManagerAutoHealingPolicy autoHealingPolicy) {
    if (!autoHealingPolicy) {
      return null
    }

    return new GoogleAutoHealingPolicy(
      healthCheck: Utils.getLocalName(autoHealingPolicy.healthCheck),
      initialDelaySec: autoHealingPolicy.initialDelaySec,
      maxUnavailable: autoHealingPolicy.maxUnavailable
    )
  }

  static List<String> retrieveScopesFromServiceAccount(String serviceAccountEmail, List<ServiceAccount> serviceAccounts) {
    return serviceAccountEmail ? serviceAccounts?.find { it.email == serviceAccountEmail }?.scopes : null
  }

  static String buildDiskTypeUrl(String projectName, String zone, GoogleDiskType diskType) {
    return GCE_API_PREFIX + "$projectName/zones/$zone/diskTypes/$diskType"
  }

  static String buildZonalServerGroupUrl(String projectName, String zone, String serverGroupName) {
    return GCE_API_PREFIX + "$projectName/zones/$zone/instanceGroups/$serverGroupName"
  }

  static String buildCertificateUrl(String projectName, String certName) {
    return GCE_API_PREFIX + "$projectName/global/sslCertificates/$certName"
  }

  static String buildHttpHealthCheckUrl(String projectName, String healthCheckName) {
    return GCE_API_PREFIX + "$projectName/global/httpHealthChecks/$healthCheckName"
  }

  static String buildHttpsHealthCheckUrl(String projectName, String healthCheckName) {
    return GCE_API_PREFIX + "$projectName/global/httpsHealthChecks/$healthCheckName"
  }

  static String buildHealthCheckUrl(String projectName, String healthCheckName) {
    return GCE_API_PREFIX + "$projectName/global/healthChecks/$healthCheckName"
  }

  static String buildBackendServiceUrl(String projectName, String backendServiceName) {
    return GCE_API_PREFIX + "$projectName/global/backendServices/$backendServiceName"
  }

  static String buildRegionBackendServiceUrl(String projectName, String region, String backendServiceName) {
    return GCE_API_PREFIX + "$projectName/regions/$region/backendServices/$backendServiceName"
  }

  static String buildRegionalServerGroupUrl(String projectName, String region, String serverGroupName) {
    return GCE_API_PREFIX + "$projectName/regions/$region/instanceGroups/$serverGroupName"
  }

  static String buildZoneUrl(String projectName, String zone) {
    return GCE_API_PREFIX + "$projectName/zones/$zone"
  }

  static List<AttachedDisk> buildAttachedDisks(BaseGoogleInstanceDescription description,
                                               String zone,
                                               boolean useDiskTypeUrl,
                                               GoogleConfiguration.DeployDefaults deployDefaults,
                                               Task task,
                                               String phase,
                                               String clouddriverUserAgentApplicationName,
                                               List<String> baseImageProjects,
                                               SafeRetry safeRetry,
                                               GoogleExecutorTraits executor) {
    def credentials = description.credentials
    def disks = description.disks
    def instanceType = description.instanceType

    if (!disks) {
      disks = deployDefaults.determineInstanceTypeDisk(instanceType).disks
    }

    if (!disks) {
      throw new GoogleOperationException("Unable to determine disks for instance type $instanceType.")
    }

    def bootImage = getBootImage(description,
                                 task,
                                 phase,
                                 clouddriverUserAgentApplicationName,
                                 baseImageProjects,
                                 safeRetry,
                                 executor)

    def firstPersistentDisk = disks.find { it.persistent }
    return disks.collect { disk ->
      def diskType = useDiskTypeUrl ? buildDiskTypeUrl(credentials.project, zone, disk.type) : disk.type

      def sourceImage
      if (disk.persistent) {
        sourceImage =
          disk.is(firstPersistentDisk)
          ? bootImage
          : queryImage(credentials.project,
                       disk.sourceImage,
                       credentials,
                       credentials.compute,
                       task,
                       phase,
                       clouddriverUserAgentApplicationName,
                       baseImageProjects,
                       executor)
      }

      if (sourceImage && sourceImage.diskSizeGb > disk.sizeGb) {
        disk.sizeGb = sourceImage.diskSizeGb
      }

      def attachedDiskInitializeParams =
        new AttachedDiskInitializeParams(sourceImage: sourceImage?.selfLink,
                                         diskSizeGb: disk.sizeGb,
                                         diskType: diskType)

      new AttachedDisk(boot: disk.is(firstPersistentDisk),
                       autoDelete: disk.autoDelete,
                       type: disk.persistent ? DISK_TYPE_PERSISTENT : DISK_TYPE_SCRATCH,
                       initializeParams: attachedDiskInitializeParams)
    }
  }

  static NetworkInterface buildNetworkInterface(GoogleNetwork network,
                                                GoogleSubnet subnet,
                                                boolean associatePublicIpAddress,
                                                String accessConfigName,
                                                String accessConfigType) {
    NetworkInterface networkInterface = new NetworkInterface(network: network.selfLink,
                                                             subnetwork: subnet ? subnet.selfLink : null)

    if (associatePublicIpAddress) {
      networkInterface.setAccessConfigs([new AccessConfig(name: accessConfigName, type: accessConfigType)])
    }

    return networkInterface
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
                                                       maxNumReplicas: maxNumReplicas,
                                                       mode: mode ? mode.toString() : "ON"
      )

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

  public static String deriveProjectId(String fullUrl) {
    if (!fullUrl) {
      throw new IllegalArgumentException("Attempting to derive project id from empty resource url.")
    }

    List<String> urlParts = fullUrl.tokenize("/")
    int indexOfProjectsToken = urlParts.indexOf("projects")

    if (indexOfProjectsToken == -1) {
      throw new IllegalArgumentException("Attempting to derive project id from resource url that does not contain 'projects': $fullUrl")
    }

    return urlParts[indexOfProjectsToken + 1]
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

  static void addInternalLoadBalancerBackends(Compute compute,
                                              String project,
                                              GoogleServerGroup.View serverGroup,
                                              GoogleLoadBalancerProvider googleLoadBalancerProvider,
                                              Task task,
                                              String phase,
                                              GoogleExecutorTraits executor) {
    String serverGroupName = serverGroup.name
    String region = serverGroup.region
    Metadata instanceMetadata = serverGroup?.launchConfig?.instanceTemplate?.properties?.metadata
    Map metadataMap = buildMapFromMetadata(instanceMetadata)
    def regionalLoadBalancersInMetadata = metadataMap?.(GoogleServerGroup.View.REGIONAL_LOAD_BALANCER_NAMES)?.tokenize(",") ?: []
    def internalLoadBalancersToAddTo = queryAllLoadBalancers(googleLoadBalancerProvider, regionalLoadBalancersInMetadata, task, phase)
      .findAll { it.loadBalancerType == GoogleLoadBalancerType.INTERNAL }
    if (!internalLoadBalancersToAddTo) {
      log.warn("Cache call missed for internal load balancer, making a call to GCP")
      List<ForwardingRule> projectRegionalForwardingRules = executor.timeExecute(
            compute.forwardingRules().list(project, region),
            "compute.forwardingRules.list",
            executor.TAG_SCOPE, executor.SCOPE_REGIONAL, executor.TAG_REGION, region
      ).getItems()
      internalLoadBalancersToAddTo = projectRegionalForwardingRules.findAll {
        // TODO(jacobkiefer): Update this check if any other types of loadbalancers support backend services from regional forwarding rules.
        it.backendService && it.name in serverGroup.loadBalancers
      }
    }

    if (internalLoadBalancersToAddTo) {
      internalLoadBalancersToAddTo.each { GoogleLoadBalancerView loadBalancerView ->
        def ilbView = loadBalancerView as GoogleInternalLoadBalancer.View
        def backendServiceName = ilbView.backendService.name
        BackendService backendService = executor.timeExecute(
          compute.regionBackendServices().get(project, region, backendServiceName),
          "compute.regionBackendServices",
          executor.TAG_SCOPE, executor.SCOPE_REGIONAL, executor.TAG_REGION, region)
        Backend backendToAdd = new Backend(balancingMode: 'CONNECTION')
        if (serverGroup.regional) {
          backendToAdd.setGroup(buildRegionalServerGroupUrl(project, region, serverGroupName))
        } else {
          backendToAdd.setGroup(buildZonalServerGroupUrl(project, serverGroup.zone, serverGroupName))
        }
        if (backendService.backends == null) {
          backendService.backends = []
        }
        backendService.backends << backendToAdd
        executor.timeExecute(
          compute.regionBackendServices().update(project, region, backendServiceName, backendService),
          "compute.regionBackendServices.update",
          executor.TAG_SCOPE, executor.SCOPE_REGIONAL, executor.TAG_REGION, region)
        task.updateStatus phase, "Enabled backend for server group ${serverGroupName} in Internal load balancer backend service ${backendServiceName}."
      }
    }
  }

  static void addHttpLoadBalancerBackends(Compute compute,
                                          ObjectMapper objectMapper,
                                          String project,
                                          GoogleServerGroup.View serverGroup,
                                          GoogleLoadBalancerProvider googleLoadBalancerProvider,
                                          Task task,
                                          String phase,
                                          GoogleExecutorTraits executor) {
    String serverGroupName = serverGroup.name
    Metadata instanceMetadata = serverGroup?.launchConfig?.instanceTemplate?.properties?.metadata
    Map metadataMap = buildMapFromMetadata(instanceMetadata)
    def httpLoadBalancersInMetadata = metadataMap?.(GoogleServerGroup.View.GLOBAL_LOAD_BALANCER_NAMES)?.tokenize(",") ?: []
    def networkLoadBalancersInMetadata = metadataMap?.(GoogleServerGroup.View.REGIONAL_LOAD_BALANCER_NAMES)?.tokenize(",") ?: []

    def allFoundLoadBalancers = (httpLoadBalancersInMetadata + networkLoadBalancersInMetadata) as List<String>
    def httpLoadBalancersToAddTo = queryAllLoadBalancers(googleLoadBalancerProvider, allFoundLoadBalancers, task, phase)
        .findAll { it.loadBalancerType == GoogleLoadBalancerType.HTTP }
    if (!httpLoadBalancersToAddTo) {
      log.warn("Cache call missed for Http load balancers ${httpLoadBalancersInMetadata}, making a call to GCP")
      List<ForwardingRule> projectGlobalForwardingRules = executor.timeExecute(
        compute.globalForwardingRules().list(project),
        "compute.globalForwardingRules.list",
        executor.TAG_SCOPE, executor.SCOPE_GLOBAL
      ).getItems()
      httpLoadBalancersToAddTo = projectGlobalForwardingRules.findAll { ForwardingRule forwardingRule ->
        forwardingRule.target && Utils.getTargetProxyType(forwardingRule.target) in [GoogleTargetProxyType.HTTP, GoogleTargetProxyType.HTTPS] &&
          forwardingRule.name in serverGroup.loadBalancers
      }
    }

    if (httpLoadBalancersToAddTo) {
      String policyJson = metadataMap?.(GoogleServerGroup.View.LOAD_BALANCING_POLICY)
      if (!policyJson) {
        updateStatusAndThrowNotFoundException("Load Balancing Policy not found for server group ${serverGroupName}", task, phase)
      }
      GoogleHttpLoadBalancingPolicy policy = objectMapper.readValue(policyJson, GoogleHttpLoadBalancingPolicy)

      List<String> backendServiceNames = metadataMap?.(GoogleServerGroup.View.BACKEND_SERVICE_NAMES)?.split(",") ?: []
      if (backendServiceNames) {
        backendServiceNames.each { String backendServiceName ->
          BackendService backendService = executor.timeExecute(
            compute.backendServices().get(project, backendServiceName),
            "compute.backendServices.get",
            executor.TAG_SCOPE, executor.SCOPE_GLOBAL)
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
        executor.timeExecute(
            compute.backendServices().update(project, backendServiceName, backendService),
            "compute.backendServices.update",
            executor.TAG_SCOPE, executor.SCOPE_GLOBAL)
          task.updateStatus phase, "Enabled backend for server group ${serverGroupName} in Http(s) load balancer backend service ${backendServiceName}."
        }
      }
    }
  }

  static void addSslLoadBalancerBackends(Compute compute,
                                         ObjectMapper objectMapper,
                                         String project,
                                         GoogleServerGroup.View serverGroup,
                                         GoogleLoadBalancerProvider googleLoadBalancerProvider,
                                         Task task,
                                         String phase,
                                         GoogleExecutorTraits executor) {
    String serverGroupName = serverGroup.name
    Metadata instanceMetadata = serverGroup?.launchConfig?.instanceTemplate?.properties?.metadata
    Map metadataMap = buildMapFromMetadata(instanceMetadata)
    def globalLoadBalancersInMetadata = metadataMap?.(GoogleServerGroup.View.GLOBAL_LOAD_BALANCER_NAMES)?.tokenize(",") ?: []
    def regionalLoadBalancersInMetadata = metadataMap?.(GoogleServerGroup.View.REGIONAL_LOAD_BALANCER_NAMES)?.tokenize(",") ?: []

    def allFoundLoadBalancers = (globalLoadBalancersInMetadata + regionalLoadBalancersInMetadata) as List<String>
    def sslLoadBalancersToAddTo = queryAllLoadBalancers(googleLoadBalancerProvider, allFoundLoadBalancers, task, phase)
      .findAll { it.loadBalancerType == GoogleLoadBalancerType.SSL }
    if (!sslLoadBalancersToAddTo) {
      log.warn("Cache call missed for ssl load balancer, making a call to GCP")
      List<ForwardingRule> projectGlobalForwardingRules = executor.timeExecute(
        compute.globalForwardingRules().list(project),
        "compute.globalForwardingRules",
        executor.TAG_SCOPE, executor.SCOPE_GLOBAL
      ).getItems()
      sslLoadBalancersToAddTo = projectGlobalForwardingRules.findAll { ForwardingRule forwardingRule ->
        forwardingRule.target && Utils.getTargetProxyType(forwardingRule.target) == GoogleTargetProxyType.SSL &&
          forwardingRule.name in serverGroup.loadBalancers
      }
    }

    if (sslLoadBalancersToAddTo) {
      String policyJson = metadataMap?.(GoogleServerGroup.View.LOAD_BALANCING_POLICY)
      if (!policyJson) {
        updateStatusAndThrowNotFoundException("Load Balancing Policy not found for server group ${serverGroupName}", task, phase)
      }
      GoogleHttpLoadBalancingPolicy policy = objectMapper.readValue(policyJson, GoogleHttpLoadBalancingPolicy)

      sslLoadBalancersToAddTo.each { GoogleLoadBalancerView loadBalancerView ->
        def sslView = loadBalancerView as GoogleSslLoadBalancer.View
        String backendServiceName = sslView.backendService.name
        BackendService backendService = executor.timeExecute(
          compute.backendServices().get(project, backendServiceName),
          "compute.backendServices",
          executor.TAG_SCOPE, executor.SCOPE_GLOBAL)
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
        executor.timeExecute(
            compute.backendServices().update(project, backendServiceName, backendService),
            "compute.backendServices.update",
            executor.TAG_SCOPE, executor.SCOPE_GLOBAL)
        task.updateStatus phase, "Enabled backend for server group ${serverGroupName} in ssl load balancer backend service ${backendServiceName}."
      }
    }
  }

  static void addTcpLoadBalancerBackends(Compute compute,
                                         ObjectMapper objectMapper,
                                         String project,
                                         GoogleServerGroup.View serverGroup,
                                         GoogleLoadBalancerProvider googleLoadBalancerProvider,
                                         Task task,
                                         String phase,
                                         GoogleExecutorTraits executor) {
    String serverGroupName = serverGroup.name
    Metadata instanceMetadata = serverGroup?.launchConfig?.instanceTemplate?.properties?.metadata
    Map metadataMap = buildMapFromMetadata(instanceMetadata)
    def globalLoadBalancersInMetadata = metadataMap?.(GoogleServerGroup.View.GLOBAL_LOAD_BALANCER_NAMES)?.tokenize(",") ?: []
    def regionalLoadBalancersInMetadata = metadataMap?.(GoogleServerGroup.View.REGIONAL_LOAD_BALANCER_NAMES)?.tokenize(",") ?: []

    def allFoundLoadBalancers = (globalLoadBalancersInMetadata + regionalLoadBalancersInMetadata) as List<String>
    def tcpLoadBalancersToAddTo = queryAllLoadBalancers(googleLoadBalancerProvider, allFoundLoadBalancers, task, phase)
      .findAll { it.loadBalancerType == GoogleLoadBalancerType.TCP }
    if (!tcpLoadBalancersToAddTo) {
      log.warn("Cache call missed for tcp load balancer, making a call to GCP")
      List<ForwardingRule> projectGlobalForwardingRules = executor.timeExecute(
        compute.globalForwardingRules().list(project),
        "compute.globalForwardingRules",
        executor.TAG_SCOPE, executor.SCOPE_GLOBAL
      ).getItems()
      tcpLoadBalancersToAddTo = projectGlobalForwardingRules.findAll { ForwardingRule forwardingRule ->
        forwardingRule.target && Utils.getTargetProxyType(forwardingRule.target) == GoogleTargetProxyType.TCP &&
          forwardingRule.name in serverGroup.loadBalancers
      }
    }

    if (tcpLoadBalancersToAddTo) {
      String policyJson = metadataMap?.(GoogleServerGroup.View.LOAD_BALANCING_POLICY)
      if (!policyJson) {
        updateStatusAndThrowNotFoundException("Load Balancing Policy not found for server group ${serverGroupName}", task, phase)
      }
      GoogleHttpLoadBalancingPolicy policy = objectMapper.readValue(policyJson, GoogleHttpLoadBalancingPolicy)

      tcpLoadBalancersToAddTo.each { GoogleLoadBalancerView loadBalancerView ->
        def tcpView = loadBalancerView as GoogleTcpLoadBalancer.View
        String backendServiceName = tcpView.backendService.name
        BackendService backendService = executor.timeExecute(
          compute.backendServices().get(project, backendServiceName),
          "compute.backendServices",
          executor.TAG_SCOPE, executor.SCOPE_GLOBAL)
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
        executor.timeExecute(
            compute.backendServices().update(project, backendServiceName, backendService),
            "compute.backendServices.update",
            executor.TAG_SCOPE, executor.SCOPE_GLOBAL)
        task.updateStatus phase, "Enabled backend for server group ${serverGroupName} in tcp load balancer backend service ${backendServiceName}."
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
      maxRatePerInstance: balancingMode == GoogleLoadBalancingPolicy.BalancingMode.RATE || balancingMode == GoogleLoadBalancingPolicy.BalancingMode.UTILIZATION ?
        policy.maxRatePerInstance : null,
      maxUtilization: balancingMode == GoogleLoadBalancingPolicy.BalancingMode.UTILIZATION ?
        policy.maxUtilization : null,
      maxConnectionsPerInstance: balancingMode == GoogleLoadBalancingPolicy.BalancingMode.CONNECTION ?
        policy.maxConnectionsPerInstance : null,
      capacityScaler: policy.capacityScaler != null ? policy.capacityScaler : 1.0,
    )
  }

  static void updateMetadataWithLoadBalancingPolicy(GoogleHttpLoadBalancingPolicy policy, Map instanceMetadata, ObjectMapper objectMapper) {
    if (policy.listeningPort) {
      log.warn("Translated old load balancer instance metadata entry to new format")
      policy.setNamedPorts([new NamedPort(name: GoogleHttpLoadBalancingPolicy.HTTP_DEFAULT_PORT_NAME, port: policy.listeningPort)])
      policy.listeningPort = null // Deprecated.
    }
    instanceMetadata[(GoogleServerGroup.View.LOAD_BALANCING_POLICY)] = objectMapper.writeValueAsString(policy)
  }

  // Note: namedPorts are not set in this method.
  static GoogleHttpLoadBalancingPolicy loadBalancingPolicyFromBackend(Backend backend) {
    def backendBalancingMode = GoogleLoadBalancingPolicy.BalancingMode.valueOf(backend.balancingMode)
    return new GoogleHttpLoadBalancingPolicy(
      balancingMode: backendBalancingMode,
      maxRatePerInstance: backendBalancingMode == GoogleLoadBalancingPolicy.BalancingMode.RATE || backendBalancingMode == GoogleLoadBalancingPolicy.BalancingMode.UTILIZATION ?
        backend.maxRatePerInstance : null,
      maxUtilization: backendBalancingMode == GoogleLoadBalancingPolicy.BalancingMode.UTILIZATION ?
        backend.maxUtilization : null,
      maxConnectionsPerInstance: backendBalancingMode == GoogleLoadBalancingPolicy.BalancingMode.CONNECTION ?
        backend.maxConnectionsPerInstance : null,
      capacityScaler: backend.capacityScaler,
    )
  }

  static void destroySslLoadBalancerBackends(Compute compute,
                                             String project,
                                             GoogleServerGroup.View serverGroup,
                                             GoogleLoadBalancerProvider googleLoadBalancerProvider,
                                             Task task,
                                             String phase,
                                             GoogleExecutorTraits executor) {
    def serverGroupName = serverGroup.name
    def region = serverGroup.region
    def foundSslLoadBalancers = googleLoadBalancerProvider.getApplicationLoadBalancers("").findAll {
      it.name in serverGroup.loadBalancers && it.loadBalancerType == GoogleLoadBalancerType.SSL
    }
    List<String> backendServicesToDeleteFrom = []
    if (foundSslLoadBalancers) {
      backendServicesToDeleteFrom = foundSslLoadBalancers.collect { lb -> lb.backendService.name }
    } else {
      log.warn("Cache call missed for ssl load balancer, making a call to GCP")
      List<ForwardingRule> projectGlobalForwardingRules = executor.timeExecute(
        compute.globalForwardingRules().list(project),
        "compute.globalForwardingRules.list",
        executor.TAG_SCOPE, executor.SCOPE_GLOBAL
      ).getItems()
      List<TargetSslProxy> projectSslProxies = executor.timeExecute(
        compute.targetSslProxies().list(project),
        "compute.targetSslProxies.list",
        executor.TAG_SCOPE, executor.SCOPE_GLOBAL
      ).getItems()
      def matchingSslProxyNames = projectGlobalForwardingRules.findAll { ForwardingRule forwardingRule ->
        forwardingRule.target && Utils.getTargetProxyType(forwardingRule.target) == GoogleTargetProxyType.SSL &&
          forwardingRule.name in serverGroup.loadBalancers
      }.collect { ForwardingRule forwardingRule -> getLocalName(forwardingRule.target) }

      backendServicesToDeleteFrom = projectSslProxies.findAll { TargetSslProxy proxy ->
        proxy.getName() in matchingSslProxyNames
      }.collect { TargetSslProxy proxy -> getLocalName(proxy.getService()) }
    }

    log.debug("Attempting to delete backends for ${serverGroup.name} from the following global load balancers: ${foundSslLoadBalancers.collect { it.name }}")
    backendServicesToDeleteFrom?.each { backendServiceName ->
      BackendService backendService = executor.timeExecute(
        compute.backendServices().get(project, backendServiceName),
        "compute.backendServices.get",
        executor.TAG_SCOPE, executor.SCOPE_GLOBAL)
      backendService?.backends?.removeAll { Backend backend ->
        (getLocalName(backend.group) == serverGroupName) &&
          (Utils.getRegionFromGroupUrl(backend.group) == region)
      }
      executor.timeExecute(
        compute.backendServices().update(project, backendServiceName, backendService),
        "compute.backendServices.update",
        executor.TAG_SCOPE, executor.SCOPE_GLOBAL)
      task.updateStatus phase, "Deleted backend for server group ${serverGroupName} from ssl load balancer backend service ${backendServiceName}."
    }
  }

  static void destroyTcpLoadBalancerBackends(Compute compute,
                                             String project,
                                             GoogleServerGroup.View serverGroup,
                                             GoogleLoadBalancerProvider googleLoadBalancerProvider,
                                             Task task,
                                             String phase,
                                             GoogleExecutorTraits executor) {
    def serverGroupName = serverGroup.name
    def region = serverGroup.region
    def foundTcpLoadBalancers = googleLoadBalancerProvider.getApplicationLoadBalancers("").findAll {
      it.name in serverGroup.loadBalancers && it.loadBalancerType == GoogleLoadBalancerType.TCP
    }
    List<String> backendServicesToDeleteFrom = []
    if (foundTcpLoadBalancers) {
      backendServicesToDeleteFrom = foundTcpLoadBalancers.collect { lb -> lb.backendService.name }
    } else {
      log.warn("Cache call missed for tcp load balancer, making a call to GCP")
      List<ForwardingRule> projectGlobalForwardingRules = executor.timeExecute(
        compute.globalForwardingRules().list(project),
        "compute.globalForwardingRules.list",
        executor.TAG_SCOPE, executor.SCOPE_GLOBAL
      ).getItems()
      List<TargetTcpProxy> projectTcpProxies = executor.timeExecute(
        compute.targetTcpProxies().list(project),
        "compute.targetTcpProxies.list",
        executor.TAG_SCOPE, executor.SCOPE_GLOBAL
      ).getItems()

      def matchingTcpProxyNames = projectGlobalForwardingRules.findAll { ForwardingRule forwardingRule ->
        forwardingRule.target && Utils.getTargetProxyType(forwardingRule.target) == GoogleTargetProxyType.TCP &&
          forwardingRule.name in serverGroup.loadBalancers
      }.collect { ForwardingRule forwardingRule -> getLocalName(forwardingRule.target) }
      backendServicesToDeleteFrom = projectTcpProxies.findAll { TargetTcpProxy proxy ->
        proxy.getName() in matchingTcpProxyNames
      }.collect { TargetTcpProxy proxy -> getLocalName(proxy.getService()) }
    }

    log.debug("Attempting to delete backends for ${serverGroup.name} from the following global load balancers: ${foundTcpLoadBalancers.collect { it.name }}")
    backendServicesToDeleteFrom?.each { String backendServiceName ->
      BackendService backendService = executor.timeExecute(
        compute.backendServices().get(project, backendServiceName),
        "compute.backendServices.get",
        executor.TAG_SCOPE, executor.SCOPE_GLOBAL)
      backendService?.backends?.removeAll { Backend backend ->
        (getLocalName(backend.group) == serverGroupName) &&
          (Utils.getRegionFromGroupUrl(backend.group) == region)
      }
      executor.timeExecute(
        compute.backendServices().update(project, backendServiceName, backendService),
        "compute.backendServices.update",
        executor.TAG_SCOPE, executor.SCOPE_GLOBAL)
      task.updateStatus phase, "Deleted backend for server group ${serverGroupName} from tcp load balancer backend service ${backendServiceName}."
    }
  }

  static void destroyInternalLoadBalancerBackends(Compute compute,
                                                  String project,
                                                  GoogleServerGroup.View serverGroup,
                                                  GoogleLoadBalancerProvider googleLoadBalancerProvider,
                                                  Task task,
                                                  String phase,
                                                  GoogleExecutorTraits executor) {
    def serverGroupName = serverGroup.name
    def region = serverGroup.region
    def foundInternalLoadBalancers = googleLoadBalancerProvider.getApplicationLoadBalancers("").findAll {
      it.name in serverGroup.loadBalancers && it.loadBalancerType == GoogleLoadBalancerType.INTERNAL
    }

    List<String> backendServicesToDeleteFrom = []
    if (foundInternalLoadBalancers) {
      backendServicesToDeleteFrom = foundInternalLoadBalancers.collect { lb -> lb.backendService.name }
    } else {
      log.warn("Cache call missed for internal load balancer, making a call to GCP")
      List<ForwardingRule> projectForwardingRules = executor.timeExecute(
        compute.forwardingRules().list(project, region),
        "compute.forwardingRules.list",
        executor.TAG_SCOPE, executor.SCOPE_REGIONAL, executor.TAG_REGION, region
      ).getItems()

      def matchingForwardingRules = projectForwardingRules.findAll { ForwardingRule forwardingRule ->
        // TODO(jacobkiefer): Update this check if any other types of loadbalancers support backend services from regional forwarding rules.
        forwardingRule.backendService && forwardingRule.name in serverGroup.loadBalancers
      }
      backendServicesToDeleteFrom = matchingForwardingRules.collect { ForwardingRule forwardingRule ->
        getLocalName(forwardingRule.getBackendService())
      }
    }

    log.debug("Attempting to delete backends for ${serverGroup.name} from the following backend services: ${backendServicesToDeleteFrom}")
    backendServicesToDeleteFrom?.each { String backendServiceName ->
      BackendService backendService = executor.timeExecute(
        compute.regionBackendServices().get(project, region, backendServiceName),
        "compute.regionBackendServices.get",
        executor.TAG_SCOPE, executor.SCOPE_REGIONAL, executor.TAG_REGION, region)
      backendService?.backends?.removeAll { Backend backend ->
        (getLocalName(backend.group) == serverGroupName) &&
          (Utils.getRegionFromGroupUrl(backend.group) == region)
      }
      executor.timeExecute(
        compute.regionBackendServices().update(project, region, backendServiceName, backendService),
        "compute.backendServices.update",
        executor.TAG_SCOPE, executor.SCOPE_GLOBAL)
      task.updateStatus phase, "Deleted backend for server group ${serverGroupName} from internal load balancer backend service ${backendServiceName}."
    }
  }

  static void destroyHttpLoadBalancerBackends(Compute compute,
                                              String project,
                                              GoogleServerGroup.View serverGroup,
                                              GoogleLoadBalancerProvider googleLoadBalancerProvider,
                                              Task task,
                                              String phase,
                                              GoogleExecutorTraits executor) {
    def serverGroupName = serverGroup.name
    def httpLoadBalancersInMetadata = serverGroup?.asg?.get(GoogleServerGroup.View.GLOBAL_LOAD_BALANCER_NAMES) ?: []
    log.debug("Attempting to delete backends for ${serverGroup.name} from the following Http load balancers: ${httpLoadBalancersInMetadata}")

    log.debug("Looking up the following Http load balancers in the cache: ${httpLoadBalancersInMetadata}")
    def foundHttpLoadBalancers = googleLoadBalancerProvider.getApplicationLoadBalancers("").findAll {
      it.name in serverGroup.loadBalancers && it.loadBalancerType == GoogleLoadBalancerType.HTTP
    }
    if (!foundHttpLoadBalancers) {
      log.warn("Cache call missed for Http load balancers ${httpLoadBalancersInMetadata}, making a call to GCP")
      List<ForwardingRule> projectGlobalForwardingRules = executor.timeExecute(
        compute.globalForwardingRules().list(project),
        "compute.globalForwardingRules",
        executor.TAG_SCOPE, executor.SCOPE_GLOBAL
      ).getItems()
      foundHttpLoadBalancers = projectGlobalForwardingRules.findAll { ForwardingRule forwardingRule ->
        forwardingRule.target && Utils.getTargetProxyType(forwardingRule.target) in [GoogleTargetProxyType.HTTP, GoogleTargetProxyType.HTTPS] &&
          forwardingRule.name in serverGroup.loadBalancers
      }
    }

    def notDeleted = httpLoadBalancersInMetadata - (foundHttpLoadBalancers.collect { it.name })
    if (notDeleted) {
      log.warn("Could not locate the following Http load balancers: ${notDeleted}. Proceeding with other backend deletions without mutating them.")
    }

    if (foundHttpLoadBalancers) {
      Metadata instanceMetadata = serverGroup?.launchConfig?.instanceTemplate?.properties?.metadata
      Map metadataMap = buildMapFromMetadata(instanceMetadata)
      List<String> backendServiceNames = metadataMap?.(GoogleServerGroup.View.BACKEND_SERVICE_NAMES)?.split(",")
      if (backendServiceNames) {
        backendServiceNames.each { String backendServiceName ->
          BackendService backendService = executor.timeExecute(
            compute.backendServices().get(project, backendServiceName),
            "compute.backendService.get",
            executor.TAG_SCOPE, executor.SCOPE_GLOBAL)
          backendService?.backends?.removeAll { Backend backend ->
            (getLocalName(backend.group) == serverGroupName) &&
                (Utils.getRegionFromGroupUrl(backend.group) == serverGroup.region)
          }
          executor.timeExecute(
              compute.backendServices().update(project, backendServiceName, backendService),
              "compute.backendServices.update",
              executor.TAG_SCOPE, executor.SCOPE_GLOBAL)
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
  static List<String> resolveHttpLoadBalancerNamesMetadata(List<String> backendServiceNames, Compute compute, String project, GoogleExecutorTraits executor) {
    def loadBalancerNames = []
    def projectUrlMaps = executor.timeExecute(
      compute.urlMaps().list(project),
      "compute.urlMaps.list",
      executor.TAG_SCOPE, executor.SCOPE_GLOBAL
    ).getItems() ?: []
    def servicesByUrlMap = projectUrlMaps.collectEntries { UrlMap urlMap ->
      [(urlMap.name): Utils.getBackendServicesFromUrlMap(urlMap)]
    }

    def urlMapsInUse = []
    backendServiceNames.each { String backendServiceName ->
      servicesByUrlMap.each { urlMapName, services ->
        if (backendServiceName in services) {
          urlMapsInUse << urlMapName
        }
      }
    }

    def globalForwardingRules = executor.timeExecute(
      compute.globalForwardingRules().list(project),
      "compute.globalForwardingRules",
      executor.TAG_SCOPE, executor.SCOPE_GLOBAL
    ).getItems()
    globalForwardingRules.each { ForwardingRule fr ->
      GoogleTargetProxyType proxyType = Utils.getTargetProxyType(fr.target)
      def proxy = null
      switch (proxyType) {
        case GoogleTargetProxyType.HTTP:
          proxy = executor.timeExecute(compute.targetHttpProxies().get(project, getLocalName(fr.target)),
            "compute.targetHttpProxies.get",
            executor.TAG_SCOPE, executor.SCOPE_GLOBAL)
          break
        case GoogleTargetProxyType.HTTPS:
          proxy = executor.timeExecute(compute.targetHttpsProxies().get(project, getLocalName(fr.target)),
            "compute.targetHttpsProxies.get",
            executor.TAG_SCOPE, executor.SCOPE_GLOBAL)
          break
        default:
          break
      }
      if (proxy && getLocalName(proxy.urlMap) in urlMapsInUse) {
        loadBalancerNames << fr.name
      }
    }
    return loadBalancerNames
  }

  def static getTargetProxyFromRule(Compute compute, String project, ForwardingRule forwardingRule, String phase, SafeRetry safeRetry, GoogleExecutorTraits executor) {
    String target = forwardingRule.getTarget()
    GoogleTargetProxyType targetProxyType = Utils.getTargetProxyType(target)
    String targetProxyName = getLocalName(target)

    def operationName
    def proxyGet = null
    switch (targetProxyType) {
      case GoogleTargetProxyType.HTTP:
        proxyGet = { executor.timeExecute(
          compute.targetHttpProxies().get(project, targetProxyName),
          "compute.targetHttpProxies.get",
          executor.TAG_SCOPE, executor.SCOPE_GLOBAL)
        }
        operationName = "compute.targetHttpProxies.get"
        break
      case GoogleTargetProxyType.HTTPS:
        proxyGet = { executor.timeExecute(
          compute.targetHttpsProxies().get(project, targetProxyName),
          "compute.targetHttpsProxies.get",
          executor.TAG_SCOPE, executor.SCOPE_GLOBAL)
        }
        operationName = "compute.targetHttpsProxies.get"
        break
      case GoogleTargetProxyType.SSL:
        proxyGet = { executor.timeExecute(
          compute.targetSslProxies().get(project, targetProxyName),
          "compute.targetSslProxies.get",
          executor.TAG_SCOPE, executor.SCOPE_GLOBAL)
        }
        operationName = "compute.targetSslProxies.get"
        break
      case GoogleTargetProxyType.TCP:
        proxyGet = { executor.timeExecute(
          compute.targetTcpProxies().get(project, targetProxyName),
          "compute.targetTcpProxies.get",
          executor.TAG_SCOPE, executor.SCOPE_GLOBAL)
        }
        operationName = "compute.targetTcpProxies.get"
        break
      default:
        log.warn("Unexpected target proxy type for $targetProxyName.")
        return null
        break
    }
    def retrievedTargetProxy = safeRetry.doRetry(
      proxyGet,
      "Target proxy $targetProxyName",
      null,
      [400, 403, 412],
      [],
      [action: "get", phase: phase, operation: operationName, (executor.TAG_SCOPE): executor.SCOPE_GLOBAL],
      executor.registry
    )
    return retrievedTargetProxy
  }

  /**
   * Deletes an L7/SSL LB global listener, i.e. a global forwarding rule and its target proxy.
   * @param compute
   * @param project
   * @param forwardingRuleName - Name of global forwarding rule to delete (along with its target proxy).
   */
  static Operation deleteGlobalListener(Compute compute,
                                        String project,
                                        String forwardingRuleName,
                                        String phase,
                                        SafeRetry safeRetry,
                                        GoogleExecutorTraits executor) {
    ForwardingRule ruleToDelete = safeRetry.doRetry(
      { executor.timeExecute(
          compute.globalForwardingRules().get(project, forwardingRuleName),
          "compute.globalForwardingRules.get",
          executor.TAG_SCOPE, executor.SCOPE_GLOBAL)
      },
      "global forwarding rule ${forwardingRuleName}",
      null,
      [400, 412],
      [404],
      [action: "get", phase: phase, operation: "compute.globalForwardingRules.get", (executor.TAG_SCOPE): executor.SCOPE_GLOBAL],
      executor.registry
    ) as ForwardingRule
    if (ruleToDelete) {
      def operation_name
      executor.timeExecute(
        compute.globalForwardingRules().delete(project, ruleToDelete.getName()),
        "compute.globalForwardingRules.delete",
        executor.TAG_SCOPE, executor.SCOPE_GLOBAL)
      String targetProxyLink = ruleToDelete.getTarget()
      String targetProxyName = getLocalName(targetProxyLink)
      GoogleTargetProxyType targetProxyType = Utils.getTargetProxyType(targetProxyLink)
      Closure deleteProxyClosure = { null }
      switch (targetProxyType) {
        case GoogleTargetProxyType.HTTP:
          deleteProxyClosure = {
            executor.timeExecute(
              compute.targetHttpProxies().delete(project, targetProxyName),
              "compute.targetHttpProxies.delete",
              executor.TAG_SCOPE, executor.SCOPE_GLOBAL)
          }
          operation_name = "compute.targetHttpProxies.delete"
          break
        case GoogleTargetProxyType.HTTPS:
          deleteProxyClosure = {
            executor.timeExecute(
              compute.targetHttpsProxies().delete(project, targetProxyName),
              "compute.targetHttpsProxies.delete",
              executor.TAG_SCOPE, executor.SCOPE_GLOBAL)
          }
          operation_name = "compute.targetHttpsProxies.delete"
          break
        case GoogleTargetProxyType.SSL:
          deleteProxyClosure = {
            executor.timeExecute(
              compute.targetSslProxies().delete(project, targetProxyName),
              "compute.targetSslProxies.delete",
              executor.TAG_SCOPE, executor.SCOPE_GLOBAL)
          }
          operation_name = "compute.targetSslProxies.delete"
          break
        case GoogleTargetProxyType.TCP:
          deleteProxyClosure = {
            executor.timeExecute(
              compute.targetTcpProxies().delete(project, targetProxyName),
              "compute.targetTcpProxies.delete",
              executor.TAG_SCOPE, executor.SCOPE_GLOBAL)
          }
          operation_name = "compute.targetTcpProxies.delete"
          break
        default:
          log.warn("Unexpected target proxy type for $targetProxyName.")
          break
      }

      Operation result = safeRetry.doRetry(
        deleteProxyClosure,
        "target proxy ${targetProxyName}",
        null,
        [400, 412],
        [404],
        [action: "delete", phase: phase, operation: operation_name, (executor.TAG_SCOPE): executor.SCOPE_GLOBAL],
        executor.registry
      ) as Operation
      return result
    }
  }

  static Operation deleteIfNotInUse(Closure<Operation> closure,
                                    String component,
                                    String project,
                                    Task task,
                                    Map tags,
                                    SafeRetry safeRetry,
                                    GoogleExecutorTraits executor) {
    task.updateStatus tags['phase'], "Deleting $component for $project..."
    Operation deleteOp
    try {
      deleteOp = safeRetry.doRetry(
        closure,
        component,
        task,
        [400, 412],
        [404],
        tags,
        executor.registry
      ) as Operation
    } catch (GoogleJsonResponseException e) {
      if (e.details?.code == 400 && e.details?.errors?.getAt(0)?.reason == "resourceInUseByAnotherResource") {
        log.warn("Could not delete $component for $project, it was in use by another resource.")
        return null
      } else {
        throw e
      }
    }
    return deleteOp
  }

  static Firewall buildFirewallRule(String accountName,
                                    UpsertGoogleSecurityGroupDescription securityGroupDescription,
                                    Task task,
                                    String phase,
                                    GoogleNetworkProvider googleNetworkProvider) {
    def network = queryNetwork(accountName, securityGroupDescription.network, task, phase, googleNetworkProvider)
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

  /**
   * Used in LB upserts to tell if HealthCheck objects are different.
   * @param existingHealthCheck
   * @param descriptionHealthCheck
   * @return
   */
  public static Boolean healthCheckShouldBeUpdated(existingHealthCheck, GoogleHealthCheck descriptionHealthCheck) {
    Boolean shouldUpdate = descriptionHealthCheck.checkIntervalSec != existingHealthCheck.getCheckIntervalSec() ||
      descriptionHealthCheck.healthyThreshold != existingHealthCheck.getHealthyThreshold() ||
      descriptionHealthCheck.unhealthyThreshold != existingHealthCheck.getUnhealthyThreshold() ||
      descriptionHealthCheck.timeoutSec != existingHealthCheck.getTimeoutSec()

    switch (descriptionHealthCheck.healthCheckType) {
      case GoogleHealthCheck.HealthCheckType.HTTP:
        shouldUpdate |= (descriptionHealthCheck.port != existingHealthCheck.httpHealthCheck.port ||
          descriptionHealthCheck.requestPath != existingHealthCheck.httpHealthCheck.requestPath)
        break
      case GoogleHealthCheck.HealthCheckType.HTTPS:
        shouldUpdate |= (descriptionHealthCheck.port != existingHealthCheck.httpsHealthCheck.port ||
          descriptionHealthCheck.requestPath != existingHealthCheck.httpsHealthCheck.requestPath)
        break
      case GoogleHealthCheck.HealthCheckType.TCP:
        shouldUpdate |= descriptionHealthCheck.port != existingHealthCheck.tcpHealthCheck.port
        break
      case GoogleHealthCheck.HealthCheckType.SSL:
        shouldUpdate |= descriptionHealthCheck.port != existingHealthCheck.sslHealthCheck.port
        break
      case GoogleHealthCheck.HealthCheckType.UDP:
        shouldUpdate |= descriptionHealthCheck.port != existingHealthCheck.udpHealthCheck.port
        break
      default:
        throw new IllegalArgumentException("Description contains illegal health check type.")
        break
    }
    return shouldUpdate
  }

  /**
   * Updates existingHealthCheck with the attributes in descriptionHealthCheck. Used in LB upserts.
   * @param existingHealthCheck
   * @param descriptionHealthCheck
   */
  public static void updateExistingHealthCheck(HealthCheck existingHealthCheck, GoogleHealthCheck descriptionHealthCheck) {
    existingHealthCheck.checkIntervalSec = descriptionHealthCheck.checkIntervalSec
    existingHealthCheck.healthyThreshold = descriptionHealthCheck.healthyThreshold
    existingHealthCheck.unhealthyThreshold = descriptionHealthCheck.unhealthyThreshold
    existingHealthCheck.timeoutSec = descriptionHealthCheck.timeoutSec

    switch (descriptionHealthCheck.healthCheckType) {
      case GoogleHealthCheck.HealthCheckType.HTTP:
        existingHealthCheck.httpHealthCheck.port = descriptionHealthCheck.port
        existingHealthCheck.httpHealthCheck.requestPath = descriptionHealthCheck.requestPath
        break
      case GoogleHealthCheck.HealthCheckType.HTTPS:
        existingHealthCheck.httpsHealthCheck.port = descriptionHealthCheck.port
        existingHealthCheck.httpsHealthCheck.requestPath = descriptionHealthCheck.requestPath
        break
      case GoogleHealthCheck.HealthCheckType.TCP:
        existingHealthCheck.tcpHealthCheck.port = descriptionHealthCheck.port
        break
      case GoogleHealthCheck.HealthCheckType.SSL:
        existingHealthCheck.sslHealthCheck.port = descriptionHealthCheck.port
        break
      case GoogleHealthCheck.HealthCheckType.UDP:
        existingHealthCheck.udpHealthCheck.port = descriptionHealthCheck.port
        break
      default:
        throw new IllegalArgumentException("Description contains illegal health check type.")
        break
    }
  }

  /**
   * Creates a new HealthCheck from a GoogleHealthCheck.
   * @param descriptionHealthCheck
   * @return
   */
  public static HealthCheck createNewHealthCheck(GoogleHealthCheck descriptionHealthCheck) {
    def newHealthCheck = new HealthCheck(
      name: descriptionHealthCheck.name,
      checkIntervalSec: descriptionHealthCheck.checkIntervalSec,
      healthyThreshold: descriptionHealthCheck.healthyThreshold,
      unhealthyThreshold: descriptionHealthCheck.unhealthyThreshold,
      timeoutSec: descriptionHealthCheck.timeoutSec,
    )
    switch (descriptionHealthCheck.healthCheckType) {
      case GoogleHealthCheck.HealthCheckType.HTTP:
        newHealthCheck.type = 'HTTP'
        newHealthCheck.httpHealthCheck = new HttpHealthCheck(
          port: descriptionHealthCheck.port,
          requestPath: descriptionHealthCheck.requestPath,
        )
        break
      case GoogleHealthCheck.HealthCheckType.HTTPS:
        newHealthCheck.type = 'HTTPS'
        newHealthCheck.httpsHealthCheck = new HttpsHealthCheck(
          port: descriptionHealthCheck.port,
          requestPath: descriptionHealthCheck.requestPath,
        )
        break
      case GoogleHealthCheck.HealthCheckType.TCP:
        newHealthCheck.type = 'TCP'
        newHealthCheck.tcpHealthCheck = new TCPHealthCheck(port: descriptionHealthCheck.port)
        break
      case GoogleHealthCheck.HealthCheckType.SSL:
        newHealthCheck.type = 'SSL'
        newHealthCheck.sslHealthCheck = new SSLHealthCheck(port:  descriptionHealthCheck.port)
        break
      case GoogleHealthCheck.HealthCheckType.UDP:
        newHealthCheck.type = 'UDP'
        newHealthCheck.udpHealthCheck = new UDPHealthCheck(port:  descriptionHealthCheck.port)
        break
      default:
        throw new IllegalArgumentException("Description contains illegal health check type.")
        break
    }
    return newHealthCheck
  }

  static List<BackendService> fetchBackendServices(GoogleExecutorTraits agent, Compute compute, String project) {
    return agent.timeExecute(
      compute.backendServices().list(project),
      "compute.backendServices.list",
      agent.TAG_SCOPE, agent.SCOPE_GLOBAL).getItems()
  }

  static List<BackendService> fetchRegionBackendServices(GoogleExecutorTraits agent, Compute compute, String project, String region) {
    return agent.timeExecute(
      compute.regionBackendServices().list(project, region),
      "compute.regionBackendServices.list",
      agent.TAG_SCOPE, agent.SCOPE_REGIONAL, agent.TAG_REGION, region).getItems()
  }

  static List<HttpHealthCheck> fetchHttpHealthChecks(GoogleExecutorTraits agent, Compute compute, String project) {
    Boolean executedAtLeastOnce = false
    String nextPageToken = null
    List<HttpHealthCheck> httpHealthChecks = []
    while (!executedAtLeastOnce || nextPageToken) {
      HttpHealthCheckList httpHealthCheckList = agent.timeExecute(
        compute.httpHealthChecks().list(project).setPageToken(nextPageToken),
        "compute.httpHealthChecks.list",
        agent.TAG_SCOPE, agent.SCOPE_GLOBAL)

      executedAtLeastOnce = true
      nextPageToken = httpHealthCheckList.getNextPageToken()
      httpHealthChecks.addAll(httpHealthCheckList.getItems() ?: [])
    }
    return httpHealthChecks
  }

  static List<HttpsHealthCheck> fetchHttpsHealthChecks(GoogleExecutorTraits agent, Compute compute, String project) {
    Boolean executedAtLeastOnce = false
    String nextPageToken = null
    List<HttpsHealthCheck> httpsHealthChecks = []
    while (!executedAtLeastOnce || nextPageToken) {
      HttpsHealthCheckList httpsHealthCheckList = agent.timeExecute(
        compute.httpsHealthChecks().list(project).setPageToken(nextPageToken),
        "compute.httpsHealthChecks.list",
        agent.TAG_SCOPE, agent.SCOPE_GLOBAL)

      executedAtLeastOnce = true
      nextPageToken = httpsHealthCheckList.getNextPageToken()
      httpsHealthChecks.addAll(httpsHealthCheckList.getItems() ?: [])
    }
    return httpsHealthChecks
  }

  static List<HealthCheck> fetchHealthChecks(GoogleExecutorTraits agent, Compute compute, String project) {
    Boolean executedAtLeastOnce = false
    String nextPageToken = null
    List<HealthCheck> healthChecks = []
    while (!executedAtLeastOnce || nextPageToken) {
      HealthCheckList healthCheckList = agent.timeExecute(
        compute.healthChecks().list(project).setPageToken(nextPageToken),
        "compute.healthChecks.list",
        agent.TAG_SCOPE, agent.SCOPE_GLOBAL)

      executedAtLeastOnce = true
      nextPageToken = healthCheckList.getNextPageToken()
      healthChecks.addAll(healthCheckList.getItems() ?: [])
    }
    return healthChecks
  }

  static List<GoogleInstance> fetchInstances(GoogleExecutorTraits agent, GoogleNamedAccountCredentials credentials) {
    List<GoogleInstance> instances = new ArrayList<GoogleInstance>()
    String pageToken = null

    while (true) {
      InstanceAggregatedList instanceAggregatedList = agent.timeExecute(
        credentials.compute.instances().aggregatedList(credentials.project).setPageToken(pageToken),
        "compute.instances.aggregatedList",
        agent.TAG_SCOPE, agent.SCOPE_GLOBAL)

      instances += transformInstances(instanceAggregatedList, credentials)
      pageToken = instanceAggregatedList.getNextPageToken()

      if (!pageToken) {
        break
      }
    }

    return instances
  }

  private static List<GoogleInstance> transformInstances(InstanceAggregatedList instanceAggregatedList, GoogleNamedAccountCredentials credentials) throws IOException {
    List<GoogleInstance> instances = []

    instanceAggregatedList?.items?.each { String zone, InstancesScopedList instancesScopedList ->
      def localZoneName = Utils.getLocalName(zone)
      instancesScopedList?.instances?.each { Instance instance ->
        def consulNode = credentials.consulConfig?.enabled ?
          ConsulProviderUtils.getHealths(credentials.consulConfig, instance.getName())
          : null
        long instanceTimestamp = instance.creationTimestamp ?
          Utils.getTimeFromTimestamp(instance.creationTimestamp) :
          Long.MAX_VALUE
        String instanceName = Utils.getLocalName(instance.name)
        def googleInstance = new GoogleInstance(
          name: instanceName,
          gceId: instance.id,
          instanceType: Utils.getLocalName(instance.machineType),
          cpuPlatform: instance.cpuPlatform,
          launchTime: instanceTimestamp,
          zone: localZoneName,
          region: credentials.regionFromZone(localZoneName),
          networkInterfaces: instance.networkInterfaces,
          networkName: Utils.decorateXpnResourceIdIfNeeded(credentials.project, instance.networkInterfaces?.getAt(0)?.network),
          metadata: instance.metadata,
          disks: instance.disks,
          serviceAccounts: instance.serviceAccounts,
          selfLink: instance.selfLink,
          tags: instance.tags,
          labels: instance.labels,
          consulNode: consulNode,
          instanceHealth: new GoogleInstanceHealth(
            status: GoogleInstanceHealth.Status.valueOf(instance.getStatus())
          ))
        instances << googleInstance
      }
    }

    return instances
  }

  static void handleHealthObject(GoogleLoadBalancer googleLoadBalancer,
                                 Object healthObject) {
    // Note: GCE callbacks aren't well-typed so this must be an Object.
    healthObject.healthStatus?.each { HealthStatus status ->
      def instanceName = Utils.getLocalName(status.instance)
      def googleLBHealthStatus = GoogleLoadBalancerHealth.PlatformStatus.valueOf(status.healthState)

      googleLoadBalancer.healths << new GoogleLoadBalancerHealth(
        instanceName: instanceName,
        instanceZone: Utils.getZoneFromInstanceUrl(status.instance),
        status: googleLBHealthStatus,
        lbHealthSummaries: [
          new GoogleLoadBalancerHealth.LBHealthSummary(
            loadBalancerName: googleLoadBalancer.name,
            instanceId: instanceName,
            state: googleLBHealthStatus.toServiceStatus(),
          )
        ]
      )
    }
  }
}
