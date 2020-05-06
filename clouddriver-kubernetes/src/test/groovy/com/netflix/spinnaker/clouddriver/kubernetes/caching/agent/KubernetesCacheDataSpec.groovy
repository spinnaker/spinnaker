/*
 * Copyright 2019 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.caching.agent

import com.google.common.collect.ImmutableMap
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.clouddriver.kubernetes.caching.Keys
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind
import spock.lang.Specification

class KubernetesCacheDataSpec extends Specification {
  private static final String ACCOUNT = "my-account"
  private static final String NAMESPACE = "my-namespace"
  private static final Keys.CacheKey REPLICA_SET_KEY = new Keys.InfrastructureCacheKey(KubernetesKind.REPLICA_SET, ACCOUNT, NAMESPACE, "testing")
  private static final Keys.CacheKey OTHER_REPLICA_SET_KEY = new Keys.InfrastructureCacheKey(KubernetesKind.REPLICA_SET, ACCOUNT, NAMESPACE, "other-key")
  private static final Keys.CacheKey APPLICATION_KEY = new Keys.ApplicationCacheKey("app")

  def "returns an empty collection when no entries are added"() {
    given:
    KubernetesCacheData kubernetesCacheData = new KubernetesCacheData()

    when:
    Collection<CacheData> cacheData = kubernetesCacheData.toCacheData()

    then:
    cacheData.isEmpty()
  }

  def "correctly caches a single item"() {
    given:
    KubernetesCacheData kubernetesCacheData = new KubernetesCacheData()
    Map<String, Object> attributes = new ImmutableMap.Builder<String, Object>().put("key", "value").build();

    when:
    kubernetesCacheData.addItem(REPLICA_SET_KEY, attributes)
    Collection<CacheData> cacheData = kubernetesCacheData.toCacheData()

    then:
    cacheData.size() == 1

    def optionalData = cacheData.stream().filter({cd -> cd.id == REPLICA_SET_KEY.toString()}).findFirst()
    optionalData.isPresent()
    optionalData.get().attributes == attributes

    // Ensure that we have explicitly added an empty list of relationships for "sticky" kinds
    def relationships = optionalData.get().getRelationships()
    KubernetesCacheDataConverter.getStickyKinds().forEach({kind ->
      relationships.get(kind.toString()) == []
    })
  }

  def "correctly merges new attributes when adding the same key twice"() {
    given:
    KubernetesCacheData kubernetesCacheData = new KubernetesCacheData()
    Map<String, Object> oldAttributes = new ImmutableMap.Builder<String, Object>()
      .put("key1", "oldvalue1")
      .put("key2", "oldvalue2")
      .build();
    Map<String, Object> newAttributes = new ImmutableMap.Builder<String, Object>()
      .put("key2", "newvalue2")
      .put("key3", "newvalue3")
      .build();

    when:
    kubernetesCacheData.addItem(REPLICA_SET_KEY, oldAttributes)
    kubernetesCacheData.addItem(REPLICA_SET_KEY, newAttributes)
    Collection<CacheData> cacheData = kubernetesCacheData.toCacheData()

    then:
    cacheData.size() == 1

    def optionalData = cacheData.stream().filter({cd -> cd.id == REPLICA_SET_KEY.toString()}).findFirst()
    optionalData.isPresent()
    optionalData.get().attributes == [
      "key1" : "oldvalue1",
      "key2" : "newvalue2",
      "key3" : "newvalue3"
    ]
  }

  def "correctly creates a bidirectional application relationship"() {
    given:
    KubernetesCacheData kubernetesCacheData = new KubernetesCacheData()
    Map<String, Object> attributes = new ImmutableMap.Builder<String, Object>().put("key", "value").build();

    when:
    kubernetesCacheData.addItem(REPLICA_SET_KEY, attributes)
    kubernetesCacheData.addRelationship(REPLICA_SET_KEY, APPLICATION_KEY)
    Collection<CacheData> cacheData = kubernetesCacheData.toCacheData()

    then:
    cacheData.size() == 2

    def replicaSet = cacheData.stream().filter({cd -> cd.id == REPLICA_SET_KEY.toString()}).findFirst().get()
    replicaSet.attributes == attributes
    def replicaSetRelationships = replicaSet.relationships.get("applications") as Collection<String>
    replicaSetRelationships.size() == 1
    replicaSetRelationships.contains(APPLICATION_KEY.toString())

    def application = cacheData.stream().filter({cd -> cd.id == APPLICATION_KEY.toString()}).findFirst().get()
    // Ensure that the default "name" key was added to the logical key
    application.attributes.get("name") == "app"
    def applicationRelationships = application.relationships.get("replicaSet") as Collection<String>
    applicationRelationships.size() == 1
    applicationRelationships.contains(REPLICA_SET_KEY.toString())
  }

  def "correctly groups cache data items"() {
    given:
    KubernetesCacheData kubernetesCacheData = new KubernetesCacheData()
    Map<String, Object> attributes = new ImmutableMap.Builder<String, Object>().put("key", "value").build();
    Map<String, Object> otherAttributes = new ImmutableMap.Builder<String, Object>().put("otherKey", "otherValue").build();

    when:
    kubernetesCacheData.addItem(REPLICA_SET_KEY, attributes)
    kubernetesCacheData.addItem(OTHER_REPLICA_SET_KEY, otherAttributes)
    kubernetesCacheData.addRelationship(REPLICA_SET_KEY, APPLICATION_KEY)
    Map<String, Collection<CacheData>> cacheData = kubernetesCacheData.toStratifiedCacheData()
    def replicaSetData = cacheData.get(REPLICA_SET_KEY.getGroup())
    def applicationData = cacheData.get(APPLICATION_KEY.getGroup())

    then:
    replicaSetData.size() == 2
    def replicaSet = replicaSetData.stream().filter({cd -> cd.id == REPLICA_SET_KEY.toString()}).findFirst().get()
    replicaSet.attributes == attributes
    def otherReplicaSet = replicaSetData.stream().filter({cd -> cd.id == OTHER_REPLICA_SET_KEY.toString()}).findFirst().get()
    otherReplicaSet.attributes == otherAttributes

    def application = applicationData.stream().filter({cd -> cd.id == APPLICATION_KEY.toString()}).findFirst().get()
    application.attributes.get("name") == "app"
  }

  def "omits infrastructure keys without attribtues from returned cache data"() {
    given:
    KubernetesCacheData kubernetesCacheData = new KubernetesCacheData()
    Map<String, Object> attributes = new ImmutableMap.Builder<String, Object>().put("key", "value").build();
    Map<String, Object> emptyAttributes = new ImmutableMap.Builder<String, Object>().build();

    when:
    kubernetesCacheData.addItem(REPLICA_SET_KEY, attributes)
    kubernetesCacheData.addItem(OTHER_REPLICA_SET_KEY, emptyAttributes)
    Collection<CacheData> cacheData = kubernetesCacheData.toCacheData()
    Map<String, Collection<CacheData>> stratifiedCacheData = kubernetesCacheData.toStratifiedCacheData()

    then:
    cacheData.size() == 1
    def replicaSet = cacheData.stream().filter({cd -> cd.id == REPLICA_SET_KEY.toString()}).findFirst().get()
    replicaSet.attributes == attributes

    def replicaSetData = stratifiedCacheData.get(REPLICA_SET_KEY.getGroup())
    replicaSetData.size() == 1
    def groupedReplicaSet = replicaSetData.stream().filter({cd -> cd.id == REPLICA_SET_KEY.toString()}).findFirst().get()
    groupedReplicaSet.attributes == attributes
  }
}
