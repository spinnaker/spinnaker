/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.v1.provider.agent

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.kubernetes.v1.api.KubernetesApiAdaptor
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.kubernetes.v1.caching.Keys
import com.netflix.spinnaker.clouddriver.kubernetes.v1.security.KubernetesV1Credentials
import io.fabric8.kubernetes.api.model.ObjectMeta
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.PodSpec
import io.fabric8.kubernetes.api.model.PodStatus
import spock.lang.Specification

class KubernetesInstanceCachingAgentSpec extends Specification {
  static final String accountName = "account"
  static final String namespace = "namespace"
  static final ObjectMapper mapper = new ObjectMapper()

  KubernetesV1Credentials credentials
  KubernetesApiAdaptor apiAdaptor
  ProviderCache providerCache
  KubernetesInstanceCachingAgent agent

  def setup() {
    apiAdaptor = Mock(KubernetesApiAdaptor)
    credentials = Mock(KubernetesV1Credentials) {
      getApiAdaptor() >> apiAdaptor
    }
    def namedCrededentialsMock = Mock(KubernetesNamedAccountCredentials)
    namedCrededentialsMock.getCredentials() >> credentials
    namedCrededentialsMock.getName() >> accountName
    providerCache = Mock(ProviderCache)
    credentials.getDeclaredNamespaces() >> [namespace]
    agent = new KubernetesInstanceCachingAgent(namedCrededentialsMock, mapper, null, 0, 1)
  }

  void "should apply cache-ttl annotation to pod"() {
    setup:
    def cacheExpiry = "1000"
    def metadata = new ObjectMeta()
    metadata.annotations = [(KubernetesInstanceCachingAgent.CACHE_TTL_ANNOTATION): cacheExpiry]
    def pod = new Pod("v1", "Pod", metadata, new PodSpec(), new PodStatus())

    when:
    def data = agent.loadData(providerCache)

    then:
    1 * apiAdaptor.getPods(namespace) >> [pod]
    data.cacheResults[Keys.Namespace.INSTANCES.ns].size() == 1
    data.cacheResults[Keys.Namespace.INSTANCES.ns][0].attributes.containsKey("cacheExpiry")
    data.cacheResults[Keys.Namespace.INSTANCES.ns][0].attributes["cacheExpiry"] == cacheExpiry
  }
}
