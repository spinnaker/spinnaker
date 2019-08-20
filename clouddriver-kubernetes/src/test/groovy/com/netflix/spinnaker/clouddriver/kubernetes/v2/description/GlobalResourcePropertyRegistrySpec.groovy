/*
 * Copyright 2019 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.description

import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.handler.KubernetesHandler
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.handler.KubernetesReplicaSetHandler
import com.netflix.spinnaker.clouddriver.kubernetes.v2.op.handler.KubernetesUnregisteredCustomResourceHandler
import spock.lang.Specification

class GlobalResourcePropertyRegistrySpec extends Specification {
  void "creates an empty resource map"() {
    given:
    def replicaSetHandler = new KubernetesReplicaSetHandler()
    def replicaSetProperties = new KubernetesResourceProperties(replicaSetHandler, replicaSetHandler.versioned())

    when:
    GlobalResourcePropertyRegistry registry = new GlobalResourcePropertyRegistry(Collections.emptyList(), new KubernetesSpinnakerKindMap())

    then:
    registry instanceof GlobalResourcePropertyRegistry
    registry.values().isEmpty()

    when:
    registry.register(replicaSetProperties)

    then:
    registry.values().size() == 1
    registry.get(KubernetesKind.REPLICA_SET) == replicaSetProperties
  }

  void "defaults to the handler for NONE if no handler is specified"() {
    given:
    def unregisteredHandler = new KubernetesUnregisteredCustomResourceHandler()
    def unregisteredProperties = new KubernetesResourceProperties(unregisteredHandler, unregisteredHandler.versioned())

    when:
    GlobalResourcePropertyRegistry registry = new GlobalResourcePropertyRegistry(Collections.emptyList(), new KubernetesSpinnakerKindMap())
    registry.register(unregisteredProperties)

    then:
    registry.values().size() == 1
    registry.get(KubernetesKind.REPLICA_SET) == unregisteredProperties
  }

  void "properly updates the kindMap"() {
    given:
    def mockHandler = Mock(KubernetesHandler) {
      spinnakerKind() >> KubernetesSpinnakerKindMap.SpinnakerKind.INSTANCES
      kind() >> KubernetesKind.REPLICA_SET
    }
    def properties = new KubernetesResourceProperties(mockHandler, true)
    def kindMap = new KubernetesSpinnakerKindMap()

    when:
    GlobalResourcePropertyRegistry registry = new GlobalResourcePropertyRegistry(Collections.emptyList(), kindMap)
    registry.register(properties)

    then:
    kindMap.translateSpinnakerKind(KubernetesSpinnakerKindMap.SpinnakerKind.INSTANCES) == [KubernetesKind.REPLICA_SET] as Set
    kindMap.translateKubernetesKind(KubernetesKind.REPLICA_SET) == KubernetesSpinnakerKindMap.SpinnakerKind.INSTANCES
  }

  void "registers handlers passed to the constructor"() {
    given:
    def unregisteredHandler = new KubernetesUnregisteredCustomResourceHandler()
    def replicaSetHandler = new KubernetesReplicaSetHandler()

    when:
    GlobalResourcePropertyRegistry registry = new GlobalResourcePropertyRegistry([unregisteredHandler, replicaSetHandler], new KubernetesSpinnakerKindMap())

    then:
    registry.values().size() == 2
    registry.get(KubernetesKind.NONE).getHandler() == unregisteredHandler
    registry.get(KubernetesKind.REPLICA_SET).getHandler() == replicaSetHandler
  }
}
