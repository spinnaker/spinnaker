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
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.kubernetes.v2.caching.Keys
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesApiVersion
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesManifest
import com.netflix.spinnaker.clouddriver.kubernetes.v2.security.KubernetesV2Credentials
import io.kubernetes.client.models.V1ObjectMeta
import io.kubernetes.client.models.V1Pod
import org.joda.time.DateTime
import spock.lang.Specification

class KubernetesPodCachingAgentSpec extends Specification {
  def ACCOUNT = "my-account"
  def CLUSTER = "my-cluster"
  def APPLICATION = "my-application"
  def NAME = "the-name"
  def NAMESPACE = "your-namespace"

  void "invokes caching agent on output pod"() {
    setup:
    def pod = new V1Pod()
    def annotations = [
        'moniker.spinnaker.io/cluster': '"' + CLUSTER + '"',
        'moniker.spinnaker.io/application': '"' + APPLICATION + '"',
        'artifact.spinnaker.io/type': '"' + "pod" + '"',
        'artifact.spinnaker.io/name': '"' + NAME + '"'
    ]

    def metadata = new V1ObjectMeta()
    metadata.setAnnotations(annotations)
    metadata.setName(NAME)
    metadata.setNamespace(NAMESPACE)
    metadata.setCreationTimestamp(DateTime.now())
    pod.setMetadata(metadata)
    pod.setKind(KubernetesKind.POD.name)
    pod.setApiVersion(KubernetesApiVersion.V1.name)

    def credentials = Mock(KubernetesV2Credentials)
    credentials.getDeclaredNamespaces() >> [NAMESPACE]

    credentials.list(KubernetesKind.POD, NAMESPACE) >> [new ObjectMapper().convertValue(pod, KubernetesManifest.class)]

    def namedAccountCredentials = Mock(KubernetesNamedAccountCredentials)
    namedAccountCredentials.getCredentials() >> credentials
    namedAccountCredentials.getName() >> ACCOUNT

    def cachingAgent = new KubernetesPodCachingAgent(namedAccountCredentials, new ObjectMapper(), null, 0, 1)

    when:
    def result = cachingAgent.loadData(null)

    then:
    result.cacheResults[KubernetesKind.POD.name].size() == 1
    result.cacheResults[KubernetesKind.POD.name].find { cacheData ->
      cacheData.relationships.get(Keys.LogicalKind.CLUSTERS.toString()) == [Keys.cluster(ACCOUNT, APPLICATION, CLUSTER)]
      cacheData.relationships.get(Keys.LogicalKind.APPLICATIONS.toString()) == [Keys.application(APPLICATION)]
      cacheData.attributes.get("name") == NAME
      cacheData.attributes.get("namespace") == NAMESPACE
    } != null
  }
}
