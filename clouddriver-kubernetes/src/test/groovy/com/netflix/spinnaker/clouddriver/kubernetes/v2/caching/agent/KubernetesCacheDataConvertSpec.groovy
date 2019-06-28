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
import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesCloudProvider
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.Keys
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesPodMetric
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesApiVersion
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifestAnnotater
import com.netflix.spinnaker.clouddriver.kubernetes.v2.names.KubernetesManifestNamer
import com.netflix.spinnaker.clouddriver.names.NamerRegistry
import com.netflix.spinnaker.moniker.Moniker
import org.apache.commons.lang3.tuple.Pair
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import spock.lang.Specification
import spock.lang.Unroll

class KubernetesCacheDataConvertSpec extends Specification {
  def mapper = new ObjectMapper()
  def yaml = new Yaml(new SafeConstructor())
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
    def moniker = Moniker.builder()
        .app(application)
        .cluster(cluster)
        .build()

    if (account != null) {
      NamerRegistry.lookup()
        .withProvider(KubernetesCloudProvider.ID)
        .withAccount(account)
        .setNamer(KubernetesManifest, new KubernetesManifestNamer())
    }

    def manifest = stringToManifest(rawManifest)
    KubernetesManifestAnnotater.annotateManifest(manifest, moniker)

    when:
    def cacheData = KubernetesCacheDataConverter.convertAsResource(account, manifest, [])

    then:
    if (application == null) {
      true
    } else {
      cacheData.relationships.get(Keys.LogicalKind.APPLICATIONS.toString()) == [Keys.ApplicationCacheKey.createKey(application)]
      if (cluster) {
        cacheData.relationships.get(Keys.LogicalKind.CLUSTERS.toString()) == [Keys.ClusterCacheKey.createKey(account, application, cluster)]
      } else {
        cacheData.relationships.get(Keys.LogicalKind.CLUSTERS.toString()) == null
      }
      cacheData.attributes.get("name") == name
      cacheData.attributes.get("namespace") == namespace
      cacheData.attributes.get("kind") == kind
      cacheData.id == Keys.InfrastructureCacheKey.createKey(kind, account, namespace, name)
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
    result.get(kind.toString()) == [Keys.InfrastructureCacheKey.createKey(kind, account, namespace, name)]

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
    def id = Keys.InfrastructureCacheKey.createKey(kind, "account", "namespace", "version")
    def cacheData = new DefaultCacheData(id, null, relationships)

    when:
    def result = KubernetesCacheDataConverter.invertRelationships([cacheData])

    then:
    relationships.every {
      group, keys -> keys.every {
        key -> result.find {
          data -> data.id == key && data.relationships.get(kind.toString()) == [id] as Set
        } != null
      }
    }

    where:
    kind                       | version                           | relationships
    KubernetesKind.REPLICA_SET | KubernetesApiVersion.APPS_V1BETA1 | ["application": [Keys.ApplicationCacheKey.createKey("app")]]
    KubernetesKind.REPLICA_SET | KubernetesApiVersion.APPS_V1BETA1 | ["application": []]
    KubernetesKind.REPLICA_SET | KubernetesApiVersion.APPS_V1BETA1 | [:]
    KubernetesKind.REPLICA_SET | KubernetesApiVersion.APPS_V1BETA1 | ["deployment": [Keys.InfrastructureCacheKey.createKey(KubernetesKind.DEPLOYMENT, "account", "namespace", "a-name")]]
    KubernetesKind.SERVICE     | KubernetesApiVersion.V1           | ["cluster": [Keys.ClusterCacheKey.createKey("account", "app", "name")], "application": [Keys.ApplicationCacheKey.createKey("blarg")]]
    KubernetesKind.SERVICE     | KubernetesApiVersion.V1           | ["cluster": [Keys.ClusterCacheKey.createKey("account", "app", "name")], "application": [Keys.ApplicationCacheKey.createKey("blarg"), Keys.ApplicationCacheKey.createKey("asdfasdf")]]
  }

  @Unroll
  def "given a cache data entry, determines cluster relationships"() {
    setup:
    def account = "account"
    def application = "app"
    def id = Keys.InfrastructureCacheKey.createKey(kind, account, "namespace", cluster)
    def attributes = [
      moniker: [
        app: application,
        cluster: cluster
      ]
    ]
    def cacheData = new DefaultCacheData(id, attributes, [:])

    when:
    def result = KubernetesCacheDataConverter.getClusterRelationships(account, [cacheData])

    then:
    result.size() == 1
    result[0].id == Keys.ApplicationCacheKey.createKey(application)
    result[0].relationships.clusters == [
      Keys.ClusterCacheKey.createKey(account, application, cluster)
    ] as Set

    where:
    kind                       | cluster
    KubernetesKind.REPLICA_SET | "my-app-321"
    KubernetesKind.DEPLOYMENT  | "my-app"
    KubernetesKind.POD         | "my-app-321-abcd"
  }

  @Unroll
  def "correctly builds cache data entry for pod metrics"() {
    setup:
    def account = "my-account"
    def namespace = "my-namespace"
    def podName = "pod-name"
    def podMetric = KubernetesPodMetric.builder()
      .podName(podName)
      .containerMetrics(containerMetrics)
      .build()

    when:
    def cacheData = KubernetesCacheDataConverter.convertPodMetric(account, namespace, podMetric)

    then:
    cacheData.attributes == [
      name: podName,
      namespace: namespace,
      metrics: containerMetrics
    ]

    when:
    def metrics = KubernetesCacheDataConverter.getMetrics(cacheData)

    then:
    metrics == containerMetrics

    where:
    containerMetrics << [
      [containerMetric("container-a")],
      [containerMetric("container-a"), containerMetric("container-b")],
      []
    ]
  }

  def containerMetric(String containerName) {
    return KubernetesPodMetric.ContainerMetric.builder()
      .containerName(containerName)
      .metrics([
        "CPU(cores)": "10m",
        "MEMORY(bytes)": "2Mi"
      ]).build()
  }

  def filterRelationships(Collection<String> keys, List<Pair<KubernetesKind, String>> existingResources) {
    return keys.findAll { sk ->
      def key = (Keys.InfrastructureCacheKey) Keys.parseKey(sk).get()
      return existingResources.find { Pair<KubernetesKind, String> lb ->
        return lb.getLeft() == key.getKubernetesKind() && lb.getRight() == key.getName()
      } != null
    }
  }
}
