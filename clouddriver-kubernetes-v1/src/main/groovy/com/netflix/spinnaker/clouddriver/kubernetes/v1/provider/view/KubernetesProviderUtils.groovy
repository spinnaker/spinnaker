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

package com.netflix.spinnaker.clouddriver.kubernetes.v1.provider.view

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.CacheFilter
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.kubernetes.v1.api.KubernetesApiConverter
import com.netflix.spinnaker.clouddriver.kubernetes.v1.caching.Keys
import com.netflix.spinnaker.clouddriver.kubernetes.v1.model.KubernetesDeploymentStatus
import com.netflix.spinnaker.clouddriver.kubernetes.v1.model.KubernetesV1Instance
import com.netflix.spinnaker.clouddriver.kubernetes.v1.model.KubernetesV1ServerGroup
import io.fabric8.kubernetes.api.model.apps.Deployment

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

  static KubernetesV1Instance convertInstance(ObjectMapper objectMapper, CacheData instanceData) {
    def instance = objectMapper.convertValue(instanceData.attributes.instance, KubernetesV1Instance)
    def loadBalancers = instanceData.relationships[Keys.Namespace.LOAD_BALANCERS.ns].collect {
      Keys.parse(it).name
    }
    instance.loadBalancers = loadBalancers

    return instance
  }

  static Map<String, Set<KubernetesV1Instance>> controllerToInstanceMap(ObjectMapper objectMapper, Collection<CacheData> instances) {
    Map<String, Set<KubernetesV1Instance>> instanceMap = [:].withDefault { _ -> [] as Set }
    instances?.forEach {
      def instance = convertInstance(objectMapper, it)
      instanceMap[instance.controllerName].add(instance)
    }
    return instanceMap
  }

  static KubernetesV1ServerGroup serverGroupFromCacheData(ObjectMapper objectMapper, CacheData cacheData, Set<KubernetesV1Instance> instances, Deployment deployment) {
    KubernetesV1ServerGroup serverGroup = objectMapper.convertValue(cacheData.attributes.serverGroup, KubernetesV1ServerGroup)
    serverGroup.instances = instances
    serverGroup.deploymentStatus = deployment ? new KubernetesDeploymentStatus(deployment) : null
    serverGroup.deployDescription.deployment = KubernetesApiConverter.fromDeployment(deployment)
    return serverGroup
  }
}
