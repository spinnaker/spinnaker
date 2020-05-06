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
import com.netflix.spinnaker.clouddriver.kubernetes.op.handler.KubernetesUnregisteredCustomResourceHandler
import spock.lang.Specification

class GlobalResourcePropertyRegistrySpec extends Specification {
  KubernetesUnregisteredCustomResourceHandler defaultHandler = new KubernetesUnregisteredCustomResourceHandler()
  void "creates an empty resource map"() {
    given:
    def replicaSetHandler = new KubernetesReplicaSetHandler()

    when:
    GlobalResourcePropertyRegistry registry = new GlobalResourcePropertyRegistry(ImmutableList.of(), defaultHandler)

    then:
    registry instanceof GlobalResourcePropertyRegistry
    registry.values().isEmpty()

    when:
    registry = new GlobalResourcePropertyRegistry([replicaSetHandler], defaultHandler)

    then:
    registry.values().size() == 1
    registry.get(KubernetesKind.REPLICA_SET).getHandler() == replicaSetHandler
    registry.get(KubernetesKind.REPLICA_SET).isVersioned() == replicaSetHandler.versioned()
  }

  void "defaults to the default handler if no handler is specified"() {
    when:
    GlobalResourcePropertyRegistry registry = new GlobalResourcePropertyRegistry([], defaultHandler)

    then:
    registry.values().isEmpty()
    registry.get(KubernetesKind.REPLICA_SET).getHandler() == defaultHandler
    registry.get(KubernetesKind.REPLICA_SET).isVersioned() == defaultHandler.versioned()
  }

  void "registers handlers passed to the constructor"() {
    given:
    def unregisteredHandler = new KubernetesUnregisteredCustomResourceHandler()
    def replicaSetHandler = new KubernetesReplicaSetHandler()

    when:
    GlobalResourcePropertyRegistry registry = new GlobalResourcePropertyRegistry([unregisteredHandler, replicaSetHandler], defaultHandler)

    then:
    registry.values().size() == 2
    registry.get(KubernetesKind.NONE).getHandler() == unregisteredHandler
    registry.get(KubernetesKind.REPLICA_SET).getHandler() == replicaSetHandler
  }
}
