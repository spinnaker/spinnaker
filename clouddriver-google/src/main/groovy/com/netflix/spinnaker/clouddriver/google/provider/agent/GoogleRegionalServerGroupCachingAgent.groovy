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
import com.google.api.client.googleapis.batch.BatchRequest
import com.google.api.client.googleapis.batch.json.JsonBatchCallback
import com.google.api.client.googleapis.json.GoogleJsonError
import com.google.api.client.http.HttpHeaders
import com.google.api.services.compute.model.*
import com.netflix.frigga.Names
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.agent.DefaultCacheResult
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent
import com.netflix.spinnaker.clouddriver.cache.OnDemandMetricsSupport
import com.netflix.spinnaker.clouddriver.google.ComputeVersion
import com.netflix.spinnaker.clouddriver.google.GoogleCloudProvider
import com.netflix.spinnaker.clouddriver.google.cache.CacheResultBuilder
import com.netflix.spinnaker.clouddriver.google.cache.Keys
import com.netflix.spinnaker.clouddriver.google.model.GoogleInstance
import com.netflix.spinnaker.clouddriver.google.model.GoogleServerGroup
import com.netflix.spinnaker.clouddriver.google.model.callbacks.Utils
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import groovy.transform.Canonical
import groovy.util.logging.Slf4j

import java.util.concurrent.TimeUnit

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.INFORMATIVE
import static com.netflix.spinnaker.clouddriver.google.cache.Keys.Namespace.*

@Slf4j
class GoogleRegionalServerGroupCachingAgent extends AbstractGoogleCachingAgent implements OnDemandAgent {

  final String region

  final Set<AgentDataType> providedDataTypes = [
    AUTHORITATIVE.forType(SERVER_GROUPS.ns),
    INFORMATIVE.forType(APPLICATIONS.ns),
    INFORMATIVE.forType(CLUSTERS.ns),
    INFORMATIVE.forType(INSTANCES.ns),
    INFORMATIVE.forType(LOAD_BALANCERS.ns),
  ] as Set

  String agentType = "${accountName}/${region}/${GoogleRegionalServerGroupCachingAgent.simpleName}"
  String onDemandAgentType = "${agentType}-OnDemand"
  final OnDemandMetricsSupport metricsSupport

  GoogleRegionalServerGroupCachingAgent(String googleApplicationName,
                                        GoogleNamedAccountCredentials credentials,
                                        ObjectMapper objectMapper,
                                        String region,
                                        Registry registry) {
    super(googleApplicationName,
          credentials,
          objectMapper)
    this.region = region
    this.metricsSupport = new OnDemandMetricsSupport(
      registry,
      this,
      "${GoogleCloudProvider.GCE}:${OnDemandAgent.OnDemandType.ServerGroup}")
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    if (credentials.computeVersion != ComputeVersion.ALPHA) {
      return new DefaultCacheResult([:])
    }

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
    Set<String> zoneNames = credentials.getZonesFromRegion(region).collect { Utils.getLocalName(it) } as Set

    // The RMIG will deploy to the last 3 zones (after sorting by zone name).
    if (zoneNames.size() > 3) {
      zoneNames = zoneNames.sort().drop(zoneNames.size() - 3)
    }

    List<GoogleServerGroup> serverGroups = []

    BatchRequest igmRequest = buildBatchRequest()
    BatchRequest instanceGroupsRequest = buildBatchRequest()
    BatchRequest autoscalerRequest = buildBatchRequest()

    InstanceGroupManagerCallbacks instanceGroupManagerCallbacks = new InstanceGroupManagerCallbacks(
      providerCache: providerCache,
      serverGroups: serverGroups,
      region: region,
      zoneNames: zoneNames,
      instanceGroupsRequest: instanceGroupsRequest,
      autoscalerRequest: autoscalerRequest)
    if (onDemandServerGroupName) {
      InstanceGroupManagerCallbacks.InstanceGroupManagerSingletonCallback igmCallback =
        instanceGroupManagerCallbacks.newInstanceGroupManagerSingletonCallback()
      compute.regionInstanceGroupManagers().get(project, region, onDemandServerGroupName).queue(igmRequest, igmCallback)
    } else {
      InstanceGroupManagerCallbacks.InstanceGroupManagerListCallback igmlCallback =
        instanceGroupManagerCallbacks.newInstanceGroupManagerListCallback()
      compute.regionInstanceGroupManagers().list(project, region).queue(igmRequest, igmlCallback)
    }
    executeIfRequestsAreQueued(igmRequest)
    executeIfRequestsAreQueued(instanceGroupsRequest)
    executeIfRequestsAreQueued(autoscalerRequest)

    serverGroups
  }

  @Override
  boolean handles(OnDemandAgent.OnDemandType type, String cloudProvider) {
    type == OnDemandAgent.OnDemandType.ServerGroup && cloudProvider == GoogleCloudProvider.GCE
  }

  @Override
  OnDemandAgent.OnDemandResult handle(ProviderCache providerCache, Map<String, ? extends Object> data) {
    if (credentials.computeVersion != ComputeVersion.ALPHA) {
      return null
    }

    if (!data.containsKey("serverGroupName") || data.account != accountName || data.region != region) {
      return null
    }

    GoogleServerGroup serverGroup = metricsSupport.readData {
      getServerGroup(providerCache, data.serverGroupName as String)
    }

    if (serverGroup && !serverGroup.regional) {
      return null
    }

    def cacheResultBuilder = new CacheResultBuilder(startTime: Long.MAX_VALUE)
    CacheResult result = metricsSupport.transformData {
      buildCacheResult(cacheResultBuilder, serverGroup ? [serverGroup] : [])
    }

    def serverGroupKey = Keys.getServerGroupKey(data.serverGroupName as String, accountName, region)

    if (result.cacheResults.values().flatten().empty) {
      // Avoid writing an empty onDemand cache record (instead delete any that may have previously existed).
      providerCache.evictDeletedItems(ON_DEMAND.ns, [serverGroupKey])
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
      evictions[SERVER_GROUPS.ns].add(serverGroupKey)
    }

    log.info("On demand cache refresh succeeded. Data: ${data}")

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
      parsedKey && parsedKey.account == accountName && parsedKey.region == region && !parsedKey.zone
    }

    def keys = providerCache.getIdentifiers(ON_DEMAND.ns).findAll { String key ->
      keyOwnedByThisAgent(Keys.parse(key))
    }

    providerCache.getAll(ON_DEMAND.ns, keys).collect { CacheData cacheData ->
      [
          details       : Keys.parse(cacheData.id),
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

      def instanceKeys = []
      def loadBalancerKeys = []

      cacheResultBuilder.namespace(APPLICATIONS.ns).keep(appKey).with {
        attributes.name = applicationName
        relationships[CLUSTERS.ns].add(clusterKey)
      }

      cacheResultBuilder.namespace(CLUSTERS.ns).keep(clusterKey).with {
        attributes.name = clusterName
        attributes.accountName = accountName
        relationships[APPLICATIONS.ns].add(appKey)
        relationships[SERVER_GROUPS.ns].add(serverGroupKey)
      }

      serverGroup.instances.each { GoogleInstance partialInstance ->
        def instanceKey = Keys.getInstanceKey(accountName, serverGroup.region, partialInstance.name)
        instanceKeys << instanceKey
        cacheResultBuilder.namespace(INSTANCES.ns).keep(instanceKey).relationships[SERVER_GROUPS.ns].add(serverGroupKey)
      }
      serverGroup.instances.clear()

      serverGroup.asg.loadBalancerNames.each { String loadBalancerName ->
        loadBalancerKeys << Keys.getLoadBalancerKey(region,
                                                    accountName,
                                                    loadBalancerName)
      }

      loadBalancerKeys.each { String loadBalancerKey ->
        cacheResultBuilder.namespace(LOAD_BALANCERS.ns).keep(loadBalancerKey).with {
          relationships[SERVER_GROUPS.ns].add(serverGroupKey)
        }
      }

      if (GoogleZonalServerGroupCachingAgent.shouldUseOnDemandData(cacheResultBuilder, serverGroupKey)) {
        moveOnDemandDataToNamespace(cacheResultBuilder, serverGroup)
      } else {
        cacheResultBuilder.namespace(SERVER_GROUPS.ns).keep(serverGroupKey).with {
          attributes = objectMapper.convertValue(serverGroup, ATTRIBUTES)
          relationships[APPLICATIONS.ns].add(appKey)
          relationships[CLUSTERS.ns].add(clusterKey)
          relationships[INSTANCES.ns].addAll(instanceKeys)
          relationships[LOAD_BALANCERS.ns].addAll(loadBalancerKeys)
        }
      }
    }

    log.info("Caching ${cacheResultBuilder.namespace(APPLICATIONS.ns).keepSize()} applications in ${agentType}")
    log.info("Caching ${cacheResultBuilder.namespace(CLUSTERS.ns).keepSize()} clusters in ${agentType}")
    log.info("Caching ${cacheResultBuilder.namespace(SERVER_GROUPS.ns).keepSize()} server groups in ${agentType}")
    log.info("Caching ${cacheResultBuilder.namespace(INSTANCES.ns).keepSize()} instance relationships in ${agentType}")
    log.info("Caching ${cacheResultBuilder.namespace(LOAD_BALANCERS.ns).keepSize()} load balancer relationships in ${agentType}")
    log.info("Caching ${cacheResultBuilder.onDemand.toKeep.size()} onDemand entries in ${agentType}")
    log.info("Evicting ${cacheResultBuilder.onDemand.toEvict.size()} onDemand entries in ${agentType}")

    cacheResultBuilder.build()
  }

  void moveOnDemandDataToNamespace(CacheResultBuilder cacheResultBuilder, GoogleServerGroup googleServerGroup) {
    def serverGroupKey = getServerGroupKey(googleServerGroup)
    Map<String, List<MutableCacheData>> onDemandData = objectMapper.readValue(
        cacheResultBuilder.onDemand.toKeep[serverGroupKey].attributes.cacheResults as String,
        new TypeReference<Map<String, List<MutableCacheData>>>() {})

    onDemandData.each { String namespace, List<MutableCacheData> cacheDatas ->
      cacheDatas.each { MutableCacheData cacheData ->
        cacheResultBuilder.namespace(namespace).keep(cacheData.id).with {
          attributes = cacheData.attributes
          relationships = cacheData.relationships
        }
        cacheResultBuilder.onDemand.toKeep.remove(cacheData.id)
      }
    }
  }

  String getServerGroupKey(GoogleServerGroup googleServerGroup) {
    return Keys.getServerGroupKey(googleServerGroup.name, accountName, region)
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
    String region
    Set<String> zoneNames
    BatchRequest instanceGroupsRequest
    BatchRequest autoscalerRequest

    InstanceGroupManagerSingletonCallback<InstanceGroupManager> newInstanceGroupManagerSingletonCallback() {
      return new InstanceGroupManagerSingletonCallback<InstanceGroupManager>()
    }

    InstanceGroupManagerListCallback<RegionInstanceGroupManagerList> newInstanceGroupManagerListCallback() {
      return new InstanceGroupManagerListCallback<RegionInstanceGroupManagerList>()
    }

    class InstanceGroupManagerSingletonCallback<InstanceGroupManager> extends JsonBatchCallback<InstanceGroupManager> {

      @Override
      void onFailure(GoogleJsonError e, HttpHeaders responseHeaders) throws IOException {
        // 404 is thrown if the managed instance group does not exist in the given region. Any other exception needs to be propagated.
        if (e.code != 404) {
          def errorJson = new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(e)
          log.error errorJson
        }
      }

      @Override
      void onSuccess(InstanceGroupManager instanceGroupManager, HttpHeaders responseHeaders) throws IOException {
        if (Names.parseName(instanceGroupManager.name)) {
          GoogleServerGroup serverGroup = buildServerGroupFromInstanceGroupManager(instanceGroupManager)
          serverGroups << serverGroup

          populateInstancesAndTemplate(providerCache, instanceGroupManager, serverGroup)

          def autoscalerCallback = new AutoscalerSingletonCallback(serverGroup: serverGroup)
          compute.regionAutoscalers().get(project, region, serverGroup.name).queue(autoscalerRequest, autoscalerCallback)
        }
      }
    }

    class InstanceGroupManagerListCallback<RegionInstanceGroupManagerList> extends JsonBatchCallback<RegionInstanceGroupManagerList> implements FailureLogger {

      @Override
      void onSuccess(RegionInstanceGroupManagerList instanceGroupManagerList, HttpHeaders responseHeaders) throws IOException {
        instanceGroupManagerList?.items?.each { InstanceGroupManager instanceGroupManager ->
          if (Names.parseName(instanceGroupManager.name)) {
            GoogleServerGroup serverGroup = buildServerGroupFromInstanceGroupManager(instanceGroupManager)
            serverGroups << serverGroup

            populateInstancesAndTemplate(providerCache, instanceGroupManager, serverGroup)
          }
        }

        def autoscalerCallback = new AutoscalerAggregatedListCallback(serverGroups: serverGroups)
        compute.autoscalers().aggregatedList(project).queue(autoscalerRequest, autoscalerCallback)
      }
    }

    GoogleServerGroup buildServerGroupFromInstanceGroupManager(InstanceGroupManager instanceGroupManager) {
      return new GoogleServerGroup(
          name: instanceGroupManager.name,
          regional: true,
          region: region,
          zones: zoneNames,
          currentActions: instanceGroupManager.currentActions,
          launchConfig: [createdTime: Utils.getTimeFromTimestamp(instanceGroupManager.creationTimestamp)],
          asg: [minSize        : instanceGroupManager.targetSize,
                maxSize        : instanceGroupManager.targetSize,
                desiredCapacity: instanceGroupManager.targetSize]
      )
    }

    void populateInstancesAndTemplate(ProviderCache providerCache, InstanceGroupManager instanceGroupManager, GoogleServerGroup serverGroup) {
      InstanceGroupsCallback instanceGroupsCallback = new InstanceGroupsCallback(serverGroup: serverGroup)
      compute.regionInstanceGroups().listInstances(project,
                                                   region,
                                                   serverGroup.name,
                                                   new RegionInstanceGroupsListInstancesRequest()).queue(instanceGroupsRequest,
                                                                                                         instanceGroupsCallback)

      String instanceTemplateName = Utils.getLocalName(instanceGroupManager.instanceTemplate)
      List<String> loadBalancerNames =
        Utils.deriveNetworkLoadBalancerNamesFromTargetPoolUrls(instanceGroupManager.getTargetPools())
      InstanceTemplatesCallback instanceTemplatesCallback = new InstanceTemplatesCallback(providerCache: providerCache,
                                                                                          serverGroup: serverGroup,
                                                                                          loadBalancerNames: loadBalancerNames)
      compute.instanceTemplates().get(project, instanceTemplateName).queue(instanceGroupsRequest,
                                                                           instanceTemplatesCallback)
    }
  }

  class InstanceGroupsCallback<RegionInstanceGroupsListInstances> extends JsonBatchCallback<RegionInstanceGroupsListInstances> implements FailureLogger {

    GoogleServerGroup serverGroup

    @Override
    void onSuccess(RegionInstanceGroupsListInstances instanceGroupsListInstances, HttpHeaders responseHeaders) throws IOException {
      instanceGroupsListInstances?.items?.each { InstanceWithNamedPorts instance ->
        serverGroup.instances << new GoogleInstance(name: Utils.getLocalName(instance.instance as String))
      }
    }
  }

  class InstanceTemplatesCallback<InstanceTemplate> extends JsonBatchCallback<InstanceTemplate> implements FailureLogger {

    private static final String LOAD_BALANCER_NAMES = "load-balancer-names"

    ProviderCache providerCache
    GoogleServerGroup serverGroup
    List<String> loadBalancerNames

    @Override
    void onSuccess(InstanceTemplate instanceTemplate, HttpHeaders responseHeaders) throws IOException {
      serverGroup.with {
        networkName = Utils.getNetworkNameFromInstanceTemplate(instanceTemplate)
        instanceTemplateTags = instanceTemplate?.properties?.tags?.items
        launchConfig.with {
          launchConfigurationName = instanceTemplate?.name
          instanceType = instanceTemplate?.properties?.machineType
        }
      }
      // "instanceTemplate = instanceTemplate" in the above ".with{ }" blocks doesn't work because Groovy thinks it's
      // assigning the same variable to itself, instead of to the "launchConfig" entry
      serverGroup.launchConfig.instanceTemplate = instanceTemplate

      def sourceImageUrl = instanceTemplate?.properties?.disks?.find { disk ->
        disk.boot
      }?.initializeParams?.sourceImage
      if (sourceImageUrl) {
        serverGroup.launchConfig.imageId = Utils.getLocalName(sourceImageUrl)

        def imageKey = Keys.getImageKey(accountName, serverGroup.launchConfig.imageId)
        def image = providerCache.get(IMAGES.ns, imageKey)

        GoogleZonalServerGroupCachingAgent.extractBuildInfo(image?.attributes?.image?.description, serverGroup)
      }

      def instanceMetadata = instanceTemplate?.properties?.metadata
      if (instanceMetadata) {
        def metadataMap = Utils.buildMapFromMetadata(instanceMetadata)
        def loadBalancerNameList = metadataMap?.get(LOAD_BALANCER_NAMES)?.split(",")
        if (loadBalancerNameList) {
          serverGroup.asg.loadBalancerNames = loadBalancerNameList

          // The isDisabled property of a server group is set based on whether there are associated target pools,
          // and whether the metadata of the server group contains a list of load balancers to actually associate
          // the server group with.
          serverGroup.setDisabled(loadBalancerNames.empty)
        }
      }
    }
  }

  class AutoscalerSingletonCallback<Autoscaler> extends JsonBatchCallback<Autoscaler> {

    GoogleServerGroup serverGroup

    @Override
    void onFailure(GoogleJsonError e, HttpHeaders responseHeaders) throws IOException {
      // 404 is thrown if the autoscaler does not exist in the given region. Any other exception needs to be propagated.
      if (e.code != 404) {
        def errorJson = new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(e)
        log.error errorJson
      }
    }

    @Override
    void onSuccess(Autoscaler autoscaler, HttpHeaders responseHeaders) throws IOException {
      serverGroup.autoscalingPolicy = autoscaler.getAutoscalingPolicy()
    }
  }

  class AutoscalerAggregatedListCallback<AutoscalerAggregatedList> extends JsonBatchCallback<AutoscalerAggregatedList> implements FailureLogger {

    List<GoogleServerGroup> serverGroups

    @Override
    void onSuccess(AutoscalerAggregatedList autoscalerAggregatedList, HttpHeaders responseHeaders) throws IOException {
      autoscalerAggregatedList?.items?.each { String location, AutoscalersScopedList autoscalersScopedList ->
        if (location.startsWith("regions/")) {
          def region = Utils.getLocalName(location)

          autoscalersScopedList.autoscalers.each { Autoscaler autoscaler ->
            def migName = Utils.getLocalName(autoscaler.target as String)
            def serverGroup = serverGroups.find {
              it.name == migName && it.region == region
            }

            if (serverGroup) {
              serverGroup.autoscalingPolicy = autoscaler.getAutoscalingPolicy()
            }
          }
        }
      }
    }
  }
}
