/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.google.provider.agent

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.api.client.googleapis.batch.json.JsonBatchCallback
import com.google.api.client.googleapis.json.GoogleJsonError
import com.google.api.client.http.HttpHeaders
import com.google.api.services.compute.Compute
import com.google.api.services.compute.ComputeRequest
import com.google.api.services.compute.model.*
import com.netflix.frigga.Names
import com.netflix.frigga.ami.AppVersion
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent
import com.netflix.spinnaker.clouddriver.cache.OnDemandMetricsSupport
import com.netflix.spinnaker.clouddriver.google.GoogleCloudProvider
import com.netflix.spinnaker.clouddriver.google.GoogleExecutorTraits
import com.netflix.spinnaker.clouddriver.google.cache.CacheResultBuilder
import com.netflix.spinnaker.clouddriver.google.cache.Keys
import com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil
import com.netflix.spinnaker.clouddriver.google.model.GoogleInstance
import com.netflix.spinnaker.clouddriver.google.model.GoogleServerGroup
import com.netflix.spinnaker.clouddriver.google.model.callbacks.Utils
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleHttpLoadBalancingPolicy
import com.netflix.spinnaker.clouddriver.googlecommon.batch.GoogleBatchRequest
import com.netflix.spinnaker.clouddriver.google.provider.agent.util.PaginatedRequest
import com.netflix.spinnaker.clouddriver.google.security.AccountForClient
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.googlecommon.GoogleExecutor
import groovy.transform.Canonical
import groovy.util.logging.Slf4j

import java.util.concurrent.TimeUnit

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.INFORMATIVE
import static com.netflix.spinnaker.clouddriver.google.cache.Keys.Namespace.*

@Slf4j
class GoogleZonalServerGroupCachingAgent extends AbstractGoogleCachingAgent implements OnDemandAgent, GoogleExecutorTraits {

  static final String GLOBAL_LOAD_BALANCER_NAMES = GoogleServerGroup.View.GLOBAL_LOAD_BALANCER_NAMES
  static final String REGIONAL_LOAD_BALANCER_NAMES = GoogleServerGroup.View.REGIONAL_LOAD_BALANCER_NAMES
  static final String BACKEND_SERVICE_NAMES = GoogleServerGroup.View.BACKEND_SERVICE_NAMES
  static final String LOAD_BALANCING_POLICY = GoogleServerGroup.View.LOAD_BALANCING_POLICY
  final String region
  final long maxMIGPageSize

  final Set<AgentDataType> providedDataTypes = [
    AUTHORITATIVE.forType(SERVER_GROUPS.ns),
    AUTHORITATIVE.forType(APPLICATIONS.ns),
    INFORMATIVE.forType(CLUSTERS.ns),
    INFORMATIVE.forType(LOAD_BALANCERS.ns),
  ] as Set

  String agentType = "${accountName}/${region}/${GoogleZonalServerGroupCachingAgent.simpleName}"
  String onDemandAgentType = "${agentType}-OnDemand"
  final OnDemandMetricsSupport metricsSupport

  GoogleZonalServerGroupCachingAgent(String clouddriverUserAgentApplicationName,
                                     GoogleNamedAccountCredentials credentials,
                                     ObjectMapper objectMapper,
                                     Registry registry,
                                     String region,
                                     long maxMIGPageSize) {
    super(clouddriverUserAgentApplicationName,
          credentials,
          objectMapper,
          registry)
    this.region = region
    this.maxMIGPageSize = maxMIGPageSize
    this.metricsSupport = new OnDemandMetricsSupport(
      registry,
      this,
      "${GoogleCloudProvider.ID}:${OnDemandAgent.OnDemandType.ServerGroup}")
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    def cacheResultBuilder = new CacheResultBuilder(startTime: System.currentTimeMillis())

    List<GoogleServerGroup> serverGroups = getServerGroups(providerCache)
    def serverGroupKeys = serverGroups.collect { getServerGroupKey(it) }

    providerCache.getAll(ON_DEMAND.ns, serverGroupKeys).each { CacheData cacheData ->
      // Ensure that we don't overwrite data that was inserted by the `handle` method while we retrieved the
      // managed instance groups. Furthermore, cache data that hasn't been moved to the proper namespace needs to be
      // updated in the ON_DEMAND cache, so don't evict data without a processedCount > 0.
      if (cacheData.attributes.cacheTime < cacheResultBuilder.startTime && cacheData.attributes.processedCount > 0) {
        cacheResultBuilder.onDemand.toEvict << cacheData.id
      } else {
        cacheResultBuilder.onDemand.toKeep[cacheData.id] = cacheData
      }
    }

    CacheResult cacheResults = buildCacheResult(cacheResultBuilder, serverGroups)

    cacheResults.cacheResults[ON_DEMAND.ns].each { CacheData cacheData ->
      cacheData.attributes.processedTime = System.currentTimeMillis()
      cacheData.attributes.processedCount = (cacheData.attributes.processedCount ?: 0) + 1
    }

    cacheResults
  }

  private List<GoogleServerGroup> getServerGroups(ProviderCache providerCache) {
    constructServerGroups(providerCache)
  }

  private GoogleServerGroup getServerGroup(ProviderCache providerCache, String onDemandServerGroupName) {
    def serverGroups = constructServerGroups(providerCache, onDemandServerGroupName)
    serverGroups ? serverGroups.first() : null
  }

  private List<GoogleServerGroup> constructServerGroups(ProviderCache providerCache, String onDemandServerGroupName = null) {
    GoogleZonalServerGroupCachingAgent cachingAgent = this
    List<String> zones = credentials.getZonesFromRegion(region)
    List<GoogleServerGroup> serverGroups = []

    GoogleBatchRequest igmRequest = buildGoogleBatchRequest()
    GoogleBatchRequest instanceGroupsRequest = buildGoogleBatchRequest()
    GoogleBatchRequest autoscalerRequest = buildGoogleBatchRequest()

    List<InstanceTemplate> instanceTemplates = fetchInstanceTemplates(cachingAgent, compute, project)
    List<GoogleInstance> instances = GCEUtil.fetchInstances(this, credentials)

    zones?.each { String zone ->
      InstanceGroupManagerCallbacks instanceGroupManagerCallbacks = new InstanceGroupManagerCallbacks(
        providerCache: providerCache,
        serverGroups: serverGroups,
        zone: zone,
        instanceGroupsRequest: instanceGroupsRequest,
        autoscalerRequest: autoscalerRequest,
        instances: instances)
      if (onDemandServerGroupName) {
        InstanceGroupManagerCallbacks.InstanceGroupManagerSingletonCallback igmCallback =
          instanceGroupManagerCallbacks.newInstanceGroupManagerSingletonCallback(instanceTemplates, instances)
        igmRequest.queue(compute.instanceGroupManagers().get(project, zone, onDemandServerGroupName), igmCallback)
      } else {
        InstanceGroupManagerCallbacks.InstanceGroupManagerListCallback igmlCallback =
          instanceGroupManagerCallbacks.newInstanceGroupManagerListCallback(instanceTemplates, instances)
        new PaginatedRequest<InstanceGroupManagerList>(cachingAgent) {
          @Override
          ComputeRequest<InstanceGroupManagerList> request(String pageToken) {
            return compute.instanceGroupManagers().list(project, zone).setMaxResults(maxMIGPageSize).setPageToken(pageToken)
          }

          @Override
          String getNextPageToken(InstanceGroupManagerList instanceGroupManagerList) {
            return instanceGroupManagerList.getNextPageToken()
          }
        }.queue(igmRequest, igmlCallback, "ZonalServerGroupCaching.igm")
      }
    }
    executeIfRequestsAreQueued(igmRequest, "ZonalServerGroupCaching.igm")
    executeIfRequestsAreQueued(instanceGroupsRequest, "ZonalServerGroupCaching.instanceGroups")
    executeIfRequestsAreQueued(autoscalerRequest, "ZonalServerGroupCaching.autoscaler")

    serverGroups
  }

  static List<InstanceTemplate> fetchInstanceTemplates(AbstractGoogleCachingAgent cachingAgent, Compute compute, String project) {
    List<InstanceTemplate> instanceTemplates = new PaginatedRequest<InstanceTemplateList>(cachingAgent) {
      @Override
      protected ComputeRequest<InstanceTemplateList> request (String pageToken) {
        return compute.instanceTemplates().list(project).setPageToken(pageToken)
      }

      @Override
      String getNextPageToken(InstanceTemplateList t) {
        return t.getNextPageToken();
      }
    }.timeExecute(
      { InstanceTemplateList list -> list.getItems() },
      "compute.instanceTemplates.list", GoogleExecutor.TAG_SCOPE, GoogleExecutor.SCOPE_GLOBAL,
      "account", AccountForClient.getAccount(compute)
    )

    return instanceTemplates
  }

  @Override
  boolean handles(OnDemandAgent.OnDemandType type, String cloudProvider) {
    type == OnDemandAgent.OnDemandType.ServerGroup && cloudProvider == GoogleCloudProvider.ID
  }

  @Override
  OnDemandAgent.OnDemandResult handle(ProviderCache providerCache, Map<String, ? extends Object> data) {
    if (!data.containsKey("serverGroupName") || data.account != accountName || data.region != region) {
      return null
    }

    GoogleServerGroup serverGroup = metricsSupport.readData {
      getServerGroup(providerCache, data.serverGroupName as String)
    }

    if (serverGroup?.regional) {
      return null
    }

    String serverGroupKey
    Collection<String> identifiers = []

    if (serverGroup) {
      serverGroupKey = getServerGroupKey(serverGroup)
    } else {
      serverGroupKey = Keys.getServerGroupKey(data.serverGroupName as String, accountName, region, "*")

      // No server group was found, so need to find identifiers for all zonal server groups in the region.
      identifiers = providerCache.filterIdentifiers(SERVER_GROUPS.ns, serverGroupKey)
    }

    def cacheResultBuilder = new CacheResultBuilder(startTime: Long.MAX_VALUE)
    CacheResult result = metricsSupport.transformData {
      buildCacheResult(cacheResultBuilder, serverGroup ? [serverGroup] : [])
    }

    if (result.cacheResults.values().flatten().empty) {
      // Avoid writing an empty onDemand cache record (instead delete any that may have previously existed).
      providerCache.evictDeletedItems(ON_DEMAND.ns, identifiers)
    } else {
      metricsSupport.onDemandStore {
        def cacheData = new DefaultCacheData(
            serverGroupKey,
            TimeUnit.MINUTES.toSeconds(10) as Integer, // ttl
            [
                cacheTime     : System.currentTimeMillis(),
                cacheResults  : objectMapper.writeValueAsString(result.cacheResults),
                processedCount: 0,
                processedTime : null
            ],
            [:]
        )

        providerCache.putCacheData(ON_DEMAND.ns, cacheData)
      }
    }

    Map<String, Collection<String>> evictions = [:].withDefault {_ -> []}
    if (!serverGroup) {
      evictions[SERVER_GROUPS.ns].addAll(identifiers)
    }

    log.info("On demand cache refresh succeeded. Data: ${data}. Added ${serverGroup ? 1 : 0} items to the cache. Evicted ${evictions[SERVER_GROUPS.ns]}.")

    return new OnDemandAgent.OnDemandResult(
        sourceAgentType: getOnDemandAgentType(),
        cacheResult: result,
        evictions: evictions,
        // Do not include "authoritativeTypes" here, as it will result in all other cache entries getting deleted!
    )
  }

  @Override
  Collection<Map> pendingOnDemandRequests(ProviderCache providerCache) {
    def keyOwnedByThisAgent = { Map<String, String> parsedKey ->
      parsedKey && parsedKey.account == accountName && parsedKey.region == region && parsedKey.zone
    }

    def keys = providerCache.getIdentifiers(ON_DEMAND.ns).findAll { String key ->
      keyOwnedByThisAgent(Keys.parse(key))
    }

    providerCache.getAll(ON_DEMAND.ns, keys).collect { CacheData cacheData ->
      def details = Keys.parse(cacheData.id)

      [
          details       : details,
          moniker       : convertOnDemandDetails(details),
          cacheTime     : cacheData.attributes.cacheTime,
          processedCount: cacheData.attributes.processedCount,
          processedTime : cacheData.attributes.processedTime
      ]
    }

  }

  private CacheResult buildCacheResult(CacheResultBuilder cacheResultBuilder, List<GoogleServerGroup> serverGroups) {
    log.info "Describing items in $agentType"

    serverGroups.each { GoogleServerGroup serverGroup ->
      def names = Names.parseName(serverGroup.name)
      def applicationName = names.app
      def clusterName = names.cluster

      def serverGroupKey = getServerGroupKey(serverGroup)
      def clusterKey = Keys.getClusterKey(accountName, applicationName, clusterName)
      def appKey = Keys.getApplicationKey(applicationName)

      def loadBalancerKeys = []
      def instanceKeys = serverGroup?.instances?.collect { Keys.getInstanceKey(accountName, region, it.name) } ?: []

      cacheResultBuilder.namespace(APPLICATIONS.ns).keep(appKey).with {
        attributes.name = applicationName
        relationships[CLUSTERS.ns].add(clusterKey)
        relationships[INSTANCES.ns].addAll(instanceKeys)
      }

      cacheResultBuilder.namespace(CLUSTERS.ns).keep(clusterKey).with {
        attributes.name = clusterName
        attributes.accountName = accountName
        relationships[APPLICATIONS.ns].add(appKey)
        relationships[SERVER_GROUPS.ns].add(serverGroupKey)
        relationships[INSTANCES.ns].addAll(instanceKeys)
      }
      log.debug("Writing cache entry for cluster key ${clusterKey} adding relationships for application ${appKey} and server group ${serverGroupKey}")

      populateLoadBalancerKeys(serverGroup, loadBalancerKeys, accountName, region)

      loadBalancerKeys.each { String loadBalancerKey ->
        cacheResultBuilder.namespace(LOAD_BALANCERS.ns).keep(loadBalancerKey).with {
          relationships[SERVER_GROUPS.ns].add(serverGroupKey)
        }
      }

      if (shouldUseOnDemandData(cacheResultBuilder, serverGroupKey)) {
        moveOnDemandDataToNamespace(cacheResultBuilder, serverGroup)
      } else {
        cacheResultBuilder.namespace(SERVER_GROUPS.ns).keep(serverGroupKey).with {
          attributes = objectMapper.convertValue(serverGroup, ATTRIBUTES)
          relationships[APPLICATIONS.ns].add(appKey)
          relationships[CLUSTERS.ns].add(clusterKey)
          relationships[LOAD_BALANCERS.ns].addAll(loadBalancerKeys)
          relationships[INSTANCES.ns].addAll(instanceKeys)
        }
      }
    }

    log.info("Caching ${cacheResultBuilder.namespace(APPLICATIONS.ns).keepSize()} applications in ${agentType}")
    log.info("Caching ${cacheResultBuilder.namespace(CLUSTERS.ns).keepSize()} clusters in ${agentType}")
    log.info("Caching ${cacheResultBuilder.namespace(SERVER_GROUPS.ns).keepSize()} server groups in ${agentType}")
    log.info("Caching ${cacheResultBuilder.namespace(LOAD_BALANCERS.ns).keepSize()} load balancer relationships in ${agentType}")
    log.info("Caching ${cacheResultBuilder.onDemand.toKeep.size()} onDemand entries in ${agentType}")
    log.info("Evicting ${cacheResultBuilder.onDemand.toEvict.size()} onDemand entries in ${agentType}")

    cacheResultBuilder.build()
  }

  static boolean shouldUseOnDemandData(CacheResultBuilder cacheResultBuilder, String serverGroupKey) {
    CacheData cacheData = cacheResultBuilder.onDemand.toKeep[serverGroupKey]
    return cacheData ? cacheData.attributes.cacheTime >= cacheResultBuilder.startTime : false
  }

  void moveOnDemandDataToNamespace(CacheResultBuilder cacheResultBuilder,
                                   GoogleServerGroup googleServerGroup) {
    def serverGroupKey = getServerGroupKey(googleServerGroup)
    Map<String, List<MutableCacheData>> onDemandData = objectMapper.readValue(
      cacheResultBuilder.onDemand.toKeep[serverGroupKey].attributes.cacheResults as String,
      new TypeReference<Map<String, List<MutableCacheData>>>() {})

    onDemandData.each { String namespace, List<MutableCacheData> cacheDatas ->
      if (namespace != 'onDemand') {
        cacheDatas.each { MutableCacheData cacheData ->
          cacheResultBuilder.namespace(namespace).keep(cacheData.id).with { it ->
            it.attributes = cacheData.attributes
            it.relationships = Utils.mergeOnDemandCacheRelationships(cacheData.relationships, it.relationships)
          }
          cacheResultBuilder.onDemand.toKeep.remove(cacheData.id)
        }
      }
    }
  }

  String getServerGroupKey(GoogleServerGroup googleServerGroup) {
    return Keys.getServerGroupKey(googleServerGroup.name, accountName, region, googleServerGroup.zone)
  }

  // TODO(lwander) this was taken from the netflix cluster caching, and should probably be shared between all providers.
  @Canonical
  static class MutableCacheData implements CacheData {
    String id
    int ttlSeconds = -1
    Map<String, Object> attributes = [:]
    Map<String, Collection<String>> relationships = [:].withDefault { [] as Set }
  }

  class InstanceGroupManagerCallbacks {

    ProviderCache providerCache
    List<GoogleServerGroup> serverGroups
    String zone
    GoogleBatchRequest instanceGroupsRequest
    GoogleBatchRequest autoscalerRequest
    List<GoogleInstance> instances

    InstanceGroupManagerSingletonCallback<InstanceGroupManager> newInstanceGroupManagerSingletonCallback(List<InstanceTemplate> instanceTemplates, List<GoogleInstance> instances) {
      return new InstanceGroupManagerSingletonCallback<InstanceGroupManager>(instanceTemplates: instanceTemplates, instances: instances)
    }

    InstanceGroupManagerListCallback<InstanceGroupManagerList> newInstanceGroupManagerListCallback(List<InstanceTemplate> instanceTemplates, List<GoogleInstance> instances) {
      return new InstanceGroupManagerListCallback<InstanceGroupManagerList>(instanceTemplates: instanceTemplates, instances: instances)
    }

    class InstanceGroupManagerSingletonCallback<InstanceGroupManager> extends JsonBatchCallback<InstanceGroupManager> {

      List<InstanceTemplate> instanceTemplates
      List<GoogleInstance> instances

      @Override
      void onFailure(GoogleJsonError e, HttpHeaders responseHeaders) throws IOException {
        // 404 is thrown if the managed instance group does not exist in the given zone. Any other exception needs to be propagated.
        if (e.code != 404) {
          def errorJson = new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(e)
          log.error errorJson
        }
      }

      @Override
      void onSuccess(InstanceGroupManager instanceGroupManager, HttpHeaders responseHeaders) throws IOException {
        if (Names.parseName(instanceGroupManager.name)) {
          GoogleServerGroup serverGroup = buildServerGroupFromInstanceGroupManager(instanceGroupManager, instances)
          serverGroups << serverGroup

          populateInstanceTemplate(providerCache, instanceGroupManager, serverGroup, instanceTemplates)

          def autoscalerCallback = new AutoscalerSingletonCallback(serverGroup: serverGroup)
          autoscalerRequest.queue(compute.autoscalers().get(project, zone, serverGroup.name), autoscalerCallback)
        }
      }
    }

    class InstanceGroupManagerListCallback<InstanceGroupManagerList> extends JsonBatchCallback<InstanceGroupManagerList> implements FailureLogger {

      List<InstanceTemplate> instanceTemplates
      List<GoogleInstance> instances

      @Override
      void onSuccess(InstanceGroupManagerList instanceGroupManagerList, HttpHeaders responseHeaders) throws IOException {
        instanceGroupManagerList?.items?.each { InstanceGroupManager instanceGroupManager ->
          if (Names.parseName(instanceGroupManager.name)) {
            GoogleServerGroup serverGroup = buildServerGroupFromInstanceGroupManager(instanceGroupManager, instances)
            serverGroups << serverGroup

            populateInstanceTemplate(providerCache, instanceGroupManager, serverGroup, instanceTemplates)
          }
        }

        def autoscalerCallback = new AutoscalerAggregatedListCallback(serverGroups: serverGroups)
        autoscalerRequest.queue(compute.autoscalers().aggregatedList(project), autoscalerCallback)
      }
    }

    GoogleServerGroup buildServerGroupFromInstanceGroupManager(InstanceGroupManager instanceGroupManager, List<GoogleInstance> instances) {
      String zone = Utils.getLocalName(instanceGroupManager.zone)
      List<GoogleInstance> groupInstances = instances.findAll { it.getName().startsWith(instanceGroupManager.getBaseInstanceName()) && it.getZone() == zone }

      Map<String, Integer> namedPorts = [:]
      instanceGroupManager.namedPorts.each { namedPorts[(it.name)] = it.port }
      return new GoogleServerGroup(
          name: instanceGroupManager.name,
          instances: groupInstances,
          region: region,
          zone: zone,
          namedPorts: namedPorts,
          zones: [zone],
          selfLink: instanceGroupManager.selfLink,
          currentActions: instanceGroupManager.currentActions,
          launchConfig: [createdTime: Utils.getTimeFromTimestamp(instanceGroupManager.creationTimestamp)],
          asg: [minSize        : instanceGroupManager.targetSize,
                maxSize        : instanceGroupManager.targetSize,
                desiredCapacity: instanceGroupManager.targetSize],
          autoHealingPolicy: instanceGroupManager.autoHealingPolicies?.getAt(0)
      )
    }

    void populateInstanceTemplate(ProviderCache providerCache, InstanceGroupManager instanceGroupManager,
                                  GoogleServerGroup serverGroup, List<InstanceTemplate> instanceTemplates) {
      String instanceTemplateName = Utils.getLocalName(instanceGroupManager.instanceTemplate)
      List<String> loadBalancerNames =
        Utils.deriveNetworkLoadBalancerNamesFromTargetPoolUrls(instanceGroupManager.getTargetPools())
      InstanceTemplate template = instanceTemplates.find { it -> it.getName() == instanceTemplateName }
      populateServerGroupWithTemplate(serverGroup, providerCache, loadBalancerNames, template, accountName, project, objectMapper)
    }
  }

  static void populateServerGroupWithTemplate(GoogleServerGroup serverGroup, ProviderCache providerCache,
                                              List<String> loadBalancerNames, InstanceTemplate instanceTemplate,
                                              String accountName, String project, ObjectMapper objectMapper) {
    serverGroup.with {
      networkName = Utils.decorateXpnResourceIdIfNeeded(project, instanceTemplate?.properties?.networkInterfaces?.getAt(0)?.network)
      canIpForward = instanceTemplate?.properties?.canIpForward
      instanceTemplateTags = instanceTemplate?.properties?.tags?.items
      instanceTemplateLabels = instanceTemplate?.properties?.labels
      launchConfig.with {
        launchConfigurationName = instanceTemplate?.name
        instanceType = instanceTemplate?.properties?.machineType
        minCpuPlatform = instanceTemplate?.properties?.minCpuPlatform
      }
    }
    // "instanceTemplate = instanceTemplate" in the above ".with{ }" blocks doesn't work because Groovy thinks it's
    // assigning the same variable to itself, instead of to the "launchConfig" entry
    serverGroup.launchConfig.instanceTemplate = instanceTemplate

    sortWithBootDiskFirst(serverGroup)

    def sourceImageUrl = instanceTemplate?.properties?.disks?.find { disk ->
      disk.boot
    }?.initializeParams?.sourceImage
    if (sourceImageUrl) {
      serverGroup.launchConfig.imageId = Utils.getLocalName(sourceImageUrl)

      def imageKey = Keys.getImageKey(accountName, serverGroup.launchConfig.imageId)
      def image = providerCache.get(IMAGES.ns, imageKey)

      extractBuildInfo(image?.attributes?.image?.description, serverGroup)
    }

    def instanceMetadata = instanceTemplate?.properties?.metadata
    setLoadBalancerMetadataOnInstance(loadBalancerNames, instanceMetadata, serverGroup, objectMapper)
  }

  static void populateLoadBalancerKeys(GoogleServerGroup serverGroup, List<String> loadBalancerKeys, String accountName, String region) {
    serverGroup.asg.get(REGIONAL_LOAD_BALANCER_NAMES).each { String loadBalancerName ->
      loadBalancerKeys << Keys.getLoadBalancerKey(region, accountName, loadBalancerName)
    }
    serverGroup.asg.get(GLOBAL_LOAD_BALANCER_NAMES).each { String loadBalancerName ->
      loadBalancerKeys << Keys.getLoadBalancerKey("global", accountName, loadBalancerName)
    }
  }

  static void sortWithBootDiskFirst(GoogleServerGroup serverGroup) {
    // Ensure that the boot disk is listed as the first persistent disk.
    if (serverGroup.launchConfig.instanceTemplate?.properties?.disks) {
      def persistentDisks = serverGroup.launchConfig.instanceTemplate.properties.disks.findAll { it.type == "PERSISTENT" }

      if (persistentDisks && !persistentDisks.first().boot) {
        def sortedDisks = []
        def firstBootDisk = persistentDisks.find { it.boot }

        if (firstBootDisk) {
          sortedDisks << firstBootDisk
        }

        sortedDisks.addAll(serverGroup.launchConfig.instanceTemplate.properties.disks.findAll { !it.boot })
        serverGroup.launchConfig.instanceTemplate.properties.disks = sortedDisks
      }
    }
  }

  /**
   * Set load balancing metadata on the server group from the instance template.
   *
   * @param loadBalancerNames -- Network load balancer names specified by target pools.
   * @param instanceMetadata -- Metadata associated with the instance template.
   * @param serverGroup -- Server groups built from the instance template.
   */
  static void setLoadBalancerMetadataOnInstance(List<String> loadBalancerNames,
                                                Metadata instanceMetadata,
                                                GoogleServerGroup serverGroup,
                                                ObjectMapper objectMapper) {
    if (instanceMetadata) {
      def metadataMap = Utils.buildMapFromMetadata(instanceMetadata)
      def regionalLBNameList = metadataMap?.get(REGIONAL_LOAD_BALANCER_NAMES)?.split(",")
      def globalLBNameList = metadataMap?.get(GLOBAL_LOAD_BALANCER_NAMES)?.split(",")
      def backendServiceList = metadataMap?.get(BACKEND_SERVICE_NAMES)?.split(",")
      def policyJson = metadataMap?.get(LOAD_BALANCING_POLICY)

      if (globalLBNameList) {
        serverGroup.asg.put(GLOBAL_LOAD_BALANCER_NAMES, globalLBNameList)
      }
      if (backendServiceList) {
        serverGroup.asg.put(BACKEND_SERVICE_NAMES, backendServiceList)
      }
      if (policyJson) {
        serverGroup.asg.put(LOAD_BALANCING_POLICY, objectMapper.readValue(policyJson, GoogleHttpLoadBalancingPolicy))
      }

      if (regionalLBNameList) {
        serverGroup.asg.put(REGIONAL_LOAD_BALANCER_NAMES, regionalLBNameList)

        // The isDisabled property of a server group is set based on whether there are associated target pools,
        // and whether the metadata of the server group contains a list of load balancers to actually associate
        // the server group with.
        // We set the disabled state for L4 lBs here (before writing into the cache) and calculate
        // the L7 disabled state when we read the server groups from the cache.
        serverGroup.setDisabled(loadBalancerNames.empty)
      }
    }
  }

  static void extractBuildInfo(String imageDescription, GoogleServerGroup googleServerGroup) {
    if (imageDescription) {
      def descriptionTokens = imageDescription?.tokenize(",")
      def appVersionTag = findTagValue(descriptionTokens, "appversion")
      Map buildInfo = null

      if (appVersionTag) {
        def appVersion = AppVersion.parseName(appVersionTag)

        if (appVersion) {
          buildInfo = [package_name: appVersion.packageName, version: appVersion.version, commit: appVersion.commit] as Map<Object, Object>

          if (appVersion.buildJobName) {
            buildInfo.jenkins = [name: appVersion.buildJobName, number: appVersion.buildNumber]
          }

          def buildHostTag = findTagValue(descriptionTokens, "build_host")

          if (buildHostTag && buildInfo.containsKey("jenkins")) {
            ((Map)buildInfo.jenkins).host = buildHostTag
          }

          def buildInfoUrlTag = findTagValue(descriptionTokens, "build_info_url")

          if (buildInfoUrlTag) {
            buildInfo.buildInfoUrl = buildInfoUrlTag
          }
        }

        if (buildInfo) {
          googleServerGroup.buildInfo = buildInfo
        }
      }
    }
  }

  static String findTagValue(List<String> descriptionTokens, String tagKey) {
    def matchingKeyValuePair = descriptionTokens?.find { keyValuePair ->
      keyValuePair.trim().startsWith("$tagKey: ")
    }

    matchingKeyValuePair ? matchingKeyValuePair.trim().substring(tagKey.length() + 2) : null
  }

  class AutoscalerSingletonCallback<Autoscaler> extends JsonBatchCallback<Autoscaler> {

    GoogleServerGroup serverGroup

    @Override
    void onFailure(GoogleJsonError e, HttpHeaders responseHeaders) throws IOException {
      // 404 is thrown if the autoscaler does not exist in the given zone. Any other exception needs to be propagated.
      if (e.code != 404) {
        def errorJson = new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(e)
        log.error errorJson
      }
    }

    @Override
    void onSuccess(Autoscaler autoscaler, HttpHeaders responseHeaders) throws IOException {
      serverGroup.autoscalingPolicy = autoscaler.getAutoscalingPolicy()
      serverGroup.asg.minSize = serverGroup.autoscalingPolicy.minNumReplicas
      serverGroup.asg.maxSize = serverGroup.autoscalingPolicy.maxNumReplicas

      List<AutoscalerStatusDetails> statusDetails = autoscaler.statusDetails

      if (statusDetails) {
        serverGroup.autoscalingMessages = statusDetails.collect { it.message }
      }
    }
  }

  class AutoscalerAggregatedListCallback<AutoscalerAggregatedList> extends JsonBatchCallback<AutoscalerAggregatedList> implements FailureLogger {

    List<GoogleServerGroup> serverGroups

    @Override
    void onSuccess(AutoscalerAggregatedList autoscalerAggregatedList, HttpHeaders responseHeaders) throws IOException {
      autoscalerAggregatedList?.items?.each { String location, AutoscalersScopedList autoscalersScopedList ->
        if (location.startsWith("zones/")) {
          def localZoneName = Utils.getLocalName(location)
          def region = localZoneName.substring(0, localZoneName.lastIndexOf('-'))

          autoscalersScopedList.autoscalers.each { Autoscaler autoscaler ->
            def migName = Utils.getLocalName(autoscaler.target as String)
            def serverGroup = serverGroups.find {
              it.name == migName && it.region == region
            }

            if (serverGroup) {
              serverGroup.autoscalingPolicy = autoscaler.getAutoscalingPolicy()
              serverGroup.asg.minSize = serverGroup.autoscalingPolicy.minNumReplicas
              serverGroup.asg.maxSize = serverGroup.autoscalingPolicy.maxNumReplicas

              List<AutoscalerStatusDetails> statusDetails = autoscaler.statusDetails

              if (statusDetails) {
                serverGroup.autoscalingMessages = statusDetails.collect { it.message }
              }
            }
          }
        }
      }
    }
  }
}
