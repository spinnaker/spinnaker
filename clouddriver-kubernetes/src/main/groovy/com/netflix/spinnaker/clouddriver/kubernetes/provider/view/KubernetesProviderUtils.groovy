/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.provider.view

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.CacheFilter
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.kubernetes.cache.Keys
import com.netflix.spinnaker.clouddriver.kubernetes.model.KubernetesInstance
import com.netflix.spinnaker.clouddriver.kubernetes.model.KubernetesProcess
import io.fabric8.kubernetes.api.model.Pod

class KubernetesProviderUtils {
  static Set<CacheData> getAllMatchingKeyPattern(Cache cacheView, String namespace, String pattern) {
    loadResults(cacheView, namespace, cacheView.filterIdentifiers(namespace, pattern))
  }

  private static Set<CacheData> loadResults(Cache cacheView, String namespace, Collection<String> identifiers) {
    cacheView.getAll(namespace, identifiers, RelationshipCacheFilter.none())
  }

  static Collection<CacheData> resolveRelationshipData(Cache cacheView, CacheData source, String relationship) {
    resolveRelationshipData(cacheView, source, relationship) { true }
  }

  static Collection<CacheData> resolveRelationshipData(Cache cacheView, CacheData source, String relationship, Closure<Boolean> relFilter) {
    Collection<String> filteredRelationships = source?.relationships[relationship]?.findAll(relFilter)
    filteredRelationships ? cacheView.getAll(relationship, filteredRelationships) : []
  }

  static Collection<CacheData> resolveRelationshipDataForCollection(Cache cacheView, Collection<CacheData> sources, String relationship, CacheFilter cacheFilter = null) {
    Set<String> relationships = sources.findResults { it.relationships[relationship]?: [] }.flatten()
    relationships ? cacheView.getAll(relationship, relationships, cacheFilter) : []
  }

  static Map<String, Set<KubernetesProcess>> jobToProcessMap(ObjectMapper objectMapper, Collection<CacheData> processes) {
    Map<String, Set<KubernetesProcess>> processMap = [:].withDefault { _ -> [] as Set }
    processes?.forEach {
      def pod = objectMapper.convertValue(it.attributes.pod, Pod)
      def loadBalancers = it.relationships[Keys.Namespace.LOAD_BALANCERS.ns].collect {
        Keys.parse(it).name
      }

      KubernetesProcess process = new KubernetesProcess(pod, loadBalancers)
      processMap[process.jobId].add(process)
    }
    return processMap
  }

  static Map<String, Set<KubernetesInstance>> serverGroupToInstanceMap(ObjectMapper objectMapper, Collection<CacheData> instances) {
    Map<String, Set<KubernetesInstance>> instanceMap = [:].withDefault { _ -> [] as Set }
    instances?.forEach {
      def pod = objectMapper.convertValue(it.attributes.pod, Pod)
      def loadBalancers = it.relationships[Keys.Namespace.LOAD_BALANCERS.ns].collect {
        Keys.parse(it).name
      }

      KubernetesInstance instance = new KubernetesInstance(pod, loadBalancers)
      instanceMap[instance.serverGroupName].add(instance)
    }
    return instanceMap
  }
}
