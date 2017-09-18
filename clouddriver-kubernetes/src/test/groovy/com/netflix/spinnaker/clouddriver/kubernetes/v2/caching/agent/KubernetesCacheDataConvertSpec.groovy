/*
 * Copyright 2017 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.agent

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.Keys
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesApiVersion
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesKind
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesManifest
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesManifestAnnotater
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesManifestSpinnakerRelationships
import io.kubernetes.client.models.V1beta1ReplicaSet
import org.apache.commons.lang3.tuple.Triple
import org.yaml.snakeyaml.Yaml
import spock.lang.Specification
import spock.lang.Unroll

class KubernetesCacheDataConvertSpec extends Specification {
  def mapper = new ObjectMapper()
  def yaml = new Yaml()
  def ACCOUNT = "my-account"
  def NAMESPACE = "spinnaker"

  KubernetesManifest stringToManifest(String input) {
    return mapper.convertValue(yaml.load(input), KubernetesManifest.class)
  }

  @Unroll
  def "given a correctly annotated manifest, build attributes & infer relationships"() {
    setup:
    def rawManifest = """
apiVersion: $apiVersion
kind: $kind
metadata:
  name: $name
  namespace: $namespace
"""
    def relationships = new KubernetesManifestSpinnakerRelationships()
        .setApplication(application)
        .setCluster(cluster)

    def manifest = stringToManifest(rawManifest)
    KubernetesManifestAnnotater.annotateManifestWithRelationships(manifest, relationships)
    V1beta1ReplicaSet resource = mapper.convertValue(manifest, V1beta1ReplicaSet.class)

    when:
    def cacheData = KubernetesCacheDataConverter.fromResource(account, mapper, resource)

    then:
    if (application == null) {
      true
    } else {
      cacheData.relationships.get(Keys.LogicalKind.APPLICATION.toString()) == [Keys.application(application)]
      if (cluster) {
        cacheData.relationships.get(Keys.LogicalKind.CLUSTER.toString()) == [Keys.cluster(account, cluster)]
      } else {
        cacheData.relationships.get(Keys.LogicalKind.CLUSTER.toString()) == null
      }
      cacheData.attributes.get("name") == name
      cacheData.attributes.get("namespace") == namespace
      cacheData.attributes.get("kind") == kind
      cacheData.id == Keys.infrastructure(kind, apiVersion, account, namespace, name)
    }

    where:
    kind                       | apiVersion                              | account           | application | cluster       | namespace        | name
    KubernetesKind.REPLICA_SET | KubernetesApiVersion.EXTENSIONS_V1BETA1 | null              | null        | null          | "some-namespace" | "a-name-v000"
    KubernetesKind.REPLICA_SET | KubernetesApiVersion.EXTENSIONS_V1BETA1 | "my-account"      | "one-app"   | "the-cluster" | "some-namespace" | "a-name-v000"
    KubernetesKind.DEPLOYMENT  | KubernetesApiVersion.EXTENSIONS_V1BETA1 | "my-account"      | "one-app"   | "the-cluster" | "some-namespace" | "a-name"
    KubernetesKind.SERVICE     | KubernetesApiVersion.V1                 | "another-account" | "your-app"  | null          | "some-namespace" | "what-name"
  }

  @Unroll
  def "given a single owner reference, correctly build relationships"() {
    setup:
    def ownerRefs = [new KubernetesManifest.OwnerReference(kind: kind, apiVersion: apiVersion, name: name)]

    when:
    def result = KubernetesCacheDataConverter.ownerReferenceRelationships(account, namespace, ownerRefs)

    then:
    result.get(kind.toString()) == [Keys.infrastructure(kind, apiVersion, account, namespace, name)]

    where:
    kind                       | apiVersion                              | account           | cluster       | namespace        | name
    KubernetesKind.REPLICA_SET | KubernetesApiVersion.EXTENSIONS_V1BETA1 | "my-account"      | "another-clu" | "some-namespace" | "a-name-v000"
    KubernetesKind.REPLICA_SET | KubernetesApiVersion.EXTENSIONS_V1BETA1 | "my-account"      | "the-cluster" | "some-namespace" | "a-name-v000"
    KubernetesKind.DEPLOYMENT  | KubernetesApiVersion.EXTENSIONS_V1BETA1 | "my-account"      | "the-cluster" | "some-namespace" | "a-name"
    KubernetesKind.SERVICE     | KubernetesApiVersion.V1                 | "another-account" | "cluster"     | "some-namespace" | "what-name"
  }

  @Unroll
  def "given a cache data entry, invert its relationships"() {
    setup:
    def id = Keys.infrastructure(kind, version, "account", "namespace", "version")
    def cacheData = new DefaultCacheData(id, null, relationships)

    when:
    def result = KubernetesCacheDataConverter.invertRelationships(cacheData)

    then:
    relationships.collect {
      group, keys -> keys.collect {
        key -> result.find {
          data -> data.id == key && data.relationships.get(kind.toString()) == [id]
        } != null
      }.inject true, { a, b -> a && b }
    }.inject true, { a, b -> a && b }

    where:
    kind                       | version                           | relationships
    KubernetesKind.REPLICA_SET | KubernetesApiVersion.APPS_V1BETA1 | ["application": [Keys.application("app")]]
    KubernetesKind.REPLICA_SET | KubernetesApiVersion.APPS_V1BETA1 | ["application": []]
    KubernetesKind.REPLICA_SET | KubernetesApiVersion.APPS_V1BETA1 | [:]
    KubernetesKind.REPLICA_SET | KubernetesApiVersion.APPS_V1BETA1 | ["deployment": [Keys.infrastructure(KubernetesKind.DEPLOYMENT, KubernetesApiVersion.APPS_V1BETA1, "account", "namespace", "a-name")]]
    KubernetesKind.SERVICE     | KubernetesApiVersion.V1           | ["cluster": [Keys.cluster("account", "name")], "application": [Keys.application("blarg")]]
    KubernetesKind.SERVICE     | KubernetesApiVersion.V1           | ["cluster": [Keys.cluster("account", "name")], "application": [Keys.application("blarg"), Keys.application("asdfasdf")]]
  }

  def filterRelationships(Collection<String> keys, List<Triple<KubernetesApiVersion, KubernetesKind, String>> existingResources) {
    return keys.findAll { sk ->
      def key = (Keys.InfrastructureCacheKey) Keys.parseKey(sk).get()
      return existingResources.find { Triple<KubernetesApiVersion, KubernetesKind, String> lb ->
        return lb.getLeft() == key.getKubernetesApiVersion() && lb.getMiddle() == key.getKubernetesKind() && lb.getRight() == key.getName()
      } != null
    }
  }

  @Unroll
  def "correctly derive annotated spinnaker relationships"() {
    setup:
    def spinnakerRelationships = new KubernetesManifestSpinnakerRelationships()
      .setCluster(cluster)
      .setApplication(application)
      .setLoadBalancers(loadBalancers)

    when:
    def relationships = KubernetesCacheDataConverter.annotatedRelationships(ACCOUNT, NAMESPACE, spinnakerRelationships)
    def parsedLbs = loadBalancers.collect { lb -> KubernetesManifest.fromFullResourceName(lb) }

    then:
    relationships.get(Keys.LogicalKind.CLUSTER.toString()) == [Keys.cluster(ACCOUNT, cluster)]
    relationships.get(Keys.LogicalKind.APPLICATION.toString()) == [Keys.application(application)]

    def services = filterRelationships(relationships.get(KubernetesKind.SERVICE.toString()), parsedLbs)
    def ingresses = filterRelationships(relationships.get(KubernetesKind.INGRESS.toString()), parsedLbs)

    ingresses.size() + services.size() == loadBalancers.size()

    where:
    cluster | application | loadBalancers
    "a"     | "b"         | ["v1|service|hi"]
    "a"     | "b"         | ["v1|service|hi", "v1|service|bye"]
    "a"     | "b"         | []
    "a"     | "b"         | ["v1|service|hi", "v1|service|bye", "extensions/v1beta1|ingress|into"]
    "a"     | "b"         | ["extensions/v1beta1|ingress|into"]
    "a"     | "b"         | ["extensions/v1beta1|ingress|into", "extensions/v1beta1|ingress|outof"]
    "a"     | "b"         | ["v1|service|hi", "v1|service|bye", "extensions/v1beta1|ingress|into", "extensions/v1beta1|ingress|outof"]
  }
}
