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

package com.netflix.spinnaker.clouddriver.kubernetes.description

import com.google.common.collect.ImmutableList
import com.netflix.spinnaker.clouddriver.kubernetes.description.manifest.KubernetesKind
import com.netflix.spinnaker.clouddriver.kubernetes.op.handler.KubernetesHandler
import com.netflix.spinnaker.clouddriver.kubernetes.op.handler.KubernetesReplicaSetHandler
import spock.lang.Specification

class AccountResourcePropertyRegistrySpec extends Specification {
  void "returns account-specific properties when defined"() {
    given:
    def replicaSetProperties = new KubernetesResourceProperties(new KubernetesReplicaSetHandler(), true)
    def globalResourcePropertyRegistry = Mock(GlobalResourcePropertyRegistry) {
      values() >> ImmutableList.of()
      get(_ as KubernetesKind) >> KubernetesKind.NONE
    }
    def factory = new AccountResourcePropertyRegistry.Factory(globalResourcePropertyRegistry)

    when:
    AccountResourcePropertyRegistry registry = factory.create([])

    then:
    registry instanceof AccountResourcePropertyRegistry
    registry.values().isEmpty()

    when:
    registry = factory.create([replicaSetProperties])

    then:
    registry.values().size() == 1
    registry.get(KubernetesKind.REPLICA_SET) == replicaSetProperties
  }

  void "returns global properties when account-specific properties are not defined"() {
    given:
    def properties = new KubernetesResourceProperties(Mock(KubernetesHandler), true)
    def globalResourcePropertyRegistry = Mock(GlobalResourcePropertyRegistry) {
      values() >> ImmutableList.of(properties)
      get(KubernetesKind.DEPLOYMENT) >> properties
    }
    def factory = new AccountResourcePropertyRegistry.Factory(globalResourcePropertyRegistry)

    when:
    AccountResourcePropertyRegistry registry = factory.create([])

    then:
    registry instanceof AccountResourcePropertyRegistry
    registry.values().size() == 1
    registry.get(KubernetesKind.DEPLOYMENT) == properties
  }
}
