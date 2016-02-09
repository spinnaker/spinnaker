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

package com.netflix.spinnaker.clouddriver.kubernetes.provider.agent

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.frigga.Names
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.*
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent
import com.netflix.spinnaker.clouddriver.cache.OnDemandMetricsSupport
import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesCloudProvider
import com.netflix.spinnaker.clouddriver.kubernetes.cache.Keys
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.KubernetesUtil
import com.netflix.spinnaker.clouddriver.kubernetes.provider.KubernetesProvider
import com.netflix.spinnaker.clouddriver.kubernetes.provider.view.MutableCacheData
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesCredentials
import groovy.util.logging.Slf4j
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.ReplicationController

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.INFORMATIVE

@Slf4j
class KubernetesServerGroupCachingAgent implements CachingAgent, OnDemandAgent, AccountAware {

  @Deprecated
  private static final String LEGACY_ON_DEMAND_TYPE = 'KubernetesCluster'

  private static final String ON_DEMAND_TYPE = 'Cluster'

  final KubernetesCloudProvider kubernetesCloudProvider
  final String accountName
  final String namespace
  final KubernetesCredentials credentials
  final ObjectMapper objectMapper

  final OnDemandMetricsSupport metricsSupport

  static final Set<AgentDataType> types = Collections.unmodifiableSet([
    INFORMATIVE.forType(Keys.Namespace.APPLICATIONS.ns),
    INFORMATIVE.forType(Keys.Namespace.CLUSTERS.ns),
    AUTHORITATIVE.forType(Keys.Namespace.SERVER_GROUPS.ns),
    AUTHORITATIVE.forType(Keys.Namespace.INSTANCES.ns),
  ] as Set)

  KubernetesServerGroupCachingAgent(KubernetesCloudProvider kubernetesCloudProvider,
                                    String accountName,
                                    KubernetesCredentials credentials,
                                    String namespace,
                                    ObjectMapper objectMapper,
                                    Registry registry) {
    this.kubernetesCloudProvider = kubernetesCloudProvider
    this.accountName = accountName
    this.credentials = credentials
    this.objectMapper = objectMapper
    this.namespace = namespace
    this.metricsSupport = new OnDemandMetricsSupport(registry, this, kubernetesCloudProvider.id + ":" + ON_DEMAND_TYPE)
  }

  @Override
  String getProviderName() {
    KubernetesProvider.PROVIDER_NAME
  }

  @Override
  String getAgentType() {
    "${accountName}/${namespace}/${KubernetesServerGroupCachingAgent.simpleName}"
  }

  @Override
  String getAccountName() {
    accountName
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
    if (data.account != accountName) {
      return null
    }

    if (data.namespace != namespace) {
      return null
    }

    List<ReplicationController> replicationControllerList = metricsSupport.readData {
      loadReplicationControllers()
    }

    CacheResult result = metricsSupport.transformData {
      buildCacheResult(replicationControllerList)
    }

    new OnDemandAgent.OnDemandResult(
      sourceAgentType: getAgentType(),
      authoritativeTypes: [Keys.Namespace.SERVER_GROUPS.ns],
      cacheResult: result
    )
  }

  @Override
  Collection<Map> pendingOnDemandRequests(ProviderCache providerCache) {
    return []
  }

  @Override
  boolean handles(String type) {
    type == LEGACY_ON_DEMAND_TYPE
  }

  @Override
  boolean handles(String type, String cloudProvider) {
    type == ON_DEMAND_TYPE && cloudProvider == kubernetesCloudProvider.id
  }

  List<ReplicationController> loadReplicationControllers() {
    credentials.apiAdaptor.getReplicationControllers(namespace)
  }

  List<Pod> loadPods(String replicationControllerName) {
    credentials.apiAdaptor.getPods(namespace, replicationControllerName)
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    List<ReplicationController> replicationControllerList = loadReplicationControllers()

    buildCacheResult(replicationControllerList)
  }

  private CacheResult buildCacheResult(List<ReplicationController> replicationControllers) {
    log.info("Describing items in ${agentType}")

    Map<String, MutableCacheData> cachedApplications = MutableCacheData.mutableCacheMap()
    Map<String, MutableCacheData> cachedClusters = MutableCacheData.mutableCacheMap()
    Map<String, MutableCacheData> cachedServerGroups = MutableCacheData.mutableCacheMap()
    Map<String, MutableCacheData> cachedInstances = MutableCacheData.mutableCacheMap()

    replicationControllers.forEach { ReplicationController replicationController ->
      def replicationControllerName = replicationController.metadata.name
      def pods = loadPods(replicationControllerName)
      def names = Names.parseName(replicationControllerName)
      def applicationName = names.app
      def clusterName = names.cluster

      def serverGroupKey = Keys.getServerGroupKey(accountName, namespace, replicationControllerName)
      def applicationKey = Keys.getApplicationKey(applicationName)
      def clusterKey = Keys.getClusterKey(accountName, applicationName, clusterName)
      def instanceKeys = []
      def loadBalancerKeys = KubernetesUtil.getDescriptionLoadBalancers(replicationController).collect({
        Keys.getLoadBalancerKey(accountName, namespace, it)
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

      pods.forEach { pod ->
        def key = Keys.getInstanceKey(accountName, namespace, replicationControllerName, pod.metadata.name)
        instanceKeys << key
        cachedInstances[key].with {
          attributes.name = pod.metadata.name
          attributes.pod = pod
          relationships[Keys.Namespace.APPLICATIONS.ns].add(applicationKey)
          relationships[Keys.Namespace.CLUSTERS.ns].add(clusterKey)
          relationships[Keys.Namespace.SERVER_GROUPS.ns].add(serverGroupKey)
          relationships[Keys.Namespace.LOAD_BALANCERS.ns].addAll(loadBalancerKeys)
        }
      }

      cachedServerGroups[serverGroupKey].with {
        attributes.name = replicationControllerName
        attributes.replicationController = replicationController
        relationships[Keys.Namespace.APPLICATIONS.ns].add(applicationKey)
        relationships[Keys.Namespace.CLUSTERS.ns].add(clusterKey)
        relationships[Keys.Namespace.LOAD_BALANCERS.ns].addAll(loadBalancerKeys)
        relationships[Keys.Namespace.INSTANCES.ns].addAll(instanceKeys)
      }

      null
    }

    log.info("Caching ${cachedApplications.size()} applications in ${agentType}")
    log.info("Caching ${cachedClusters.size()} clusters in ${agentType}")
    log.info("Caching ${cachedServerGroups.size()} server groups in ${agentType}")
    log.info("Caching ${cachedInstances.size()} instances in ${agentType}")

    new DefaultCacheResult([
      (Keys.Namespace.APPLICATIONS.ns): cachedApplications.values(),
      (Keys.Namespace.CLUSTERS.ns): cachedClusters.values(),
      (Keys.Namespace.SERVER_GROUPS.ns): cachedServerGroups.values(),
      (Keys.Namespace.INSTANCES.ns): cachedInstances.values(),
    ])
  }
}
