/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.oort.aws.provider.view

import com.netflix.frigga.Names
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.CacheFilter
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.oort.aws.data.Keys
import com.netflix.spinnaker.oort.aws.model.AmazonInstance
import com.netflix.spinnaker.oort.aws.model.AmazonServerGroup
import com.netflix.spinnaker.oort.aws.provider.AwsProvider
import com.netflix.spinnaker.oort.model.LoadBalancerProvider
import com.netflix.spinnaker.oort.aws.model.AmazonLoadBalancer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import static com.netflix.spinnaker.oort.aws.data.Keys.Namespace.APPLICATIONS
import static com.netflix.spinnaker.oort.aws.data.Keys.Namespace.CLUSTERS
import static com.netflix.spinnaker.oort.aws.data.Keys.Namespace.HEALTH
import static com.netflix.spinnaker.oort.aws.data.Keys.Namespace.INSTANCES
import static com.netflix.spinnaker.oort.aws.data.Keys.Namespace.LOAD_BALANCERS
import static com.netflix.spinnaker.oort.aws.data.Keys.Namespace.SERVER_GROUPS

@Component
class CatsLoadBalancerProvider implements LoadBalancerProvider<AmazonLoadBalancer> {

  private final Cache cacheView
  private final AwsProvider awsProvider

  @Autowired
  public CatsLoadBalancerProvider(Cache cacheView, AwsProvider awsProvider) {
    this.cacheView = cacheView
    this.awsProvider = awsProvider
  }

  @Override
  Map<String, Set<AmazonLoadBalancer>> getLoadBalancers() {
    Map<String, Set<AmazonLoadBalancer>> partitionedLb = [:].withDefault { new HashSet<AmazonLoadBalancer>() }
    Collection<AmazonLoadBalancer> allLb = cacheView.getAll(LOAD_BALANCERS.ns).findResults(this.&translate)
    for (AmazonLoadBalancer lb : allLb) {
      partitionedLb[lb.account].add(lb)
    }
  }

  AmazonLoadBalancer translate(CacheData cacheData) {
    Map<String, String> keyParts = Keys.parse(cacheData.id)
    def lb = new AmazonLoadBalancer(keyParts.loadBalancer, keyParts.region)
    lb.account = keyParts.account
    lb.elb = cacheData.attributes
    lb.serverGroups = cacheData.relationships[SERVER_GROUPS.ns]?.collect {
      Map<String, String> sgParts = Keys.parse(it)
      sgParts.cluster
    } ?: []
  }

  Collection<CacheData> resolveRelationshipData(CacheData source, String relationship) {
    source.relationships[relationship] ? cacheView.getAll(relationship, source.relationships[relationship]) : []
  }

  private Collection<CacheData> resolveRelationshipDataForCollection(Collection<CacheData> sources, String relationship, CacheFilter cacheFilter = null) {
    Set<String> relationships = sources.findResults { it.relationships[relationship]?: [] }.flatten()
    relationships ? cacheView.getAll(relationship, relationships, cacheFilter) : []
  }

  @Override
  Set<AmazonLoadBalancer> getLoadBalancers(String account) {
    def searchKey = Keys.getLoadBalancerKey('*', account, '*', null)
    def filteredIds = cacheView.filterIdentifiers(LOAD_BALANCERS.ns, searchKey)
    cacheView.getAll(LOAD_BALANCERS.ns, filteredIds).findResults(this.&translate) as Set<AmazonLoadBalancer>
  }

  @Override
  Set<AmazonLoadBalancer> getLoadBalancers(String account, String cluster) {
    Names names = Names.parseName(cluster)
    CacheData clusterData = cacheView.get(CLUSTERS.ns, Keys.getClusterKey(cluster, names.app, account))

    resolveRelationshipData(clusterData, LOAD_BALANCERS.ns).findResults(this.&translate)
  }

  @Override
  Set<AmazonLoadBalancer> getLoadBalancers(String account, String cluster, String type) {
    getLoadBalancers(account, cluster)
  }

  @Override
  Set<AmazonLoadBalancer> getLoadBalancer(String account, String cluster, String type, String loadBalancerName) {
    getLoadBalancers(account, cluster).findAll { it.name == loadBalancerName }
  }

  @Override
  AmazonLoadBalancer getLoadBalancer(String account, String cluster, String type, String loadBalancerName, String region) {
    def searchKey = Keys.getLoadBalancerKey(loadBalancerName, account, region, null) + '*'
    def lbs = cacheView.filterIdentifiers(LOAD_BALANCERS.ns, searchKey)
    def keys = lbs.findAll {
      Keys.parse(it).loadBalancer == loadBalancerName
    }

    def candidates = cacheView.getAll(LOAD_BALANCERS.ns, keys).findResults(this.&translate)
    if (candidates.isEmpty()) {
      null
    } else {
      candidates.first()
    }
  }

  @Override
  Set<AmazonLoadBalancer> getApplicationLoadBalancers(String applicationName) {

    Map<String, AmazonServerGroup> serverGroups
    Set<String> keys = []

    CacheData application = cacheView.get(APPLICATIONS.ns, Keys.getApplicationKey(applicationName))

    def applicationServerGroups = application ? resolveRelationshipData(application, SERVER_GROUPS.ns) : []
    applicationServerGroups.each { CacheData serverGroup ->
      keys.addAll(serverGroup.relationships[LOAD_BALANCERS.ns])
    }

    def nameMatches = cacheView.filterIdentifiers(LOAD_BALANCERS.ns, '*' + applicationName + '-*')

    keys.addAll(nameMatches)

    def allLoadBalancers = cacheView.getAll(LOAD_BALANCERS.ns, keys)

    def allInstances = resolveRelationshipDataForCollection(allLoadBalancers, INSTANCES.ns)
    def allServerGroups = resolveRelationshipDataForCollection(allLoadBalancers, SERVER_GROUPS.ns)

    Map<String, AmazonInstance> instances = translateInstances(allInstances)

    serverGroups = translateServerGroups(allServerGroups, instances)

    translateLoadBalancers(allLoadBalancers, serverGroups)
  }

  private static Set<AmazonLoadBalancer> translateLoadBalancers(Collection<CacheData> loadBalancerData, Map<String, AmazonServerGroup> serverGroups) {
    Set<AmazonLoadBalancer> loadBalancers = loadBalancerData.collect { loadBalancerEntry ->
      Map<String, String> loadBalancerKey = Keys.parse(loadBalancerEntry.id)
      AmazonLoadBalancer loadBalancer = new AmazonLoadBalancer(loadBalancerKey.loadBalancer, loadBalancerKey.account, loadBalancerKey.region)
      loadBalancer.putAll(loadBalancerEntry.attributes)
      loadBalancer.instances = loadBalancer.instances.findResults { it.instanceId }
      loadBalancer.vpcId = loadBalancerKey.vpcId
      loadBalancer.account = loadBalancerKey.account
      def lbServerGroups = loadBalancerEntry.relationships[SERVER_GROUPS.ns]?.findResults { serverGroups.get(it) } ?: []
      lbServerGroups.each { serverGroup ->
        loadBalancer.serverGroups << [
          name: serverGroup.name,
          instances: serverGroup.instances ? serverGroup.instances.collect { instance ->
            def health = instance.health.find { it.loadBalancerName == loadBalancer.name }
            [
              id: instance.name,
              zone: instance.zone,
              health:
                [
                  state: health.state,
                  reasonCode: health.reasonCode,
                  description: health.description
                ]
            ]
          } : [],
          detachedInstances: serverGroup.detachedInstances

        ]
      }
      loadBalancer
    }

    loadBalancers
  }

  private static Map<String, AmazonServerGroup> translateServerGroups(Collection<CacheData> serverGroupData, Map<String, AmazonInstance> instances) {
    Map<String, AmazonServerGroup> serverGroups = serverGroupData.collectEntries { serverGroupEntry ->
      Map<String, String> serverGroupKey = Keys.parse(serverGroupEntry.id)

      def serverGroup = new AmazonServerGroup(serverGroupKey.serverGroup, 'aws', serverGroupKey.region)
      serverGroup.instances = serverGroupEntry.relationships[INSTANCES.ns]?.findResults { instances.get(it) }
      serverGroup.detachedInstances = serverGroupEntry.relationships[INSTANCES.ns]?.findResults { instances.get(it) ? null : Keys.parse(it).instanceId }
      [(serverGroupEntry.id) : serverGroup]
    }

    serverGroups
  }

  private Map<String, AmazonInstance> translateInstances(Collection<CacheData> instanceData) {
    Map<String, AmazonInstance> instances = instanceData.collectEntries { instanceEntry ->
      AmazonInstance instance = new AmazonInstance(instanceEntry.attributes.instanceId.toString())
      instance.zone = instanceEntry.attributes.placement?.availabilityZone
      [(instanceEntry.id): instance]
    }
    addHealthToInstances(instanceData, instances)

    instances
  }

  private void addHealthToInstances(Collection<CacheData> instanceData, Map<String, AmazonInstance> instances) {
    // Health will only be picked up when the healthAgent's healthId contains 'load-balancer'
    Map<String, String> healthKeysToInstance = [:]
    def loadBalancingHealthAgents = awsProvider.healthAgents.findAll { it.healthId.contains('load-balancer')}

    instanceData.each { instanceEntry ->
      Map<String, String> instanceKey = Keys.parse(instanceEntry.id)
      loadBalancingHealthAgents.each {
        def key = Keys.getInstanceHealthKey(instanceKey.instanceId, instanceKey.account, instanceKey.region, it.healthId)
        healthKeysToInstance.put(key, instanceEntry.id)
      }
    }

    Collection<CacheData> healths = cacheView.getAll(HEALTH.ns, healthKeysToInstance.keySet(), RelationshipCacheFilter.none())
    healths.findAll { it.attributes.type == 'LoadBalancer' && it.attributes.loadBalancers }.each { healthEntry ->
      def instanceId = healthKeysToInstance.get(healthEntry.id)
      def interestingHealth = healthEntry.attributes.loadBalancers
      instances[instanceId].health.addAll(interestingHealth.collect {
        [
          loadBalancerName: it.loadBalancerName,
          state: it.state,
          reasonCode: it.reasonCode,
          description: it.description
        ]
      })
    }

  }
}
