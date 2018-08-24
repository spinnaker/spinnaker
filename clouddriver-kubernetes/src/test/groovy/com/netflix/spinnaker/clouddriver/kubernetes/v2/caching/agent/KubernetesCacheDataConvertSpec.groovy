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
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesApiVersion
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifestAnnotater
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifestMetadata
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifestSpinnakerRelationships
import com.netflix.spinnaker.clouddriver.kubernetes.v2.names.KubernetesManifestNamer
import com.netflix.spinnaker.clouddriver.names.NamerRegistry
import com.netflix.spinnaker.kork.artifacts.model.Artifact
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
      cacheData.relationships.get(Keys.LogicalKind.APPLICATIONS.toString()) == [Keys.application(application)]
      if (cluster) {
        cacheData.relationships.get(Keys.LogicalKind.CLUSTERS.toString()) == [Keys.cluster(account, application, cluster)]
      } else {
        cacheData.relationships.get(Keys.LogicalKind.CLUSTERS.toString()) == null
      }
      cacheData.attributes.get("name") == name
      cacheData.attributes.get("namespace") == namespace
      cacheData.attributes.get("kind") == kind
      cacheData.id == Keys.infrastructure(kind, account, namespace, name)
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
    result.get(kind.toString()) == [Keys.infrastructure(kind, account, namespace, name)]

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
    def id = Keys.infrastructure(kind, "account", "namespace", "version")
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
    KubernetesKind.REPLICA_SET | KubernetesApiVersion.APPS_V1BETA1 | ["deployment": [Keys.infrastructure(KubernetesKind.DEPLOYMENT, "account", "namespace", "a-name")]]
    KubernetesKind.SERVICE     | KubernetesApiVersion.V1           | ["cluster": [Keys.cluster("account", "app", "name")], "application": [Keys.application("blarg")]]
    KubernetesKind.SERVICE     | KubernetesApiVersion.V1           | ["cluster": [Keys.cluster("account", "app", "name")], "application": [Keys.application("blarg"), Keys.application("asdfasdf")]]
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
