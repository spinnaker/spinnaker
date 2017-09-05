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
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.Keys
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesApiVersion
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesKind
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesManifest
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesManifestAnnotater
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.KubernetesManifestSpinnakerRelationships
import io.kubernetes.client.models.V1beta1ReplicaSet
import org.yaml.snakeyaml.Yaml
import spock.lang.Specification

class KubernetesCacheDataConvertSpec extends Specification {
  def mapper = new ObjectMapper()
  def yaml = new Yaml()

  KubernetesManifest stringToManifest(String input) {
    return mapper.convertValue(yaml.load(input), KubernetesManifest.class)
  }

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
        cacheData.relationships.get(Keys.LogicalKind.CLUSTER.toString()) == [Keys.cluster(account, application, cluster)]
      } else {
        cacheData.relationships.get(Keys.LogicalKind.CLUSTER.toString()) == null
      }
      cacheData.attributes.get("name") == name
      cacheData.attributes.get("namespace") == namespace
      cacheData.attributes.get("kind") == kind
      cacheData.id == Keys.infrastructure(kind, apiVersion, account, application, namespace, name)
    }

    where:
    kind                       | apiVersion                              | account           | application | cluster       | namespace        | name
    KubernetesKind.REPLICA_SET | KubernetesApiVersion.EXTENSIONS_V1BETA1 | null              | null        | null          | "some-namespace" | "a-name-v000"
    KubernetesKind.REPLICA_SET | KubernetesApiVersion.EXTENSIONS_V1BETA1 | "my-account"      | "one-app"   | "the-cluster" | "some-namespace" | "a-name-v000"
    KubernetesKind.DEPLOYMENT  | KubernetesApiVersion.EXTENSIONS_V1BETA1 | "my-account"      | "one-app"   | "the-cluster" | "some-namespace" | "a-name"
    KubernetesKind.SERVICE     | KubernetesApiVersion.V1                 | "another-account" | "your-app"  | null          | "some-namespace" | "what-name"
  }

}
