/*
 * Copyright 2017 Cerner Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.spinnaker.clouddriver.dcos.provider.agent

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.collect.Iterables
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.*
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent
import com.netflix.spinnaker.clouddriver.cache.OnDemandMetricsSupport
import com.netflix.spinnaker.clouddriver.dcos.DcosClientProvider
import com.netflix.spinnaker.clouddriver.dcos.DcosCloudProvider
import com.netflix.spinnaker.clouddriver.dcos.cache.Keys
import com.netflix.spinnaker.clouddriver.dcos.deploy.util.id.DcosSpinnakerAppId
import com.netflix.spinnaker.clouddriver.dcos.model.DcosInstance
import com.netflix.spinnaker.clouddriver.dcos.model.DcosServerGroup
import com.netflix.spinnaker.clouddriver.dcos.provider.DcosProvider
import com.netflix.spinnaker.clouddriver.dcos.provider.MutableCacheData
import com.netflix.spinnaker.clouddriver.dcos.security.DcosAccountCredentials
import groovy.util.logging.Slf4j
import mesosphere.dcos.client.DCOS
import mesosphere.marathon.client.model.v2.App
import mesosphere.marathon.client.model.v2.GetAppNamespaceResponse

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.INFORMATIVE

@Slf4j
class DcosServerGroupCachingAgent implements CachingAgent, OnDemandAgent, DcosClusterAware {
  private final Collection<DcosAccountCredentials> accounts
  private final Set<String> accountNames
  private final String clusterName
  private final String serviceAccountUID
  private final String clusterUrl
  private final DCOS dcosClient
  private final DcosCloudProvider dcosCloudProvider = new DcosCloudProvider()
  private final ObjectMapper objectMapper
  final OnDemandMetricsSupport metricsSupport

  static final Set<AgentDataType> types =
    Collections.unmodifiableSet([
      AUTHORITATIVE.forType(Keys.Namespace.SERVER_GROUPS.ns),
      AUTHORITATIVE.forType(Keys.Namespace.APPLICATIONS.ns),
      AUTHORITATIVE.forType(Keys.Namespace.CLUSTERS.ns),
      AUTHORITATIVE.forType(Keys.Namespace.INSTANCES.ns),
      INFORMATIVE.forType(Keys.Namespace.LOAD_BALANCERS.ns),
    ] as Set)

  DcosServerGroupCachingAgent(Collection<DcosAccountCredentials> accounts,
                              String clusterName,
                              DcosClientProvider clientProvider,
                              ObjectMapper objectMapper,
                              Registry registry) {
    this.accounts = accounts
    this.accountNames = accounts.collect { account -> account.account }

    def primaryAccount = Iterables.getFirst(accounts, null)
    this.clusterName = clusterName
    this.serviceAccountUID = primaryAccount.getCredentialsByCluster(clusterName).dcosConfig.credentials.uid
    this.clusterUrl = primaryAccount.getCredentialsByCluster(clusterName).dcosUrl
    this.objectMapper = objectMapper
    this.dcosClient = clientProvider.getDcosClient(primaryAccount, clusterName)
    this.metricsSupport = new OnDemandMetricsSupport(registry,
      this,
      "$dcosCloudProvider.id:$OnDemandAgent.OnDemandType.ServerGroup")
  }

  @Override
  String getAgentType() {
    return "${clusterName}/${serviceAccountUID}/${DcosServerGroupCachingAgent.simpleName}"
  }

  @Override
  String getProviderName() {
    DcosProvider.name
  }

  @Override
  Collection<DcosAccountCredentials> getAccounts() {
    return accounts
  }

  @Override
  String getClusterName() {
    return clusterName
  }

  @Override
  String getServiceAccountUID() {
    return serviceAccountUID
  }

  @Override
  Collection<AgentDataType> getProvidedDataTypes() {
    return types
  }

  @Override
  CacheResult loadData(final ProviderCache providerCache) {
    Long start = System.currentTimeMillis()

    def serverGroups = loadServerGroups()

    List<CacheData> evictFromOnDemand = []
    List<CacheData> keepInOnDemand = []

    providerCache.getAll(Keys.Namespace.ON_DEMAND.ns,
      serverGroups.collect { serverGroup ->
        Keys.getServerGroupKey(DcosSpinnakerAppId.parse(serverGroup.app.id).get(), clusterName)
      })
      .each { CacheData onDemandEntry ->
      // Ensure that we don't overwrite data that was inserted by the `handle` method while we retrieved the
      // replication controllers. Furthermore, cache data that hasn't been processed needs to be updated in the ON_DEMAND
      // cache, so don't evict data without a processedCount > 0.
      if (onDemandEntry.attributes.cacheTime < start && onDemandEntry.attributes.processedCount > 0) {
        evictFromOnDemand << onDemandEntry
      } else {
        keepInOnDemand << onDemandEntry
      }
    }

    def result = buildCacheResult(serverGroups, keepInOnDemand.collectEntries { CacheData onDemandEntry ->
      [(onDemandEntry.id): onDemandEntry]
    }, evictFromOnDemand*.id, start)

    result.cacheResults[Keys.Namespace.ON_DEMAND.ns].each { CacheData onDemandEntry ->
      onDemandEntry.attributes.processedTime = System.currentTimeMillis()
      onDemandEntry.attributes.processedCount = (onDemandEntry.attributes.processedCount ?: 0) + 1
    }

    return result
  }

  // OnDemandAgent methods

  @Override
  String getOnDemandAgentType() {
    return "${getAgentType()}-OnDemand"
  }

  @Override
  boolean handles(final OnDemandAgent.OnDemandType type, final String cloudProvider) {
    return OnDemandAgent.OnDemandType.ServerGroup == type && cloudProvider == dcosCloudProvider.id
  }

  @Override
  OnDemandAgent.OnDemandResult handle(final ProviderCache providerCache, final Map<String, ?> data) {
    if (!data.containsKey("serverGroupName")) {
      return null
    }

    if (!accountNames.contains(data.account.toString())) {
      return null
    }

    if (data.dcosCluster != clusterName) {
      return null
    }

    def serverGroupName = data.serverGroupName.toString()

    def appId = DcosSpinnakerAppId.from(data.account.toString(), data.group.toString(), serverGroupName)

    if (!appId.isPresent()) {
      return null
    }

    def spinnakerId = appId.get()

    DcosServerGroup serverGroup = metricsSupport.readData {
      loadServerGroup(spinnakerId.toString())
    }

    CacheResult result = metricsSupport.transformData {
      buildCacheResult([serverGroup], [:], [], Long.MAX_VALUE)
    }

    def jsonResult = objectMapper.writeValueAsString(result.cacheResults)

    if (result.cacheResults.values().flatten().isEmpty()) {
      // Avoid writing an empty onDemand cache record (instead delete any that may have previously existed).
      providerCache.evictDeletedItems(Keys.Namespace.ON_DEMAND.ns, [Keys.getServerGroupKey(spinnakerId, clusterName)])
    } else {
      metricsSupport.onDemandStore {
        def cacheData = new DefaultCacheData(
          Keys.getServerGroupKey(spinnakerId, clusterName),
          10 * 60, // ttl is 10 minutes
          [
            cacheTime     : System.currentTimeMillis(),
            cacheResults  : jsonResult,
            processedCount: 0,
            processedTime : null
          ],
          [:]
        )


        providerCache.putCacheData(Keys.Namespace.ON_DEMAND.ns, cacheData)
      }
    }

    // Evict this server group if it no longer exists.
    Map<String, Collection<String>> evictions = serverGroup ? [:] : [
      (Keys.Namespace.SERVER_GROUPS.ns): [
        Keys.getServerGroupKey(spinnakerId, clusterName)
      ]
    ]

    log.info("On demand cache refresh (data: ${data}) succeeded.")

    return new OnDemandAgent.OnDemandResult(
      sourceAgentType: getOnDemandAgentType(),
      cacheResult: result,
      evictions: evictions //TODO
    )
  }

  @Override
  Collection<Map> pendingOnDemandRequests(final ProviderCache providerCache) {
    def keys = providerCache.getIdentifiers(Keys.Namespace.ON_DEMAND.ns)
    keys = keys.findResults {
      def parse = Keys.parse(it)
      if (parse && accountNames.contains(parse.account)) {
        return it
      } else {
        return null
      }
    }

    providerCache.getAll(Keys.Namespace.ON_DEMAND.ns, keys).collect {
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

  // PRIVATE METHODS

  private DcosServerGroup loadServerGroup(String dcosAppId) {
    App app = dcosClient.getApp(dcosAppId)?.app
    app ? new DcosServerGroup(clusterName, clusterUrl, app) : null
  }

  private List<DcosServerGroup> loadServerGroups() {
    final Optional<GetAppNamespaceResponse> response = dcosClient.maybeApps("", ['app.tasks', 'app.deployments'])
    if (!response.isPresent()) {
      log.info("Unable to retrieve DC/OS applications from the root namespace. No server groups will be cached.")
      return []
    }

    response.get().apps.findAll {
      def id = DcosSpinnakerAppId.parse(it.id)
      !it.labels?.containsKey("SPINNAKER_LOAD_BALANCER") && id.isPresent() && accountNames.contains(id.get().account)
    }.collect {
      new DcosServerGroup(clusterName, clusterUrl, it)
    }
  }

  private CacheResult buildCacheResult(List<DcosServerGroup> serverGroups,
                                       Map<String, CacheData> onDemandKeep,
                                       List<String> onDemandEvict,
                                       Long start) {
    Map<String, MutableCacheData> cachedApps = MutableCacheData.mutableCacheMap()
    Map<String, MutableCacheData> cachedServerGroups = MutableCacheData.mutableCacheMap()
    Map<String, MutableCacheData> cachedClusters = MutableCacheData.mutableCacheMap()
    Map<String, MutableCacheData> cachedInstances = MutableCacheData.mutableCacheMap()
    Map<String, MutableCacheData> cachedLoadBalancers = MutableCacheData.mutableCacheMap()

    serverGroups.each { serverGroup ->
      if (serverGroup == null) {
        return
      }

      def app = serverGroup.app
      def onDemandData = onDemandKeep ?
        onDemandKeep[Keys.getServerGroupKey(DcosSpinnakerAppId.parse(app.id).get(), clusterName)] :
        null

      if (onDemandData && onDemandData.attributes.cacheTime >= start) {
        Map<String, List<CacheData>> cacheResults = objectMapper.readValue(onDemandData.attributes.cacheResults as String,
          new TypeReference<Map<String, List<MutableCacheData>>>() {
          })
        cache(cacheResults, Keys.Namespace.APPLICATIONS.ns, cachedApps)
        cache(cacheResults, Keys.Namespace.CLUSTERS.ns, cachedClusters)
        cache(cacheResults, Keys.Namespace.SERVER_GROUPS.ns, cachedServerGroups)
        cache(cacheResults, Keys.Namespace.INSTANCES.ns, cachedInstances)

      } else {

        // based on the way kubernetes handles this it looks like the app id must conform to
        // the spinnaker naming convention as we have to parse it.  There's no storage that maps
        // an arbitrary app id to these fields
        def appId = app.id
        def spinnakerId = DcosSpinnakerAppId.parse(appId).get()
        def names = spinnakerId.getServerGroupName()
        def appName = names.app
        def cluster = names.cluster
        def instanceKeys = []

        def loadBalancerKeys = serverGroup.fullyQualifiedLoadBalancers.findResults({
          Keys.getLoadBalancerKey(it, clusterName)
        })

        def applicationKey = Keys.getApplicationKey(appName)
        def serverGroupKey = Keys.getServerGroupKey(spinnakerId, clusterName)
        String clusterKey = Keys.getClusterKey(spinnakerId.account, appName, cluster)

        cachedApps[applicationKey].with {
          attributes.name = appName
          relationships[Keys.Namespace.CLUSTERS.ns].add(clusterKey)
          relationships[Keys.Namespace.SERVER_GROUPS.ns].add(serverGroupKey)
          relationships[Keys.Namespace.LOAD_BALANCERS.ns].addAll(loadBalancerKeys)
        }

        cachedClusters[clusterKey].with {
          attributes.name = cluster
          relationships[Keys.Namespace.APPLICATIONS.ns].add(applicationKey)
          relationships[Keys.Namespace.SERVER_GROUPS.ns].add(serverGroupKey)
          relationships[Keys.Namespace.LOAD_BALANCERS.ns].addAll(loadBalancerKeys)
        }

        app.tasks.forEach { task ->
          final String safeGroup = DcosSpinnakerAppId.parse(task.getAppId()).get().getSafeGroup()
          def groupName = clusterName
          if (!safeGroup.isEmpty()) {
            groupName = "${clusterName}_${safeGroup}"
          }
          final String instanceKey = Keys.getInstanceKey(spinnakerId.account, groupName, task.id)
          instanceKeys << instanceKey
          final boolean isDeploying = app.deployments != null && !app.deployments.empty
          cachedInstances[instanceKey].with {
            attributes.name = task.id
            attributes.instance = new DcosInstance(task, spinnakerId.account, clusterName, clusterUrl, isDeploying)
            relationships[Keys.Namespace.APPLICATIONS.ns].add(applicationKey)
            relationships[Keys.Namespace.CLUSTERS.ns].add(clusterKey)
            relationships[Keys.Namespace.SERVER_GROUPS.ns].add(serverGroupKey)
            relationships[Keys.Namespace.LOAD_BALANCERS.ns].addAll(loadBalancerKeys)
          }
        }

        loadBalancerKeys.forEach { loadBalancerKey ->
          cachedLoadBalancers[loadBalancerKey].with {
            relationships[Keys.Namespace.SERVER_GROUPS.ns].add(serverGroupKey)
            relationships[Keys.Namespace.INSTANCES.ns].addAll(instanceKeys)
          }
        }

        cachedServerGroups[serverGroupKey].with {
          attributes.name = appId
          attributes.serverGroup = serverGroup
          relationships[Keys.Namespace.APPLICATIONS.ns].add(applicationKey)
          relationships[Keys.Namespace.CLUSTERS.ns].add(clusterKey)
          relationships[Keys.Namespace.LOAD_BALANCERS.ns].addAll(loadBalancerKeys)
          relationships[Keys.Namespace.INSTANCES.ns].addAll(instanceKeys)
        }

      }
    }

    log.info("Caching ${cachedApps.size()} applications in ${agentType}")
    log.info("Caching ${cachedClusters.size()} clusters in ${agentType}")
    log.info("Caching ${cachedServerGroups.size()} server groups in ${agentType}")
    log.info("Caching ${cachedInstances.size()} instances in ${agentType}")

    new DefaultCacheResult([
      (Keys.Namespace.SERVER_GROUPS.ns) : cachedServerGroups.values(),
      (Keys.Namespace.LOAD_BALANCERS.ns): cachedLoadBalancers.values(),
      (Keys.Namespace.CLUSTERS.ns)      : cachedClusters.values(),
      (Keys.Namespace.SERVER_GROUPS.ns) : cachedServerGroups.values(),
      (Keys.Namespace.INSTANCES.ns)     : cachedInstances.values(),
      (Keys.Namespace.APPLICATIONS.ns)  : cachedApps.values(),
      (Keys.Namespace.ON_DEMAND.ns)     : onDemandKeep.values()],
      [(Keys.Namespace.ON_DEMAND.ns): onDemandEvict])
  }

  private
  static void cache(Map<String, List<CacheData>> cacheResults, String cacheNamespace, Map<String, CacheData> cacheDataById) {
    cacheResults[cacheNamespace].each {
      def existingCacheData = cacheDataById[it.id]
      if (existingCacheData) {
        existingCacheData.attributes.putAll(it.attributes)
        it.relationships.each { String relationshipName, Collection<String> relationships ->
          existingCacheData.relationships[relationshipName].addAll(relationships)
        }
      } else {
        cacheDataById[it.id] = it
      }
    }
  }
}
