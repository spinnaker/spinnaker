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

import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesApiGroup
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKindRegistry
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class KubernetesKindRegistrySpec extends Specification {
  static final KubernetesApiGroup CUSTOM_API_GROUP =  KubernetesApiGroup.fromString("test")
  static final KubernetesKind CUSTOM_KIND = KubernetesKind.fromString("customKind", CUSTOM_API_GROUP)

  @Unroll
  void "kinds from core API groups are returned if any core API group is input"() {
    given:
    @Subject KubernetesKindRegistry kindRegistry = new KubernetesKindRegistry()
    kindRegistry.registerKind(KubernetesKind.REPLICA_SET)

    when:
    def kind = kindRegistry.getRegisteredKind(name, apiGroup)

    then:
    result == kind.orElse(null)

    where:
    name         | apiGroup                      | result
    "replicaSet" | null                          | KubernetesKind.REPLICA_SET
    "replicaSet" | KubernetesApiGroup.APPS       | KubernetesKind.REPLICA_SET
    "replicaSet" | KubernetesApiGroup.EXTENSIONS | KubernetesKind.REPLICA_SET
    "replicaSet" | CUSTOM_API_GROUP              | null
    "rs"         | null                          | KubernetesKind.REPLICA_SET
    "rs"         | KubernetesApiGroup.APPS       | KubernetesKind.REPLICA_SET
    "replicaSet" | KubernetesApiGroup.EXTENSIONS | KubernetesKind.REPLICA_SET
    "replicaSet" | CUSTOM_API_GROUP              | null

  }

  @Unroll
  void "getRegisteredKind returns kinds that have been registered"() {
    given:
    @Subject KubernetesKindRegistry kindRegistry = new KubernetesKindRegistry()
    KubernetesKind result

    when:
    result = kindRegistry.getRegisteredKind("customKind", CUSTOM_API_GROUP).orElse(null)

    then:
    result == null

    when:
    kindRegistry.registerKind(CUSTOM_KIND)
    result = kindRegistry.getRegisteredKind("customKind", CUSTOM_API_GROUP).orElse(null)

    then:
    result == CUSTOM_KIND
  }

  @Unroll
  void "getOrRegisterKind registers kinds that are not in the registry"() {
    given:
    @Subject KubernetesKindRegistry kindRegistry = new KubernetesKindRegistry()
    KubernetesKind result

    when:
    result = kindRegistry.getOrRegisterKind("customKind", CUSTOM_API_GROUP, {CUSTOM_KIND})

    then:
    result == CUSTOM_KIND

    when:
    result = kindRegistry.getRegisteredKind("customKind", CUSTOM_API_GROUP).orElse(null)

    then:
    result == CUSTOM_KIND
  }

  @Unroll
  void "getOrRegisterKind does not call the supplier for a kind that is already registered"() {
    given:
    @Subject KubernetesKindRegistry kindRegistry = new KubernetesKindRegistry()
    kindRegistry.registerKind(CUSTOM_KIND)

    when:
    def result = kindRegistry.getOrRegisterKind("customKind", CUSTOM_API_GROUP, {
      throw new Exception("Should not have called supplier")
    })

    then:
    result == CUSTOM_KIND
    noExceptionThrown()
  }

  @Unroll
  void "getRegisteredKinds returns all kinds that are registered"() {
    given:
    @Subject KubernetesKindRegistry kindRegistry = new KubernetesKindRegistry()
    Collection<KubernetesKind> kinds

    when:
    kinds = kindRegistry.getRegisteredKinds()

    then:
    kinds.isEmpty()

    when:
    kindRegistry.registerKind(KubernetesKind.REPLICA_SET)
    kindRegistry.registerKind(CUSTOM_KIND)
    kinds = kindRegistry.getRegisteredKinds()

    then:
    kinds.size() == 2
    kinds.contains(KubernetesKind.REPLICA_SET)
    kinds.contains(CUSTOM_KIND)
  }
}
