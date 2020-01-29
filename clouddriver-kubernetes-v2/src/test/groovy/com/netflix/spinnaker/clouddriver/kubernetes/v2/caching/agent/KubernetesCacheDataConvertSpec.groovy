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
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesCloudProvider
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.Keys
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesPodMetric
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.GlobalKubernetesKindRegistry
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesApiVersion
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKindProperties
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesKindRegistry
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
    KubernetesCacheData kubernetesCacheData = new KubernetesCacheData()
    KubernetesCacheDataConverter.convertAsResource(kubernetesCacheData, account, KubernetesKindProperties.create(kind, true), manifest, [], false)
    def optional = kubernetesCacheData.toCacheData().stream().filter({
      cd -> cd.id == Keys.InfrastructureCacheKey.createKey(kind, account, namespace, name)
    }).findFirst()

    then:
    if (application == null) {
      true
    } else {
      optional.isPresent()
      def cacheData = optional.get()
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
    result.contains(new Keys.InfrastructureCacheKey(kind, account, namespace, name))

    where:
    kind                       | apiVersion                              | account           | cluster       | namespace        | name
    KubernetesKind.REPLICA_SET | KubernetesApiVersion.EXTENSIONS_V1BETA1 | "my-account"      | "another-clu" | "some-namespace" | "a-name-v000"
    KubernetesKind.REPLICA_SET | KubernetesApiVersion.EXTENSIONS_V1BETA1 | "my-account"      | "the-cluster" | "some-namespace" | "a-name-v000"
    KubernetesKind.DEPLOYMENT  | KubernetesApiVersion.EXTENSIONS_V1BETA1 | "my-account"      | "the-cluster" | "some-namespace" | "a-name"
    KubernetesKind.SERVICE     | KubernetesApiVersion.V1                 | "another-account" | "cluster"     | "some-namespace" | "what-name"
  }

  @Unroll
  def "correctly builds cache data entry for pod metrics"() {
    setup:
    KubernetesCacheData kubernetesCacheData = new KubernetesCacheData()
    def account = "my-account"
    def namespace = "my-namespace"
    def podName = "pod-name"
    def podMetric = KubernetesPodMetric.builder()
      .podName(podName)
      .namespace(namespace)
      .containerMetrics(containerMetrics)
      .build()
    def metricKey = new Keys.MetricCacheKey(KubernetesKind.POD, account, namespace, podName)

    when:
    KubernetesCacheDataConverter.convertPodMetric(kubernetesCacheData, account, podMetric)
    List<CacheData> cacheDataList = kubernetesCacheData.toCacheData()

    then:
    CacheData cacheData = cacheDataList
      .stream()
      .filter({cd -> cd.id == metricKey.toString()})
      .findFirst().get()
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
