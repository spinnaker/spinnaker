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
import com.google.api.services.compute.ComputeRequest
import com.google.api.services.compute.model.*
import com.netflix.frigga.Names
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
import com.netflix.spinnaker.clouddriver.google.batch.GoogleBatchRequest
import com.netflix.spinnaker.clouddriver.google.cache.CacheResultBuilder
import com.netflix.spinnaker.clouddriver.google.cache.Keys
import com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil
import com.netflix.spinnaker.clouddriver.google.model.GoogleDistributionPolicy
import com.netflix.spinnaker.clouddriver.google.model.GoogleInstance
import com.netflix.spinnaker.clouddriver.google.model.GoogleServerGroup
import com.netflix.spinnaker.clouddriver.google.model.callbacks.Utils
import com.netflix.spinnaker.clouddriver.google.provider.agent.util.PaginatedRequest
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import com.netflix.spinnaker.moniker.Moniker
import groovy.transform.Canonical
import groovy.util.logging.Slf4j

import java.util.concurrent.TimeUnit

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.INFORMATIVE
import static com.netflix.spinnaker.clouddriver.google.cache.Keys.Namespace.*

@Slf4j
class GoogleRegionalServerGroupCachingAgent extends AbstractGoogleCachingAgent implements OnDemandAgent, GoogleExecutorTraits {
  final String region
  final long maxMIGPageSize

  final Set<AgentDataType> providedDataTypes = [
    AUTHORITATIVE.forType(SERVER_GROUPS.ns),
    AUTHORITATIVE.forType(APPLICATIONS.ns),
    INFORMATIVE.forType(CLUSTERS.ns),
    INFORMATIVE.forType(LOAD_BALANCERS.ns),
  ] as Set

  String agentType = "${accountName}/${region}/${GoogleRegionalServerGroupCachingAgent.simpleName}"
  String onDemandAgentType = "${agentType}-OnDemand"
  final OnDemandMetricsSupport metricsSupport

  GoogleRegionalServerGroupCachingAgent(String clouddriverUserAgentApplicationName,
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
    GoogleRegionalServerGroupCachingAgent cachingAgent = this
    List<GoogleServerGroup> serverGroups = []

    GoogleBatchRequest igmRequest = buildGoogleBatchRequest()
    GoogleBatchRequest instanceGroupsRequest = buildGoogleBatchRequest()
    GoogleBatchRequest autoscalerRequest = buildGoogleBatchRequest()

    List<InstanceTemplate> instanceTemplates = GoogleZonalServerGroupCachingAgent.fetchInstanceTemplates(cachingAgent, compute, project)
    List<GoogleInstance> instances = GCEUtil.fetchInstances(this, credentials)

    InstanceGroupManagerCallbacks instanceGroupManagerCallbacks = new InstanceGroupManagerCallbacks(
      providerCache: providerCache,
      serverGroups: serverGroups,
      region: region,
      instanceGroupsRequest: instanceGroupsRequest,
      autoscalerRequest: autoscalerRequest,
      instances: instances
    )
    if (onDemandServerGroupName) {
      InstanceGroupManagerCallbacks.InstanceGroupManagerSingletonCallback igmCallback =
        instanceGroupManagerCallbacks.newInstanceGroupManagerSingletonCallback(instanceTemplates, instances)
      igmRequest.queue(compute.regionInstanceGroupManagers().get(project, region, onDemandServerGroupName), igmCallback)
    } else {
      InstanceGroupManagerCallbacks.InstanceGroupManagerListCallback igmlCallback =
        instanceGroupManagerCallbacks.newInstanceGroupManagerListCallback(instanceTemplates, instances)
      new PaginatedRequest<RegionInstanceGroupManagerList>(this) {
        @Override
        ComputeRequest<RegionInstanceGroupManagerList> request (String pageToken) {
          return compute.regionInstanceGroupManagers().list(project, region).setMaxResults(maxMIGPageSize).setPageToken(pageToken)
        }

        @Override
        String getNextPageToken(RegionInstanceGroupManagerList instanceGroupManagerList) {
          return instanceGroupManagerList.getNextPageToken()
        }
      }.queue(igmRequest, igmlCallback, "RegionalServerGroupCaching.igm")
    }
    executeIfRequestsAreQueued(igmRequest, "RegionalServerGroupCaching.igm")
    executeIfRequestsAreQueued(instanceGroupsRequest, "RegionalServerGroupCaching.instanceGroups")
    executeIfRequestsAreQueued(autoscalerRequest, "RegionalServerGroupCaching.autoscaler")

    serverGroups
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

    if (serverGroup && !serverGroup.regional) {
      return null
    }

    def cacheResultBuilder = new CacheResultBuilder(startTime: Long.MAX_VALUE)
    CacheResult result = metricsSupport.transformData {
      buildCacheResult(cacheResultBuilder, serverGroup ? [serverGroup] : [])
    }

    def serverGroupKey = Keys.getServerGroupKey(data.serverGroupName as String, serverGroup?.view?.moniker?.cluster, accountName, region)

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

    log.debug("On demand cache refresh succeeded. Data: ${data}. Added ${serverGroup ? 1 : 0} items to the cache. Evicted ${evictions[SERVER_GROUPS.ns]}.")

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
      def details = Keys.parse(cacheData.id)

      return [
          details       : details,
          moniker       : cacheData.attributes.moniker,
          cacheTime     : cacheData.attributes.cacheTime,
          processedCount: cacheData.attributes.processedCount,
          processedTime : cacheData.attributes.processedTime
      ]
    }

  }

  private CacheResult buildCacheResult(CacheResultBuilder cacheResultBuilder, List<GoogleServerGroup> serverGroups) {
    log.debug "Describing items in $agentType"

    serverGroups.each { GoogleServerGroup serverGroup ->
      Moniker moniker = naming.deriveMoniker(serverGroup)
      def applicationName = moniker.app
      def clusterName = moniker.cluster
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
        attributes.moniker = moniker
        relationships[APPLICATIONS.ns].add(appKey)
        relationships[SERVER_GROUPS.ns].add(serverGroupKey)
        relationships[INSTANCES.ns].addAll(instanceKeys)
      }
      log.debug("Writing cache entry for cluster key ${clusterKey} adding relationships for application ${appKey} and server group ${serverGroupKey}")

      GoogleZonalServerGroupCachingAgent.populateLoadBalancerKeys(serverGroup, loadBalancerKeys, accountName, region)

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
          relationships[LOAD_BALANCERS.ns].addAll(loadBalancerKeys)
          relationships[INSTANCES.ns].addAll(instanceKeys)
        }
      }
    }

    log.debug("Caching ${cacheResultBuilder.namespace(APPLICATIONS.ns).keepSize()} applications in ${agentType}")
    log.debug("Caching ${cacheResultBuilder.namespace(CLUSTERS.ns).keepSize()} clusters in ${agentType}")
    log.debug("Caching ${cacheResultBuilder.namespace(SERVER_GROUPS.ns).keepSize()} server groups in ${agentType}")
    log.debug("Caching ${cacheResultBuilder.namespace(LOAD_BALANCERS.ns).keepSize()} load balancer relationships in ${agentType}")
    log.debug("Caching ${cacheResultBuilder.onDemand.toKeep.size()} onDemand entries in ${agentType}")
    log.debug("Evicting ${cacheResultBuilder.onDemand.toEvict.size()} onDemand entries in ${agentType}")

    cacheResultBuilder.build()
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
    def moniker = googleServerGroup.view.moniker
    return Keys.getServerGroupKey(googleServerGroup.name, moniker.cluster, accountName, region)
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
    GoogleBatchRequest instanceGroupsRequest
    GoogleBatchRequest autoscalerRequest
    List<GoogleInstance> instances

    InstanceGroupManagerSingletonCallback<InstanceGroupManager> newInstanceGroupManagerSingletonCallback(List<InstanceTemplate> instanceTemplates, List<GoogleInstance> instances) {
      return new InstanceGroupManagerSingletonCallback<InstanceGroupManager>(instanceTemplates: instanceTemplates, instances: instances)
    }

    InstanceGroupManagerListCallback<RegionInstanceGroupManagerList> newInstanceGroupManagerListCallback(List<InstanceTemplate> instanceTemplates, List<GoogleInstance> instances) {
      return new InstanceGroupManagerListCallback<RegionInstanceGroupManagerList>(instanceTemplates: instanceTemplates, instances: instances)
    }

    class InstanceGroupManagerSingletonCallback<InstanceGroupManager> extends JsonBatchCallback<InstanceGroupManager> {

      List<InstanceTemplate> instanceTemplates
      List<GoogleInstance> instances

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
          GoogleServerGroup serverGroup = buildServerGroupFromInstanceGroupManager(instanceGroupManager, instances)
          serverGroups << serverGroup

          populateInstanceTemplate(providerCache, instanceGroupManager, serverGroup, instanceTemplates)

          def autoscalerCallback = new AutoscalerSingletonCallback(serverGroup: serverGroup)
          autoscalerRequest.queue(compute.regionAutoscalers().get(project, region, serverGroup.name), autoscalerCallback)
        }
      }
    }

    class InstanceGroupManagerListCallback<RegionInstanceGroupManagerList> extends JsonBatchCallback<RegionInstanceGroupManagerList> implements FailureLogger {

      List<InstanceTemplate> instanceTemplates
      List<GoogleInstance> instances

      @Override
      void onSuccess(RegionInstanceGroupManagerList instanceGroupManagerList, HttpHeaders responseHeaders) throws IOException {
        instanceGroupManagerList?.items?.each { InstanceGroupManager instanceGroupManager ->
          if (Names.parseName(instanceGroupManager.name)) {
            GoogleServerGroup serverGroup = buildServerGroupFromInstanceGroupManager(instanceGroupManager, instances)
            serverGroups << serverGroup

            populateInstanceTemplate(providerCache, instanceGroupManager, serverGroup, instanceTemplates)
          }
        }

        def autoscalerCallback = new AutoscalerAggregatedListCallback(serverGroups: serverGroups)
        buildAutoscalerListRequest().queue(autoscalerRequest, autoscalerCallback,
          'GoogleRegionalServerGroupCachingAgent.autoscalerAggregatedList')
      }
    }

    GoogleServerGroup buildServerGroupFromInstanceGroupManager(InstanceGroupManager instanceGroupManager,
                                                               List<GoogleInstance> instances) {

      DistributionPolicy distributionPolicy = instanceGroupManager?.getDistributionPolicy()
      // The distribution policy zones are URLs.
      List<String> zones = distributionPolicy?.getZones()?.collect { Utils.getLocalName(it.getZone()) }

      List<GoogleInstance> groupInstances = instances.findAll { it.getName().startsWith(instanceGroupManager.getBaseInstanceName()) && it.getRegion() == region }

      Map<String, Integer> namedPorts = [:]
      instanceGroupManager.namedPorts.each { namedPorts[(it.name)] = it.port }
      return new GoogleServerGroup(
          name: instanceGroupManager.name,
          account: accountName,
          instances: groupInstances,
          regional: true,
          region: region,
          namedPorts: namedPorts,
          zones: zones,
          distributionPolicy: zones ? new GoogleDistributionPolicy(zones: zones) : null,
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
      GoogleZonalServerGroupCachingAgent.populateServerGroupWithTemplate(serverGroup, providerCache, loadBalancerNames,
          template, accountName, project, objectMapper)
      def instanceMetadata = template?.properties?.metadata
      if (instanceMetadata) {
        def metadataMap = Utils.buildMapFromMetadata(instanceMetadata)
        serverGroup.selectZones = metadataMap?.get(GCEUtil.SELECT_ZONES) ?: false
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
        if (location.startsWith("regions/")) {
          def region = Utils.getLocalName(location)

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

  PaginatedRequest<AutoscalerAggregatedList> buildAutoscalerListRequest() {
    return new PaginatedRequest<AutoscalerAggregatedList>(this) {
      @Override
      protected String getNextPageToken(AutoscalerAggregatedList autoscalerAggregatedList) {
        return autoscalerAggregatedList.getNextPageToken()
      }

      @Override
      protected ComputeRequest<AutoscalerAggregatedList> request(String pageToken) {
        return compute.autoscalers().aggregatedList(project).setPageToken(pageToken)
      }
    }
  }
}
