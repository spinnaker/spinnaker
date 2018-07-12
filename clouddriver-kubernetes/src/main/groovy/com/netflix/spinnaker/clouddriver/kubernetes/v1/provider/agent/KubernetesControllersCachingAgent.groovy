/*
 * Copyright 2017 Cisco, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
 */

package com.netflix.spinnaker.clouddriver.kubernetes.v1.provider.agent

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
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
import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesCloudProvider
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.kubernetes.v1.caching.Keys
import com.netflix.spinnaker.clouddriver.kubernetes.v1.deploy.KubernetesUtil
import com.netflix.spinnaker.clouddriver.kubernetes.v1.model.KubernetesV1ServerGroup
import com.netflix.spinnaker.clouddriver.kubernetes.v1.provider.view.MutableCacheData
import com.netflix.spinnaker.clouddriver.kubernetes.v1.security.KubernetesV1Credentials
import groovy.util.logging.Slf4j
import io.fabric8.kubernetes.api.model.Event
import io.kubernetes.client.models.V1PodList
import io.kubernetes.client.models.V1beta1DaemonSet
import io.kubernetes.client.models.V1beta1StatefulSet

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.INFORMATIVE

@Slf4j
class KubernetesControllersCachingAgent extends KubernetesV1CachingAgent implements OnDemandAgent {
  final String category = 'serverGroup'
  final OnDemandMetricsSupport metricsSupport

  static final Set<AgentDataType> types = Collections.unmodifiableSet([
    AUTHORITATIVE.forType(Keys.Namespace.APPLICATIONS.ns),
    AUTHORITATIVE.forType(Keys.Namespace.CLUSTERS.ns),
    INFORMATIVE.forType(Keys.Namespace.LOAD_BALANCERS.ns),
    AUTHORITATIVE.forType(Keys.Namespace.SERVER_GROUPS.ns),
    INFORMATIVE.forType(Keys.Namespace.INSTANCES.ns),
  ] as Set)

  KubernetesControllersCachingAgent(KubernetesNamedAccountCredentials<KubernetesV1Credentials> namedAccountCredentials,
                                    ObjectMapper objectMapper,
                                    Registry registry,
                                    int agentIndex,
                                    int agentCount) {
    super(namedAccountCredentials, objectMapper, registry, agentIndex, agentCount)
    this.metricsSupport = new OnDemandMetricsSupport(registry, this, "$KubernetesCloudProvider.ID:$OnDemandAgent.OnDemandType.ServerGroup")
  }

  @Override
  Collection<AgentDataType> getProvidedDataTypes() {
    return types
  }

  @Override
  String getOnDemandAgentType() {
    "${getAgentType()}-OnDemand"
  }

  @Override
  OnDemandMetricsSupport getMetricsSupport() {
    return null
  }

  @Override
  boolean handles(OnDemandAgent.OnDemandType type, String cloudProvider) {
    OnDemandAgent.OnDemandType.ServerGroup == type && cloudProvider == KubernetesCloudProvider.ID
  }

  @Override
  OnDemandAgent.OnDemandResult handle(ProviderCache providerCache, Map<String, ? extends Object> data) {
    if (!data.containsKey("serverGroupName")) {
      return null
    }

    if (data.account != accountName) {
      return null
    }

    reloadNamespaces()
    String namespace = data.region
    if (!namespaces.contains(namespace)) {
      return null
    }

    def serverGroupName = data.serverGroupName.toString()

    V1beta1StatefulSet statefulSet = metricsSupport.readData {
      loadStatefulSet(namespace, serverGroupName)
    }

    V1beta1DaemonSet daemonSet = metricsSupport.readData {
      loadDaemonSet(namespace, serverGroupName)
    }

    CacheResult result = metricsSupport.transformData {
      buildCacheResult([new KubernetesController(statefulController: statefulSet, daemonController: daemonSet)], [:], [], Long.MAX_VALUE)
    }

    def jsonResult = objectMapper.writeValueAsString(result.cacheResults)
    boolean isControllerSetCachingAgentType = true
    if (result.cacheResults.values().flatten().isEmpty()) {

      // Determine if this is the correct agent to delete cache which can avoid double deletion
      CacheData serverGroup = providerCache.get(Keys.Namespace.SERVER_GROUPS.ns, Keys.getServerGroupKey(accountName, namespace, serverGroupName))

      if (serverGroup) {
        String kind = serverGroup.attributes?.get("serverGroup")?.get("kind")
        if (kind == "StatefulSet" || kind == "DaemonSet") {
          // Avoid writing an empty onDemand cache record (instead delete any that may have previously existed).
          providerCache.evictDeletedItems(Keys.Namespace.ON_DEMAND.ns, [Keys.getServerGroupKey(accountName, namespace, serverGroupName)])
        }else{
          isControllerSetCachingAgentType = false
        }
      }
    } else {
      metricsSupport.onDemandStore {
        def cacheData = new DefaultCacheData(
          Keys.getServerGroupKey(accountName, namespace, serverGroupName),
          10 * 60, // ttl is 10 minutes
          [
            cacheTime: System.currentTimeMillis(),
            cacheResults: jsonResult,
            processedCount: 0,
            processedTime: null
          ],
          [:]
        )
        providerCache.putCacheData(Keys.Namespace.ON_DEMAND.ns, cacheData)
      }
    }

    // Evict this server group if it no longer exists.
    Map<String, Collection<String>> evictions
    if (isControllerSetCachingAgentType) {
      evictions = statefulSet || daemonSet ? [:] : [
        (Keys.Namespace.SERVER_GROUPS.ns): [
          Keys.getServerGroupKey(accountName, namespace, serverGroupName)
        ]
      ]
    }

    log.info("On demand cache refresh (data: ${data}) succeeded.")

    return new OnDemandAgent.OnDemandResult(
      sourceAgentType: getOnDemandAgentType(),
      cacheResult: result,
      evictions: evictions
    )
  }

  @Override
  Collection<Map> pendingOnDemandRequests(ProviderCache providerCache) {
    def keys = providerCache.getIdentifiers(Keys.Namespace.ON_DEMAND.ns)
    keys = keys.findResults {
      def parse = Keys.parse(it)
      if (parse && namespaces.contains(parse.namespace) && parse.account == accountName) {
        return it
      } else {
        return null
      }
    }

    def keyCount = keys.size()
    def be = keyCount == 1 ? "is" : "are"
    def pluralize = keyCount == 1 ? "" : "s"
    log.info("There $be $keyCount pending on demand request$pluralize")

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

  /**
   * Triggered by an AgentScheduler to tell this Agent to load its data.
   *
   * @param providerCache Cache associated with this Agent's provider
   * @return the complete set of data for this Agent.
   */
  @Override
  CacheResult loadData(ProviderCache providerCache) {
    reloadNamespaces()
    Long start = System.currentTimeMillis()
    List<V1beta1StatefulSet> statefulSet = loadStatefulSets()
    List<V1beta1DaemonSet> daemonSet = loadDaemonSets()
    List<KubernetesController> serverGroups = (statefulSet.collect {
      it ? new KubernetesController(statefulController: it) : null
    }+ daemonSet.collect {
      it ? new KubernetesController(daemonController: it) : null
    }
    ) - null
    List<CacheData> evictFromOnDemand = []
    List<CacheData> keepInOnDemand = []
    providerCache.getAll(Keys.Namespace.ON_DEMAND.ns,
      serverGroups.collect { serverGroup ->
        Keys.getServerGroupKey(accountName, serverGroup.namespace, serverGroup.name)
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

  List<V1beta1StatefulSet> loadStatefulSets() {
    namespaces.collect { String namespace ->
      credentials.apiClientAdaptor.getStatefulSets(namespace)
    }.flatten()
  }

  List<V1beta1DaemonSet> loadDaemonSets() {
    namespaces.collect { String namespace ->
      credentials.apiClientAdaptor.getDaemonSets(namespace)
    }.flatten()
  }

  V1PodList loadPods(KubernetesController serverGroup) {
    credentials.apiClientAdaptor.getPods(serverGroup.namespace, serverGroup.selector)
  }

  V1beta1StatefulSet loadStatefulSet(String namespace, String name) {
    credentials.apiClientAdaptor.getStatefulSet(name, namespace)
  }

  V1beta1DaemonSet loadDaemonSet(String namespace, String name) {
    credentials.apiClientAdaptor.getDaemonSet(name, namespace)
  }

  private CacheResult buildCacheResult(List<KubernetesController> serverGroups, Map<String, CacheData> onDemandKeep, List<String> onDemandEvict, Long start) {
    log.info("Describing items in ${agentType}")

    Map<String, MutableCacheData> cachedApplications = MutableCacheData.mutableCacheMap()
    Map<String, MutableCacheData> cachedClusters = MutableCacheData.mutableCacheMap()
    Map<String, MutableCacheData> cachedServerGroups = MutableCacheData.mutableCacheMap()
    Map<String, MutableCacheData> cachedInstances = MutableCacheData.mutableCacheMap()
    Map<String, MutableCacheData> cachedLoadBalancers = MutableCacheData.mutableCacheMap()

    Map<String, Map<String, Event>> stateFulsetEvents = [:].withDefault { _ -> [:] }
    Map<String, Map<String, Event>> daemonsetEvents = [:].withDefault { _ -> [:] }

    try {
      namespaces.each { String namespace ->
        stateFulsetEvents[namespace] = credentials.apiAdaptor.getEvents(namespace, "V1beta1StatefulSet")
        daemonsetEvents[namespace] = credentials.apiAdaptor.getEvents(namespace, "V1beta1DaemonSet")
      }
    } catch (Exception e) {
      log.warn "Failure fetching events for all server groups in $namespaces", e
    }

    for (KubernetesController serverGroup: serverGroups) {
      if (!serverGroup.exists()) {
        continue
      }

      def onDemandData = onDemandKeep ? onDemandKeep[Keys.getServerGroupKey(accountName, serverGroup.namespace, serverGroup.name)] : null

      if (onDemandData && onDemandData.attributes.cacheTime >= start) {
        Map<String, List<CacheData>> cacheResults = objectMapper.readValue(onDemandData.attributes.cacheResults as String,
          new TypeReference<Map<String, List<MutableCacheData>>>() { })
        cache(cacheResults, Keys.Namespace.APPLICATIONS.ns, cachedApplications)
        cache(cacheResults, Keys.Namespace.CLUSTERS.ns, cachedClusters)
        cache(cacheResults, Keys.Namespace.SERVER_GROUPS.ns, cachedServerGroups)
        cache(cacheResults, Keys.Namespace.INSTANCES.ns, cachedInstances)
      } else {
        def serverGroupName = serverGroup.name
        def pods = loadPods(serverGroup)
        def names = Names.parseName(serverGroupName)
        def applicationName = names.app
        def clusterName = names.cluster
        def serverGroupKey = Keys.getServerGroupKey(accountName, serverGroup.namespace, serverGroupName)
        def applicationKey = Keys.getApplicationKey(applicationName)
        def clusterKey = Keys.getClusterKey(accountName, applicationName, category, clusterName)
        def instanceKeys = []
        def loadBalancerKeys = serverGroup.loadBalancers.collect({
          Keys.getLoadBalancerKey(accountName, serverGroup.namespace, it)
        })
        cachedApplications[applicationKey].with {
          attributes.name = applicationName
          relationships[Keys.Namespace.CLUSTERS.ns].add(clusterKey)
          relationships[Keys.Namespace.SERVER_GROUPS.ns].add(serverGroupKey)
          relationships[Keys.Namespace.LOAD_BALANCERS.ns].addAll(loadBalancerKeys)
        }

        cachedClusters[clusterKey].with {
          attributes.name = clusterName
          relationships[Keys.Namespace.APPLICATIONS.ns].add(applicationKey)
          relationships[Keys.Namespace.SERVER_GROUPS.ns].add(serverGroupKey)
          relationships[Keys.Namespace.LOAD_BALANCERS.ns].addAll(loadBalancerKeys)
        }
        pods?.getItems().forEach { pod ->
          def key = Keys.getInstanceKey(accountName, pod.metadata.namespace, pod.metadata.name)
          instanceKeys << key
          cachedInstances[key].with {
            relationships[Keys.Namespace.APPLICATIONS.ns].add(applicationKey)
            relationships[Keys.Namespace.CLUSTERS.ns].add(clusterKey)
            relationships[Keys.Namespace.SERVER_GROUPS.ns].add(serverGroupKey)
            relationships[Keys.Namespace.LOAD_BALANCERS.ns].addAll(loadBalancerKeys)
          }
        }
        boolean isDaemonset
        cachedServerGroups[serverGroupKey].with {
          def events = null
          attributes.name = serverGroupName

          if (serverGroup.statefulController instanceof V1beta1StatefulSet) {
            events = stateFulsetEvents[serverGroup.namespace][serverGroupName]
          } else if (serverGroup.daemonController instanceof V1beta1DaemonSet) {
            events = daemonsetEvents[serverGroup.namespace][serverGroupName]
            isDaemonset = true
          }
          attributes.serverGroup = new KubernetesV1ServerGroup(serverGroup.statefulController ?: serverGroup.daemonController, accountName, events)
          if (isDaemonset) {
            attributes.serverGroup.replicas = pods?.getItems().size()
          }

          relationships[Keys.Namespace.APPLICATIONS.ns].add(applicationKey)
          relationships[Keys.Namespace.CLUSTERS.ns].add(clusterKey)
          relationships[Keys.Namespace.INSTANCES.ns].addAll(instanceKeys)
          relationships[Keys.Namespace.LOAD_BALANCERS.ns].addAll(loadBalancerKeys)
        }
      }
    }

    log.info("Caching ${cachedApplications.size()} applications in ${agentType}")
    log.info("Caching ${cachedClusters.size()} clusters in ${agentType}")
    log.info("Caching ${cachedServerGroups.size()} server groups in ${agentType}")
    log.info("Caching ${cachedInstances.size()} instances in ${agentType}")

    new DefaultCacheResult([
      (Keys.Namespace.APPLICATIONS.ns): cachedApplications.values(),
      (Keys.Namespace.LOAD_BALANCERS.ns): cachedLoadBalancers.values(),
      (Keys.Namespace.CLUSTERS.ns): cachedClusters.values(),
      (Keys.Namespace.SERVER_GROUPS.ns): cachedServerGroups.values(),
      (Keys.Namespace.INSTANCES.ns): cachedInstances.values(),
      (Keys.Namespace.ON_DEMAND.ns): onDemandKeep.values()
    ],[
      (Keys.Namespace.ON_DEMAND.ns): onDemandEvict,
    ])

  }

  private static void cache(Map<String, List<CacheData>> cacheResults, String cacheNamespace, Map<String, CacheData> cacheDataById) {
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

  static class KubernetesController{
    def statefulController
    def daemonController

    String getName() {
      statefulController ? statefulController.metadata.name : daemonController.metadata.name
    }

    String getNamespace() {
      statefulController ? statefulController.metadata.namespace : daemonController.metadata.namespace
    }

    Map<String, String> getSelector() {
      statefulController ? statefulController.spec.selector?.matchLabels : daemonController.spec.selector?.matchLabels
    }

    boolean exists() {
      statefulController ?: daemonController
    }

    List<String> getLoadBalancers() {
      statefulController ? KubernetesUtil.getLoadBalancers(statefulController.spec?.template?.metadata?.labels ?: [:]) :
        KubernetesUtil.getLoadBalancers(daemonController.spec?.template?.metadata?.labels ?: [:])
    }
  }
}
