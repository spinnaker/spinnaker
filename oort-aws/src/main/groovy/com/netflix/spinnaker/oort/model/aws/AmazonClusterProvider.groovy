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

package com.netflix.spinnaker.oort.model.aws

import com.netflix.spinnaker.oort.data.aws.Keys
import com.netflix.spinnaker.oort.model.ClusterProvider
import groovy.transform.CompileStatic
import org.apache.directmemory.cache.CacheService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@CompileStatic
@Component
class AmazonClusterProvider implements ClusterProvider<AmazonCluster> {
  @Autowired
  CacheService<String, Object> cacheService

  @Override
  Map<String, Set<AmazonCluster>> getClusters() {
    def keys = cacheService.map.keySet()
    def clusters = (List<AmazonCluster>) keys.findAll { it.startsWith("clusters") }.collect { cacheService.retrieve(it) }
    getClustersWithServerGroups keys, clusters
  }

  @Override
  Map<String, Set<AmazonCluster>> getClusters(String application) {
    def keys = cacheService.map.keySet()
    def clusters = (List<AmazonCluster>) keys.findAll { it.startsWith("clusters:${application}") }.collect { (AmazonCluster) cacheService.retrieve(it) }
    getClustersWithServerGroups keys, clusters
  }

  @Override
  Set<AmazonCluster> getClusters(String application, String accountName) {
    def keys = cacheService.map.keySet()
    def clusters = keys.findAll { it.startsWith("clusters:${application}:${accountName}") }.collect { (AmazonCluster) cacheService.retrieve(it) }
    (Set<AmazonCluster>) getClustersWithServerGroups(keys, clusters)?.get(accountName)
  }

  @Override
  AmazonCluster getCluster(String application, String account, String name) {
    def keys = cacheService.map.keySet()
    def cluster = (AmazonCluster) cacheService.retrieve(Keys.getClusterKey(name, application, account))
    if (cluster) {
      def withServerGroups = getClustersWithServerGroups(keys, [cluster])
      cluster = withServerGroups[account]?.getAt(0)
    }
    cluster
  }

  private Map<String, Set<AmazonCluster>> getClustersWithServerGroups(Set<String> keys, List<AmazonCluster> clusters) {
    Map<String, Set<AmazonCluster>> result = new HashMap<>()
    clusters.collect { Thread.startDaemon(clusterFiller.curry(keys, it)) }*.join()
    for (cluster in clusters) {
      if (!result.containsKey(cluster.accountName)) {
        result[cluster.accountName] = new HashSet<>()
      }
      result[cluster.accountName] << cluster
    }
    result
  }

  final Closure clusterFiller = { Set<String> keys, AmazonCluster cluster ->
    def serverGroups = keys.findAll { it.startsWith("serverGroups:${cluster.name}:${cluster.accountName}") }
    if (!serverGroups) return
    for (loadBalancer in cluster.loadBalancers) {
      def elb = keys.find { it == "loadBalancers:${loadBalancer.region}:${loadBalancer.name}" }
      if (elb) {
        loadBalancer.elb = cacheService.retrieve(elb)
      }
    }
    cluster.serverGroups = serverGroups.collect {
      def serverGroup = (AmazonServerGroup)cacheService.retrieve(it)
      for (AmazonInstance instance in (Set<AmazonInstance>)serverGroup.instances) {
        def amazonInstance = cacheService.retrieve(Keys.getInstanceKey(instance.name, serverGroup.region))
        if (amazonInstance) {
          instance.instance = amazonInstance
        }
      }
      serverGroup
    } as Set
  }
}
