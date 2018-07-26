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
import io.fabric8.kubernetes.api.model.HorizontalPodAutoscaler
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.ReplicationController
import io.fabric8.kubernetes.api.model.apps.ReplicaSet

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.INFORMATIVE

@Slf4j
class KubernetesServerGroupCachingAgent extends KubernetesV1CachingAgent implements OnDemandAgent {
  final String category = 'serverGroup'

  final OnDemandMetricsSupport metricsSupport

  static final Set<AgentDataType> types = Collections.unmodifiableSet([
    AUTHORITATIVE.forType(Keys.Namespace.APPLICATIONS.ns),
    AUTHORITATIVE.forType(Keys.Namespace.CLUSTERS.ns),
    INFORMATIVE.forType(Keys.Namespace.LOAD_BALANCERS.ns),
    AUTHORITATIVE.forType(Keys.Namespace.SERVER_GROUPS.ns),
    INFORMATIVE.forType(Keys.Namespace.INSTANCES.ns),
  ] as Set)

  KubernetesServerGroupCachingAgent(KubernetesNamedAccountCredentials<KubernetesV1Credentials> namedAccountCredentials,
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

    ReplicationController replicationController = metricsSupport.readData {
      loadReplicationController(namespace, serverGroupName)
    }

    ReplicaSet replicaSet = metricsSupport.readData {
      loadReplicaSet(namespace, serverGroupName)
    }

    CacheResult result = metricsSupport.transformData {
      buildCacheResult([new ReplicaSetOrController(replicationController: replicationController, replicaSet: replicaSet)], [:], [], Long.MAX_VALUE)
    }

    def jsonResult = objectMapper.writeValueAsString(result.cacheResults)
    boolean isControllerSetCachingAgentType = false
    if (result.cacheResults.values().flatten().isEmpty()) {
      // Avoid writing an empty onDemand cache record (instead delete any that may have previously existed).
      providerCache.evictDeletedItems(Keys.Namespace.ON_DEMAND.ns, [Keys.getServerGroupKey(accountName, namespace, serverGroupName)])
      // Determine if this is the correct agent to delete cache which can avoid double deletion
      CacheData serverGroup = providerCache.get(Keys.Namespace.SERVER_GROUPS.ns, Keys.getServerGroupKey(accountName, namespace, serverGroupName))
      if (serverGroup) {
        String kind = serverGroup.attributes?.get("serverGroup")?.get("kind")
        if (kind == "StatefulSet" || kind == "DaemonSet") {
          isControllerSetCachingAgentType = true
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
    if (!isControllerSetCachingAgentType) {
      evictions = replicationController || replicaSet ? [:] : [
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

  @Override
  boolean handles(OnDemandAgent.OnDemandType type, String cloudProvider) {
    OnDemandAgent.OnDemandType.ServerGroup == type && cloudProvider == KubernetesCloudProvider.ID
  }

  List<ReplicationController> loadReplicationControllers() {
    namespaces.collect { String namespace ->
      credentials.apiAdaptor.getReplicationControllers(namespace)
    }.flatten()
  }

  List<ReplicaSet> loadReplicaSets() {
    namespaces.collect { String namespace ->
      credentials.apiAdaptor.getReplicaSets(namespace)
    }.flatten()
  }

  ReplicaSet loadReplicaSet(String namespace, String name) {
    credentials.apiAdaptor.getReplicaSet(namespace, name)
  }

  ReplicationController loadReplicationController(String namespace, String name) {
    credentials.apiAdaptor.getReplicationController(namespace, name)
  }

  List<Pod> loadPods(ReplicaSetOrController serverGroup) {
    credentials.apiAdaptor.getPods(serverGroup.namespace, serverGroup.selector)
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    reloadNamespaces()
    Long start = System.currentTimeMillis()
    List<ReplicationController> replicationControllerList = loadReplicationControllers()
    List<ReplicaSet> replicaSetList = loadReplicaSets()
    List<ReplicaSetOrController> serverGroups = (replicationControllerList.collect {
      it ? new ReplicaSetOrController(replicationController: it) : null
    } + replicaSetList.collect {
      it ? new ReplicaSetOrController(replicaSet: it) : null
    }) - null

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

  private CacheResult buildCacheResult(List<ReplicaSetOrController> serverGroups, Map<String, CacheData> onDemandKeep, List<String> onDemandEvict, Long start) {
    log.info("Describing items in ${agentType}")

    Map<String, MutableCacheData> cachedApplications = MutableCacheData.mutableCacheMap()
    Map<String, MutableCacheData> cachedClusters = MutableCacheData.mutableCacheMap()
    Map<String, MutableCacheData> cachedServerGroups = MutableCacheData.mutableCacheMap()
    Map<String, MutableCacheData> cachedInstances = MutableCacheData.mutableCacheMap()
    Map<String, MutableCacheData> cachedLoadBalancers = MutableCacheData.mutableCacheMap()

    // Map namespace -> name -> event
    Map<String, Map<String, Event>> rcEvents = [:].withDefault { _ -> [:] }
    Map<String, Map<String, Event>> rsEvents = [:].withDefault { _ -> [:] }
    try {
      namespaces.each { String namespace ->
        rcEvents[namespace] = credentials.apiAdaptor.getEvents(namespace, KubernetesUtil.DEPRECATED_SERVER_GROUP_KIND)
        rsEvents[namespace] = credentials.apiAdaptor.getEvents(namespace, KubernetesUtil.SERVER_GROUP_KIND)
      }
    } catch (Exception e) {
      log.warn "Failure fetching events for all server groups in $namespaces", e
    }

    // Map namespace -> name -> autoscaler
    Map<String, Map<String, HorizontalPodAutoscaler>> rcAutoscalers = [:].withDefault { _ -> [:] }
    Map<String, Map<String, HorizontalPodAutoscaler>> rsAutoscalers = [:].withDefault { _ -> [:] }
    Map<String, Map<String, HorizontalPodAutoscaler>> deployAutoscalers = [:].withDefault { _ -> [:] }
    try {
      namespaces.each { String namespace ->
        rcAutoscalers[namespace] = credentials.apiAdaptor.getAutoscalers(namespace, KubernetesUtil.DEPRECATED_SERVER_GROUP_KIND)
        rsAutoscalers[namespace] = credentials.apiAdaptor.getAutoscalers(namespace, KubernetesUtil.SERVER_GROUP_KIND)
        deployAutoscalers[namespace] = credentials.apiAdaptor.getAutoscalers(namespace, KubernetesUtil.DEPLOYMENT_KIND)
      }
    } catch (Exception e) {
      log.warn "Failure fetching autoscalers for all server groups in $namespaces", e
    }

    for (ReplicaSetOrController serverGroup: serverGroups) {
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

        pods?.forEach { pod ->
          def key = Keys.getInstanceKey(accountName, pod.metadata.namespace, pod.metadata.name)
          instanceKeys << key
          cachedInstances[key].with {
            relationships[Keys.Namespace.APPLICATIONS.ns].add(applicationKey)
            relationships[Keys.Namespace.CLUSTERS.ns].add(clusterKey)
            relationships[Keys.Namespace.SERVER_GROUPS.ns].add(serverGroupKey)
            relationships[Keys.Namespace.LOAD_BALANCERS.ns].addAll(loadBalancerKeys)
          }
        }

        loadBalancerKeys?.forEach { loadBalancerKey ->
          cachedLoadBalancers[loadBalancerKey].with {
            relationships[Keys.Namespace.SERVER_GROUPS.ns].add(serverGroupKey)
            relationships[Keys.Namespace.INSTANCES.ns].addAll(instanceKeys)
          }
        }

        cachedServerGroups[serverGroupKey].with {
          def events = null
          def autoscaler = null
          attributes.name = serverGroupName

          if (serverGroup.replicaSet) {
            if (credentials.apiAdaptor.hasDeployment(serverGroup.replicaSet)) {
              autoscaler = deployAutoscalers[serverGroup.namespace][clusterName]
            } else {
              autoscaler = rsAutoscalers[serverGroup.namespace][serverGroupName]
            }
            events = rsEvents[serverGroup.namespace][serverGroupName]
          } else {
            autoscaler = rcAutoscalers[serverGroup.namespace][serverGroupName]
            events = rcEvents[serverGroup.namespace][serverGroupName]
          }

          attributes.serverGroup = new KubernetesV1ServerGroup(serverGroup.replicaSet ?: serverGroup.replicationController, accountName, events, autoscaler)
          relationships[Keys.Namespace.APPLICATIONS.ns].add(applicationKey)
          relationships[Keys.Namespace.CLUSTERS.ns].add(clusterKey)
          relationships[Keys.Namespace.LOAD_BALANCERS.ns].addAll(loadBalancerKeys)
          relationships[Keys.Namespace.INSTANCES.ns].addAll(instanceKeys)
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

  class ReplicaSetOrController {
    ReplicationController replicationController
    ReplicaSet replicaSet

    String getName() {
      replicaSet ? replicaSet.metadata.name : replicationController.metadata.name
    }

    String getNamespace() {
      replicaSet ? replicaSet.metadata.namespace : replicationController.metadata.namespace
    }

    Map<String, String> getSelector() {
      replicaSet ? replicaSet.spec.selector?.matchLabels : replicationController.spec.selector
    }

    boolean exists() {
      replicaSet || replicationController
    }

    List<String> getLoadBalancers() {
      replicaSet ? KubernetesUtil.getLoadBalancers(replicaSet) : KubernetesUtil.getLoadBalancers(replicationController)
    }
  }
}
