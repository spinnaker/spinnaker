/*
 * Copyright 2017 Google, Inc.
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

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.kubernetes.security.KubernetesV2Credentials
import spock.lang.Specification
import spock.lang.Unroll

class KubernetesReplicaSetCachingAgentSpec extends Specification {
  def ACCOUNT = "my-account"
  def CLUSTER = "my-cluster"
  def APPLICATION = "my-application"
  def NAME = "the-name"
  def NAMESPACE = "your-namespace"

  @Unroll
  void "merges two cache data"() {
    when:
    def credentials = Mock(KubernetesV2Credentials)
    credentials.getDeclaredNamespaces() >> [NAMESPACE]

    def namedAccountCredentials = Mock(KubernetesNamedAccountCredentials)
    namedAccountCredentials.getCredentials() >> credentials
    namedAccountCredentials.getName() >> ACCOUNT

    def registryMock = Mock(Registry)
    registryMock.timer(_) >> null
    def a = new DefaultCacheData("id", attrA, relA)
    def b = new DefaultCacheData("id", attrB, relB)
    def res = KubernetesCacheDataConverter.mergeCacheData(a, b)

    then:
    a.getAttributes().collect { k, v -> res.getAttributes().get(k) == v }.every()
    b.getAttributes().collect { k, v -> res.getAttributes().get(k) == v }.every()
    a.getRelationships().collect { k, v -> v.collect { r -> res.getRelationships().get(k).contains(r) }.every() }.every()
    b.getRelationships().collect { k, v -> v.collect { r -> res.getRelationships().get(k).contains(r) }.every() }.every()

    where:
    attrA      | attrB      | relA              | relB
    ["a": "b"] | ["c": "d"] | ["a": ["1", "2"]] | ["b": ["3", "4"]]
    [:]        | ["c": "d"] | [:]               | ["b": ["3", "4"]]
    [:]        | [:]        | [:]               | [:]
    ["a": "b"] | [:]        | ["a": ["1", "2"]] | [:]
  }
}
