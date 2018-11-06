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

package com.netflix.spinnaker.clouddriver.appengine.provider.agent

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.api.client.googleapis.batch.BatchRequest
import com.google.api.client.googleapis.json.GoogleJsonError
import com.google.api.client.http.HttpHeaders
import com.google.api.services.appengine.v1.model.*
import com.netflix.frigga.Names
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.agent.DefaultCacheResult
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.appengine.AppengineCloudProvider
import com.netflix.spinnaker.clouddriver.appengine.cache.Keys
import com.netflix.spinnaker.clouddriver.appengine.model.AppengineInstance
import com.netflix.spinnaker.clouddriver.appengine.model.AppengineLoadBalancer
import com.netflix.spinnaker.clouddriver.appengine.model.AppengineServerGroup
import com.netflix.spinnaker.clouddriver.appengine.provider.callbacks.AppengineCallback
import com.netflix.spinnaker.clouddriver.appengine.security.AppengineNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent
import com.netflix.spinnaker.clouddriver.cache.OnDemandMetricsSupport
import com.netflix.spinnaker.clouddriver.kubernetes.v1.provider.view.MutableCacheData
import groovy.util.logging.Slf4j

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.INFORMATIVE
import static com.netflix.spinnaker.clouddriver.appengine.cache.Keys.Namespace.*

@Slf4j
class AppengineServerGroupCachingAgent extends AbstractAppengineCachingAgent implements OnDemandAgent {
  final String category = "serverGroup"

  final OnDemandMetricsSupport metricsSupport

  static final Set<AgentDataType> types = Collections.unmodifiableSet([
    AUTHORITATIVE.forType(APPLICATIONS.ns),
    AUTHORITATIVE.forType(CLUSTERS.ns),
    AUTHORITATIVE.forType(SERVER_GROUPS.ns),
    AUTHORITATIVE.forType(INSTANCES.ns),
    INFORMATIVE.forType(LOAD_BALANCERS.ns),
  ] as Set)

  String agentType = "${accountName}/${AppengineServerGroupCachingAgent.simpleName}"

  AppengineServerGroupCachingAgent(String accountName,
                                   AppengineNamedAccountCredentials credentials,
                                   ObjectMapper objectMapper,
                                   Registry registry) {
    super(accountName, objectMapper, credentials)
    this.metricsSupport = new OnDemandMetricsSupport(
      registry,
      this,
      "$AppengineCloudProvider.ID:$OnDemandAgent.OnDemandType.ServerGroup")
  }

  @Override
  String getSimpleName() {
    AppengineServerGroupCachingAgent.simpleName
  }

  @Override
  Collection<AgentDataType> getProvidedDataTypes() {
    types
  }

  @Override
  String getOnDemandAgentType() {
    "${getAgentType()}-OnDemand"
  }

  @Override
  boolean handles(OnDemandAgent.OnDemandType type, String cloudProvider) {
    type == OnDemandAgent.OnDemandType.ServerGroup && cloudProvider == AppengineCloudProvider.ID
  }

  @Override
  OnDemandAgent.OnDemandResult handle(ProviderCache providerCache, Map<String, ? extends Object> data) {
    if (!data.containsKey("serverGroupName") || data.account != accountName) {
      return null
    }

    def serverGroupName = data.serverGroupName.toString()
    def matchingServerGroupAndLoadBalancer = metricsSupport.readData {
      loadServerGroupAndLoadBalancer(serverGroupName)
    }
    Version serverGroup = matchingServerGroupAndLoadBalancer.serverGroup
    Service loadBalancer = matchingServerGroupAndLoadBalancer.loadBalancer
    def serverGroupsByLoadBalancer = loadBalancer ? [(loadBalancer): [serverGroup]] : [:].withDefault { [] }
    Map<Version, List<Instance>> instances = (serverGroup && loadBalancer) ?
      loadInstances(serverGroupsByLoadBalancer) :
      [:].withDefault { [] }

    CacheResult result = metricsSupport.transformData {
      buildCacheResult(
        serverGroupsByLoadBalancer,
        instances,
        [:],
        [],
        Long.MAX_VALUE
      )
    }

    def jsonResult = objectMapper.writeValueAsString(result.cacheResults)
    def serverGroupKey = Keys.getServerGroupKey(accountName, serverGroupName, credentials.region)
    if (result.cacheResults.values().flatten().isEmpty()) {
      providerCache.evictDeletedItems(
        ON_DEMAND.ns,
        [serverGroupKey]
      )
    } else {
      metricsSupport.onDemandStore {
        def cacheData = new DefaultCacheData(
          serverGroupKey,
          10 * 60, // ttl is 10 minutes.
          [
            cacheTime: System.currentTimeMillis(),
            cacheResults: jsonResult,
            processedCount: 0,
            processedTime: null
          ],
          [:]
        )

        providerCache.putCacheData(ON_DEMAND.ns, cacheData)
      }
    }

    Map<String, Collection<String>> evictions = serverGroup ? [:] : [(SERVER_GROUPS.ns): [serverGroupKey]]

    log.info "On demand cache refresh (data: ${data}) succeeded."

    new OnDemandAgent.OnDemandResult(
      sourceAgentType: getOnDemandAgentType(),
      cacheResult: result,
      evictions: evictions
    )
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    Long start = System.currentTimeMillis()
    Map<Service, List<Version>> serverGroupsByLoadBalancer = loadServerGroups()
    Map<Version, List<Instance>> instancesByServerGroup = loadInstances(serverGroupsByLoadBalancer)
    List<CacheData> evictFromOnDemand = []
    List<CacheData> keepInOnDemand = []

    def serverGroupKeys = serverGroupsByLoadBalancer.collectMany([], { Service loadBalancer, List<Version> versions ->
      versions.collect { version -> Keys.getServerGroupKey(accountName, version.getId(), credentials.region) }
    })

    providerCache.getAll(ON_DEMAND.ns, serverGroupKeys).each { CacheData onDemandEntry ->
      if (onDemandEntry.attributes.cacheTime < start && onDemandEntry.attributes.processedCount > 0) {
        evictFromOnDemand << onDemandEntry
      } else {
        keepInOnDemand << onDemandEntry
      }
    }

    def onDemandMap = keepInOnDemand.collectEntries { CacheData onDemandEntry -> [(onDemandEntry.id): onDemandEntry] }
    def result = buildCacheResult(serverGroupsByLoadBalancer,
                                  instancesByServerGroup,
                                  onDemandMap,
                                  evictFromOnDemand*.id,
                                  start)

    result.cacheResults[ON_DEMAND.ns].each { CacheData onDemandEntry ->
      onDemandEntry.attributes.processedTime = System.currentTimeMillis()
      onDemandEntry.attributes.processedCount = (onDemandEntry.attributes.processedCount ?: 0) + 1
    }

    result
  }

  CacheResult buildCacheResult(Map<Service, List<Version>> serverGroupsByLoadBalancer,
                               Map<Version, List<Instance>> instancesByServerGroup,
                               Map<String, CacheData> onDemandKeep,
                               List<String> onDemandEvict,
                               Long start) {
    log.info "Describing items in $agentType"

    Map<String, MutableCacheData> cachedApplications = MutableCacheData.mutableCacheMap()
    Map<String, MutableCacheData> cachedClusters = MutableCacheData.mutableCacheMap()
    Map<String, MutableCacheData> cachedServerGroups = MutableCacheData.mutableCacheMap()
    Map<String, MutableCacheData> cachedLoadBalancers = MutableCacheData.mutableCacheMap()
    Map<String, MutableCacheData> cachedInstances = MutableCacheData.mutableCacheMap()

    serverGroupsByLoadBalancer.each { Service loadBalancer, List<Version> serverGroups ->
      def loadBalancerName = loadBalancer.getId()
      serverGroups.each { Version serverGroup ->
        def onDemandData = onDemandKeep ?
          onDemandKeep[Keys.getServerGroupKey(accountName, serverGroup.getId(), credentials.region)] :
          null

        if (onDemandData && onDemandData.attributes.cacheTime >= start) {
          Map<String, List<CacheData>> cacheResults = objectMapper.readValue(onDemandData.attributes.cacheResults as String,
                                                                             new TypeReference<Map<String, List<MutableCacheData>>>() {})
          cache(cacheResults, APPLICATIONS.ns, cachedApplications)
          cache(cacheResults, CLUSTERS.ns, cachedClusters)
          cache(cacheResults, SERVER_GROUPS.ns, cachedServerGroups)
          cache(cacheResults, INSTANCES.ns, cachedInstances)
          cache(cacheResults, LOAD_BALANCERS.ns, cachedLoadBalancers)
        } else {
          def serverGroupName = serverGroup.getId()
          def names = Names.parseName(serverGroupName)
          def applicationName = names.app
          def clusterName = names.cluster
          def instances = instancesByServerGroup[serverGroup] ?: []

          def serverGroupKey = Keys.getServerGroupKey(accountName, serverGroupName, credentials.region)
          def applicationKey = Keys.getApplicationKey(applicationName)
          def clusterKey = Keys.getClusterKey(accountName, applicationName, clusterName)
          def loadBalancerKey = Keys.getLoadBalancerKey(accountName, loadBalancerName)

          cachedApplications[applicationKey].with {
            attributes.name = applicationName
            relationships[CLUSTERS.ns].add(clusterKey)
            relationships[SERVER_GROUPS.ns].add(serverGroupKey)
            relationships[LOAD_BALANCERS.ns].add(loadBalancerKey)
          }

          cachedClusters[clusterKey].with {
            attributes.name = clusterName
            relationships[APPLICATIONS.ns].add(applicationKey)
            relationships[SERVER_GROUPS.ns].add(serverGroupKey)
            relationships[LOAD_BALANCERS.ns].add(loadBalancerKey)
          }

          def instanceKeys = instances.inject([], { ArrayList keys, instance ->
            def instanceName = instance.getVmName() ?: instance.getId()
            def key = Keys.getInstanceKey(accountName, instanceName)
            cachedInstances[key].with {
              attributes.name = instanceName
              attributes.instance = new AppengineInstance(instance, serverGroup, loadBalancer, credentials.region)
              relationships[APPLICATIONS.ns].add(applicationKey)
              relationships[CLUSTERS.ns].add(clusterKey)
              relationships[SERVER_GROUPS.ns].add(serverGroupKey)
              relationships[LOAD_BALANCERS.ns].add(loadBalancerKey)
            }
            keys << key
            keys
          })

          cachedServerGroups[serverGroupKey].with {
            attributes.name = serverGroupName
            def isDisabled = !loadBalancer.getSplit().getAllocations().containsKey(serverGroupName);
            attributes.serverGroup = new AppengineServerGroup(serverGroup,
                                                              accountName,
                                                              credentials.region,
                                                              loadBalancerName,
                                                              isDisabled)
            relationships[APPLICATIONS.ns].add(applicationKey)
            relationships[CLUSTERS.ns].add(clusterKey)
            relationships[INSTANCES.ns].addAll(instanceKeys)
          }

          cachedLoadBalancers[loadBalancerKey].with {
            attributes.name = loadBalancerName
            attributes.loadBalancer = new AppengineLoadBalancer(loadBalancer, accountName, credentials.region)
            relationships[SERVER_GROUPS.ns].add(serverGroupKey)
            relationships[INSTANCES.ns].addAll(instanceKeys)
          }
        }
      }
    }

    log.info("Caching ${cachedApplications.size()} applications in ${agentType}")
    log.info("Caching ${cachedClusters.size()} clusters in ${agentType}")
    log.info("Caching ${cachedServerGroups.size()} server groups in ${agentType}")
    log.info("Caching ${cachedLoadBalancers.size()} load balancers in ${agentType}")
    log.info("Caching ${cachedInstances.size()} instances in ${agentType}")

    new DefaultCacheResult([
      (APPLICATIONS.ns): cachedApplications.values(),
      (CLUSTERS.ns): cachedClusters.values(),
      (SERVER_GROUPS.ns): cachedServerGroups.values(),
      (LOAD_BALANCERS.ns): cachedLoadBalancers.values(),
      (INSTANCES.ns): cachedInstances.values(),
      (ON_DEMAND.ns): onDemandKeep.values()
    ], [
      (ON_DEMAND.ns): onDemandEvict
    ])
  }

  Map<Service, List<Version>> loadServerGroups() {
    def project = credentials.project
    def loadBalancers = credentials.appengine.apps().services().list(project).execute().getServices() ?: []
    BatchRequest batch = credentials.appengine.batch() // TODO(jacobkiefer): Consider limiting batch sizes. https://github.com/spinnaker/spinnaker/issues/3564.
    Map<Service, List<Version>> serverGroupsByLoadBalancer = [:].withDefault { [] }

    loadBalancers.each { loadBalancer ->
      def loadBalancerName = loadBalancer.getId()
      def callback = new AppengineCallback<ListVersionsResponse>()
        .success { ListVersionsResponse versionsResponse, HttpHeaders responseHeaders ->
          def versions = versionsResponse.getVersions()
          if (versions) {
            serverGroupsByLoadBalancer[loadBalancer].addAll(versions)
          }
        }

      credentials.appengine.apps().services().versions().list(project, loadBalancerName).queue(batch, callback)
    }

    executeIfRequestsAreQueued(batch)
    return serverGroupsByLoadBalancer
  }

  Map loadServerGroupAndLoadBalancer(String serverGroupName) {
    def project = credentials.project
    def loadBalancers = credentials.appengine.apps().services().list(project).execute().getServices() ?: []
    BatchRequest batch = credentials.appengine.batch()
    Service loadBalancer
    Version serverGroup

    // We don't know where our server group is, so we have to check all of the load balancers.
    loadBalancers.each { Service lb ->
      def loadBalancerName = lb.getId()
      def callback = new AppengineCallback<Version>()
        .success { Version version, HttpHeaders responseHeaders ->
          if (version) {
            serverGroup = version
            loadBalancer = lb
          }
        }
        .failure { GoogleJsonError e, HttpHeaders responseHeaders ->
          if (e.code != 404) {
            def errorJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(e)
            log.error errorJson
          }
        }

        credentials
          .appengine
          .apps()
          .services()
          .versions()
          .get(credentials.project, loadBalancerName, serverGroupName)
          .queue(batch, callback)
    }

    executeIfRequestsAreQueued(batch)
    return [serverGroup: serverGroup, loadBalancer: loadBalancer]
  }

  Map<Version, List<Instance>> loadInstances(Map<Service, List<Version>> serverGroupsByLoadBalancer) {
    BatchRequest batch = credentials.appengine.batch()
    Map<Version, List<Instance>> instancesByServerGroup = [:].withDefault { [] }

    serverGroupsByLoadBalancer.each { Service loadBalancer, List<Version> serverGroups ->
      serverGroups.each { Version serverGroup ->
        def serverGroupName = serverGroup.getId()
        def callback = new AppengineCallback<ListInstancesResponse>()
          .success { ListInstancesResponse instancesResponse, HttpHeaders httpHeaders ->
            def instances = instancesResponse.getInstances()
            if (instances) {
              instancesByServerGroup[serverGroup].addAll(instances)
            }
          }

        credentials
          .appengine
          .apps()
          .services()
          .versions()
          .instances()
          .list(credentials.project, loadBalancer.getId(), serverGroupName)
          .queue(batch, callback)
      }
    }

    executeIfRequestsAreQueued(batch)
    return instancesByServerGroup
  }

  @Override
  Collection<Map> pendingOnDemandRequests(ProviderCache providerCache) {
    def keys = providerCache.getIdentifiers(ON_DEMAND.ns)
    keys = keys.findResults {
      def parse = Keys.parse(it)
      if (parse && parse.account == accountName) {
        return it
      } else {
        return null
      }
    }

    providerCache.getAll(ON_DEMAND.ns, keys).collect {
      def details = Keys.parse(it.id)

      return [
          details       : details,
          moniker       : convertOnDemandDetails(details),
          cacheTime     : it.attributes.cacheTime,
          processedCount: it.attributes.processedCount,
          processedTime : it.attributes.processedTime
      ]
    }
  }
}
