/*
 * Copyright 2016 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.cf.provider.agent

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.frigga.Names
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.*
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent
import com.netflix.spinnaker.clouddriver.cache.OnDemandMetricsSupport
import com.netflix.spinnaker.clouddriver.cf.CloudFoundryCloudProvider
import com.netflix.spinnaker.clouddriver.cf.cache.Keys
import com.netflix.spinnaker.clouddriver.cf.config.CloudFoundryConstants
import com.netflix.spinnaker.clouddriver.cf.model.CloudFoundryService
import com.netflix.spinnaker.clouddriver.cf.provider.CloudFoundryProvider
import com.netflix.spinnaker.clouddriver.cf.provider.ProviderUtils
import com.netflix.spinnaker.clouddriver.cf.security.CloudFoundryAccountCredentials
import com.netflix.spinnaker.clouddriver.cf.utils.CloudFoundryClientFactory
import org.cloudfoundry.client.lib.CloudFoundryException
import org.cloudfoundry.client.lib.CloudFoundryOperations
import org.cloudfoundry.client.lib.domain.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.INFORMATIVE
import static com.netflix.spinnaker.clouddriver.cf.cache.Keys.Namespace.*

class ClusterCachingAgent implements CachingAgent, OnDemandAgent, AccountAware {

  final static Logger log = LoggerFactory.getLogger(ClusterCachingAgent)

  private static final TypeReference<Map<String, Object>> ATTRIBUTES = new TypeReference<Map<String, Object>>() {}

  static final Set<AgentDataType> types = Collections.unmodifiableSet([
      AUTHORITATIVE.forType(SERVER_GROUPS.ns),
      INFORMATIVE.forType(CLUSTERS.ns),
      INFORMATIVE.forType(APPLICATIONS.ns),
      AUTHORITATIVE.forType(INSTANCES.ns),
      AUTHORITATIVE.forType(LOAD_BALANCERS.ns)
  ] as Set)

  final CloudFoundryClientFactory cloudFoundryClientFactory
  final CloudFoundryAccountCredentials account
  final ObjectMapper objectMapper
  final Registry registry

  final OnDemandMetricsSupport metricsSupport

  ClusterCachingAgent(CloudFoundryClientFactory cloudFoundryClientFactory,
                      CloudFoundryAccountCredentials account,
                      ObjectMapper objectMapper,
                      Registry registry) {
    this.objectMapper = objectMapper
    this.account = account
    this.cloudFoundryClientFactory = cloudFoundryClientFactory
    this.registry = registry
    this.metricsSupport = new OnDemandMetricsSupport(registry, this, "${CloudFoundryCloudProvider.ID}:${OnDemandAgent.OnDemandType.ServerGroup}")
  }

  @Override
  String getProviderName() {
    CloudFoundryProvider.PROVIDER_NAME
  }

  @Override
  String getAgentType() {
    "${account.name}/${ClusterCachingAgent.simpleName}"
  }

  @Override
  String getAccountName() {
    account.name
  }

  @Override
  String getOnDemandAgentType() {
    "${agentType}-OnDemand"
  }


  @Override
  Collection<AgentDataType> getProvidedDataTypes() {
    types
  }

  static class MutableCacheData implements CacheData {
    final String id
    int ttlSeconds = -1
    final Map<String, Object> attributes = [:]
    final Map<String, Collection<String>> relationships = [:].withDefault { [] as Set }

    public MutableCacheData(String id) {
      this.id = id
    }

    @JsonCreator
    public MutableCacheData(@JsonProperty("id") String id,
                            @JsonProperty("attributes") Map<String, Object> attributes,
                            @JsonProperty("relationships") Map<String, Collection<String>> relationships) {
      this(id);
      this.attributes.putAll(attributes);
      this.relationships.putAll(relationships);
    }
  }

  @Override
  boolean handles(OnDemandAgent.OnDemandType type, String cloudProvider) {
    type == OnDemandAgent.OnDemandType.ServerGroup && cloudProvider == CloudFoundryCloudProvider.ID
  }

  @Override
  OnDemandAgent.OnDemandResult handle(ProviderCache providerCache, Map<String, ? extends Object> data) {
    if (!data.containsKey("serverGroupName")) {
      return null
    }
    if (!data.containsKey("account")) {
      return null
    }
    if (!data.containsKey("region")) {
      return null
    }

    if (account.name != data.account) {
      return null
    }

    if (account.org != data.region) {
      return null
    }

    String serverGroupName = data.serverGroupName.toString()

    def client = cloudFoundryClientFactory.createCloudFoundryClient(account, true)

    def spaces = loadSpaces(client)
    def currentSpace = spaces.values().find { it?.name == account.space && it?.organization.name == account.org }
    def services = loadServices(client, currentSpace)
    def routes = loadRoutes(client)

    Collection<AppAndInstances> onDemandData = metricsSupport.readData {
      try {
        def application = client.getApplication(serverGroupName)
        [
            new AppAndInstances(
                app: application,
                instancesInfo: client.getApplicationInstances(application)
            )
        ]
      } catch (CloudFoundryException e) {
        []
      }
    }

    def cacheResult = metricsSupport.transformData {
      buildCacheResult(onDemandData, currentSpace, services, routes, [:], [], Long.MAX_VALUE)
    }

    if (cacheResult.cacheResults.values().flatten().isEmpty()) {
      // Avoid writing an empty onDemand cache record (instead delete any that may have previously existed).
      providerCache.evictDeletedItems(ON_DEMAND.ns, [Keys.getServerGroupKey(serverGroupName, account.name, account.org)])
    } else {
      metricsSupport.onDemandStore {
        def cacheData = new DefaultCacheData(
            Keys.getServerGroupKey(serverGroupName, account.name, account.org),
            10 * 60,
            [
                cacheTime     : System.currentTimeMillis(),
                cacheResults  : objectMapper.writeValueAsString(cacheResult.cacheResults),
                processedCount: 0,
                processedTime : null
            ],
            [:]
        )

        providerCache.putCacheData(ON_DEMAND.ns, cacheData)
      }
    }

    Map<String, Collection<String>> evictions = !onDemandData.isEmpty() ? [:] : [
        (SERVER_GROUPS.ns): [
            Keys.getServerGroupKey(serverGroupName, account.name, account.org)
        ]
    ]

    log.info("onDemand cache refresh (data: ${data}, evictions: ${evictions})")

    return new OnDemandAgent.OnDemandResult(
        sourceAgentType: getOnDemandAgentType(), cacheResult: cacheResult, evictions: evictions
    )
  }

  @Override
  Collection<Map> pendingOnDemandRequests(ProviderCache providerCache) {
    def keys = providerCache.getIdentifiers(ON_DEMAND.ns)
    providerCache.getAll(ON_DEMAND.ns, keys).collect {
      [
        id: it.id,
        details: Keys.parse(it.id),
        cacheTime: it.attributes.cacheTime,
        processedCount: it.attributes.processedCount ?: 0,
        processedTime: it.attributes.processedTime
      ]
    }
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    Long start = System.currentTimeMillis()

    log.info "Describing items in ${agentType}"

    def client = cloudFoundryClientFactory.createCloudFoundryClient(account, true)

    def spaces = loadSpaces(client)
    def currentSpace = spaces.values().find { it?.name == account.space && it?.organization.name == account.org }
    def services = loadServices(client, currentSpace)
    def routes = loadRoutes(client)

    Collection<AppAndInstances> appsAndInstances = client.applications.collect { app ->
      new AppAndInstances(app: app, instancesInfo: client.getApplicationInstances(app))
    }

    def evictableOnDemandCacheDatas = []
    def keepInOnDemand = []

    providerCache.getAll(ON_DEMAND.ns, appsAndInstances.collect {
      appAndInstances -> Keys.getServerGroupKey(appAndInstances.app.name, account.name, account.org)
    }).each {
      if (new Long(it.attributes.cacheTime) < start && it.attributes.processedCount > 0) {
        evictableOnDemandCacheDatas << it
      } else {
        keepInOnDemand << it
      }
    }

    def stuffToConsider = keepInOnDemand.collectEntries { [(it.id), it] }

    def result = buildCacheResult(appsAndInstances, currentSpace, services, routes,
        stuffToConsider, evictableOnDemandCacheDatas*.id, start)

    result.cacheResults[ON_DEMAND.ns].each {
      it.attributes.processedTime = System.currentTimeMillis()
      it.attributes.processedCount = (it.attributes.processedCount ?: 0) + 1
    }

    result
  }

  private Set<CloudRoute> loadRoutes(CloudFoundryOperations client) {
    log.info "Looking up routes for ${agentType}"
    Set<CloudRoute> routes = [] as Set<CloudRoute>
    client.domainsForOrg.each { domain ->
      client.getRoutes(domain.name).each { route ->
        routes.add(route)
      }
    }
    routes
  }

  private Map<String, Set<CloudService>> loadServices(CloudFoundryOperations client, currentSpace) {
    log.info "Looking up services for ${agentType}"
    Map<String, Set<CloudService>> services = [:].withDefault { [] as Set<CloudService> }
    client.services.each { service ->
      services[currentSpace.meta.guid].add(service)
    }
    services
  }

  private LinkedHashMap<String, CloudSpace> loadSpaces(CloudFoundryOperations client) {
    log.info "Looking up spaces for ${agentType}"
    Map<String, CloudSpace> spaces = [:]
    client.spaces.each { space ->
      if (!spaces.containsKey(space.meta.guid) && space.name == account.space) {
        log.info "Storing space ${space.name} in ${agentType}"
        spaces[space.meta.guid] = space
      }
    }
    spaces
  }

  private CacheResult buildCacheResult(Collection<AppAndInstances> appsAndInstances,
                                       CloudSpace currentSpace,
                                       Map<String, Set<CloudService>> services,
                                       Set<CloudRoute> routes,
                                       Map<String, CacheData> onDemandKeep,
                                       Collection<String> evictableOnDemandCacheDataIdentifiers,
                                       Long start) {

    Map<String, CacheData> applications = cache()
    Map<String, CacheData> clusters = cache()
    Map<String, CacheData> serverGroups = cache()
    Map<String, CacheData> instances = cache()
    Map<String, CacheData> loadBalancers = cache()

    appsAndInstances.findAll {
      it.app.envAsMap.containsKey(CloudFoundryConstants.LOAD_BALANCERS) // Skip apps not fully created
    }.each { appAndInstances ->

      def app = appAndInstances.app

      app.space = currentSpace // For some reason, app.space's org is null

      def onDemandData = onDemandKeep ?
          onDemandKeep[Keys.getServerGroupKey(app.name, account.name, app.space.organization.name)] : null

      if (onDemandData && new Long(onDemandData.attributes.cacheTime) >= start) {
        log.info("Using onDemand cache value (${onDemandData.id})")
        Map<String, List<CacheData>> cacheResults = objectMapper.readValue(onDemandData.attributes.cacheResults as String, new TypeReference<Map<String, List<MutableCacheData>>>() {})

        cacheResults["instances"].each {
          it.attributes.nativeInstance = ProviderUtils.buildNativeInstance(it.attributes.nativeInstance)
        }

        cacheResults["serverGroups"].each {
          it.attributes.nativeApplication = ProviderUtils.buildNativeApplication(it.attributes.nativeApplication)
          it.attributes.services.each {
            it.nativeService = ProviderUtils.buildNativeService(it.nativeService)
          }
        }
        cache(cacheResults, APPLICATIONS.ns, applications)
        cache(cacheResults, CLUSTERS.ns, clusters)
        cache(cacheResults, SERVER_GROUPS.ns, serverGroups)
        cache(cacheResults, INSTANCES.ns, instances)
        cache(cacheResults, LOAD_BALANCERS.ns, loadBalancers)
      } else {
        try {
          CloudFoundryData data = new CloudFoundryData(app, account.name, appAndInstances.instancesInfo?.instances, routes)
          cacheApplications(data, applications)
          cacheCluster(data, clusters)
          cacheServerGroup(data, serverGroups, currentSpace, services)
          cacheInstances(data, instances)
          cacheLoadBalancers(data, loadBalancers)
        } catch (Exception e) {
          log.warn("Failed to cache ${app.name} in ${account.name}", e)
        }
      }
    }

    new DefaultCacheResult([
      (APPLICATIONS.ns): applications.values(),
      (CLUSTERS.ns): clusters.values(),
      (SERVER_GROUPS.ns): serverGroups.values(),
      (INSTANCES.ns): instances.values(),
      (LOAD_BALANCERS.ns): loadBalancers.values(),
      (ON_DEMAND.ns): onDemandKeep.values()
    ],[
      (ON_DEMAND.ns): evictableOnDemandCacheDataIdentifiers
    ])
  }

  private Map<String, CacheData> cache() {
    [:].withDefault { String id -> new MutableCacheData(id) }
  }

  private static void cache(Map<String, List<CacheData>> cacheResults, String namespace, Map<String, CacheData> cacheDataById) {
    cacheResults[namespace].each {
      def existingCacheData = cacheDataById[it.id]
      if (!existingCacheData) {
        cacheDataById[it.id] = it
      } else {
        existingCacheData.attributes.putAll(it.attributes)
        it.relationships.each { String relationshipName, Collection<String> relationships ->
          existingCacheData.relationships[relationshipName].addAll(relationships)
        }
      }
    }
  }


  private void cacheApplications(CloudFoundryData data, Map<String, CacheData> applications) {
    applications[data.appNameKey].with {
      attributes.name = data.name.app

      relationships[CLUSTERS.ns].add(data.clusterKey)
      relationships[SERVER_GROUPS.ns].add(data.serverGroupKey)
      relationships[LOAD_BALANCERS.ns].addAll(data.loadBalancerKeys)
    }
  }

  private void cacheCluster(CloudFoundryData data, Map<String, CacheData> clusters) {
    clusters[data.clusterKey].with {
      attributes.name = data.name.cluster

      relationships[APPLICATIONS.ns].add(data.appNameKey)
      relationships[SERVER_GROUPS.ns].add(data.serverGroupKey)
      relationships[LOAD_BALANCERS.ns].addAll(data.loadBalancerKeys)
    }
  }

  private void cacheServerGroup(CloudFoundryData data, Map<String, CacheData> serverGroups, CloudSpace currentSpace, Map<String, Set<CloudService>> services) {
    serverGroups[data.serverGroupKey].with {
      attributes.name = data.application.name
      attributes.logsLink = "${account.console}/organizations/${data.application.space.organization.meta.guid}/spaces/${data.application.space.meta.guid}/applications/${data.application.meta.guid}/tailing_logs".toString()
      attributes.consoleLink = "${account.console}/organizations/${data.application.space.organization.meta.guid}/spaces/${data.application.space.meta.guid}/applications/${data.application.meta.guid}".toString()
      attributes.nativeApplication = data.application
      attributes.services = services[currentSpace.meta.guid].findAll { data.application.services.contains(it.name) }.collect {
        new CloudFoundryService([
            type: 'cf',
            id: it.meta.guid,
            name: it.name,
            application: data.name.app,
            accountName: account.name,
            region: currentSpace.organization.name,
            nativeService: it
        ])
      }

      relationships[APPLICATIONS.ns].add(data.appNameKey)
      relationships[CLUSTERS.ns].add(data.clusterKey)
      relationships[INSTANCES.ns].addAll(data.instanceIdKeys)
      relationships[LOAD_BALANCERS.ns].addAll(data.loadBalancerKeys)
    }
  }

  private void cacheInstances(CloudFoundryData data, Map<String, CacheData> instances) {
    data.instances.each { instance ->
      def id = getInstanceId(data.application, instance)
      def instanceIdKey = Keys.getInstanceKey(id, account.name, data.application.space.organization.name)
      instances[instanceIdKey].with {
        attributes.name = id
        attributes.nativeInstance = instance

        relationships[SERVER_GROUPS.ns].add(data.serverGroupKey)
      }
    }
  }

  private void cacheLoadBalancers(CloudFoundryData data, Map<String, CacheData> loadBalancers) {
    data.loadBalancers.each { loadBalancer ->
      loadBalancers[Keys.getLoadBalancerKey(loadBalancer.name, loadBalancer.account, loadBalancer.region)].with {
        attributes.name = loadBalancer.name
        attributes.nativeRoute = loadBalancer.nativeRoute

        relationships[APPLICATIONS.ns].add(data.appNameKey)
        relationships[SERVER_GROUPS.ns].add(data.serverGroupKey)
        relationships[INSTANCES.ns].addAll(data.instanceIdKeys)
      }
    }
  }

  private static String getInstanceId(CloudApplication application, InstanceInfo instance) {
    application.name + '(' + instance.index + ')'
  }

  private static class CloudFoundryData {
    final CloudApplication application
    final Names name
    final List<InstanceInfo> instances
    final Set<Map> loadBalancers

    final String appNameKey
    final String clusterKey
    final String serverGroupKey
    final Set<String> instanceIdKeys
    final Set<String> loadBalancerKeys

    public CloudFoundryData(CloudApplication application,
                            String account,
                            List<InstanceInfo> instances,
                            Set<CloudRoute> routes) {
      this.application = application
      this.name = Names.parseName(application.name)
      this.instances = instances
      this.loadBalancers = (application.envAsMap[CloudFoundryConstants.LOAD_BALANCERS] ?: '').split(',').collect { route ->
        [
            name       : route,
            region     : application.space.organization.name,
            account    : account,
            nativeRoute: routes.find { it.host == route }
        ]
      }

      this.appNameKey = Keys.getApplicationKey(name.app)
      this.clusterKey = Keys.getClusterKey(name.cluster, name.app, account)
      this.serverGroupKey = Keys.getServerGroupKey(application.name, account, application.space.organization.name)
      this.instanceIdKeys = (this.instances == null) ? Collections.emptyList() : this.instances.collect { InstanceInfo it ->
        Keys.getInstanceKey(getInstanceId(application, it), account, application.space.organization.name)
      }
      this.loadBalancerKeys = this.loadBalancers.collect {
        Keys.getLoadBalancerKey(it.name, it.account, it.region)
      }
    }
  }

  private static class AppAndInstances {
    CloudApplication app
    InstancesInfo instancesInfo
  }

}
