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

import com.netflix.spinnaker.oort.model.ClusterProvider
import groovy.transform.CompileStatic
import org.apache.directmemory.cache.CacheService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@CompileStatic
@Component
class AmazonClusterProvider implements ClusterProvider<AmazonCluster> {
  @Autowired
  CacheService<String, AmazonApplication> applicationCacheService

  @Autowired
  CacheService<String, AmazonCluster> clusterCacheService

  @Override
  Map<String, Set<AmazonCluster>> getClusters() {
    Map<String, Set<AmazonCluster>> result = new HashMap<>()

    for (key in clusterCacheService.map.keySet()) {
      def cluster = clusterCacheService.retrieve(key)
      if (!result.containsKey(cluster.accountName)) {
        result[cluster.accountName] = new HashSet<>()
      }
      result[cluster.accountName] << cluster
    }
    result
  }

  @Override
  Map<String, Set<AmazonCluster>> getClusters(String application) {
    Map<String, Set<AmazonCluster>> result = new HashMap<>()
    def app = applicationCacheService.retrieve(application)
    if (!app) return result
    for (e in app.clusterNames) {
      def entry = (Map.Entry<String, Set<String>>)e
      def account = entry.key
      def clusterNames = entry.value
      Set<AmazonCluster> clusters = new HashSet<>()
      for (clusterName in clusterNames) {
        def cluster = clusterCacheService.retrieve(clusterName)
        if (cluster) {
          clusters << cluster
        }
      }
      result.put account, Collections.unmodifiableSet(clusters)
    }
    result
  }

  @Override
  Set<AmazonCluster> getClusters(String application, String accountName) {
    Set<AmazonCluster> clusters = new HashSet<>()
    def app = applicationCacheService.retrieve(application)
    if (!app) {
      return clusters
    }
    if (app.clusterNames.containsKey(accountName)) {
      for (String clusterName in app.clusterNames[accountName]) {
        def key = "${accountName}:${clusterName}".toString()
        def cluster = clusterCacheService.retrieve(key)
        if (cluster) {

          clusters << clusterCacheService.retrieve(key)
        }
      }
    }
    Collections.unmodifiableSet(clusters)
  }

  @Override
  AmazonCluster getCluster(String account, String name) {
    def key = "${account}:${name}".toString()
    clusterCacheService.getPointer(key) ? clusterCacheService.retrieve(key) : null
  }
}
